package com.waylo.authservice.service;

import com.waylo.authservice.client.HotelCatalogClient;
import com.waylo.authservice.client.HotelLookupResponse;
import com.waylo.authservice.domain.MembershipStatus;
import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationMembership;
import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.domain.RoleAssignment;
import com.waylo.authservice.domain.ScopeType;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.RoleGrantRequest;
import com.waylo.authservice.dto.RoleGrantResult;
import com.waylo.authservice.repository.OrganizationMembershipRepository;
import com.waylo.authservice.repository.OrganizationRepository;
import com.waylo.authservice.repository.RoleAssignmentRepository;
import com.waylo.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleGrantService {

    private static final Logger log = LoggerFactory.getLogger(RoleGrantService.class);

    private static final Set<Role> GRANTABLE_ROLES = EnumSet.of(Role.HOTEL_OWNER, Role.HOTEL_MANAGER, Role.HOTEL_STAFF);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final HotelCatalogClient hotelCatalogClient;

    public RoleGrantService(UserRepository userRepository,
                            OrganizationRepository organizationRepository,
                            OrganizationMembershipRepository membershipRepository,
                            RoleAssignmentRepository roleAssignmentRepository,
                            HotelCatalogClient hotelCatalogClient) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.hotelCatalogClient = hotelCatalogClient;
    }

    @Transactional
    public RoleGrantResult grantRole(UUID actorId, UUID organizationId, RoleGrantRequest req) {
        // Rule 1: validate role
        if (!GRANTABLE_ROLES.contains(req.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ORGANIZATION_OWNER cannot be granted here");
        }

        // Rule 2: organization existence and status
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        if (org.getStatus() != OrganizationStatus.PENDING && org.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization is not active");
        }

        // Rule 3: actor membership and authority
        Optional<OrganizationMembership> actorMemberOpt =
                membershipRepository.findByUserIdAndOrganizationId(actorId, organizationId);
        if (actorMemberOpt.isEmpty() || actorMemberOpt.get().getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not an active member of the organization");
        }
        OrganizationMembership actorMember = actorMemberOpt.get();

        // Fetch actor's assignments to determine authority
        List<RoleAssignment> actorAssignments = roleAssignmentRepository.findByMembershipId(actorMember.getId());
        boolean hasOrgOwner = actorAssignments.stream()
                .anyMatch(a -> a.getRole() == Role.ORGANIZATION_OWNER
                        && a.getScopeType() == ScopeType.ORGANIZATION
                        && a.getScopeId().equals(organizationId));

        boolean hasHotelOwnerForHotel = actorAssignments.stream()
                .anyMatch(a -> a.getRole() == Role.HOTEL_OWNER
                        && a.getScopeType() == ScopeType.HOTEL
                        && a.getScopeId().equals(req.hotelId()));

        if (hasOrgOwner) {
            // Organization owner can grant any of HOTEL_OWNER/MANAGER/STAFF on any hotel
        } else if (hasHotelOwnerForHotel) {
            // Hotel owner can only grant MANAGER or STAFF (not HOTEL_OWNER)
            if (req.role() == Role.HOTEL_OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot grant a role beyond your own authority");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot grant a role beyond your own authority");
        }

        // Rule 4: hotel belongs to organization
        if (!hotelBelongsToOrganization(req.hotelId(), organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hotel does not belong to this organization");
        }

        // Rule 5: target user
        User target = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Find or create membership for target
        Optional<OrganizationMembership> targetMemberOpt =
                membershipRepository.findByUserIdAndOrganizationId(target.getId(), organizationId);
        OrganizationMembership targetMember;
        if (targetMemberOpt.isPresent()) {
            targetMember = targetMemberOpt.get();
            if (targetMember.getStatus() != MembershipStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Target user membership is not active");
            }
        } else {
            targetMember = new OrganizationMembership(target, org);
            targetMember = membershipRepository.save(targetMember);
        }

        // Duplicate check
        if (roleAssignmentRepository.existsByMembershipIdAndRoleAndScopeTypeAndScopeId(
                targetMember.getId(), req.role(), ScopeType.HOTEL, req.hotelId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role assignment already exists");
        }

        // Create grant
        RoleAssignment assignment = new RoleAssignment(targetMember, req.role(), ScopeType.HOTEL, req.hotelId());
        assignment = roleAssignmentRepository.save(assignment);

        // Bump permission version
        targetMember.incrementPermissionVersion();
        membershipRepository.save(targetMember);

        return new RoleGrantResult(assignment.getId(), target.getId(), req.role(), ScopeType.HOTEL, req.hotelId());
    }

    @Transactional
    public void revokeRole(UUID actorId, UUID organizationId, UUID grantId) {
        // Find grant and verify it belongs to this organization
        RoleAssignment grant = roleAssignmentRepository.findByIdAndOrganizationId(grantId, organizationId);
        if (grant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grant not found");
        }

        // Rule 6: ORGANIZATION_OWNER cannot be revoked
        if (grant.getRole() == Role.ORGANIZATION_OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ORGANIZATION_OWNER cannot be revoked here");
        }

        // Actor authority check (same rules as grant, but for the grant's role/hotel)
        Optional<OrganizationMembership> actorMemberOpt =
                membershipRepository.findByUserIdAndOrganizationId(actorId, organizationId);
        if (actorMemberOpt.isEmpty() || actorMemberOpt.get().getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not an active member");
        }
        OrganizationMembership actorMember = actorMemberOpt.get();

        List<RoleAssignment> actorAssignments = roleAssignmentRepository.findByMembershipId(actorMember.getId());
        boolean hasOrgOwner = actorAssignments.stream()
                .anyMatch(a -> a.getRole() == Role.ORGANIZATION_OWNER
                        && a.getScopeType() == ScopeType.ORGANIZATION
                        && a.getScopeId().equals(organizationId));

        boolean hasHotelOwnerForHotel = actorAssignments.stream()
                .anyMatch(a -> a.getRole() == Role.HOTEL_OWNER
                        && a.getScopeType() == ScopeType.HOTEL
                        && a.getScopeId().equals(grant.getScopeId()));

        if (hasOrgOwner) {
            // OK to revoke any
        } else if (hasHotelOwnerForHotel) {
            // Hotel owner can only revoke MANAGER/STAFF (not HOTEL_OWNER)
            if (grant.getRole() == Role.HOTEL_OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot revoke a role beyond your own authority");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot revoke a role beyond your own authority");
        }

        // Delete grant
        roleAssignmentRepository.delete(grant);

        // Bump permission version
        grant.getMembership().incrementPermissionVersion();
        membershipRepository.save(grant.getMembership());
    }

    private boolean hotelBelongsToOrganization(UUID hotelId, UUID organizationId) {
        try {
            HotelLookupResponse response = hotelCatalogClient.getHotel(hotelId);
            if (response == null || !response.success() || response.data() == null
                    || response.data().organizationId() == null) {
                log.warn("Hotel lookup returned invalid data for hotel {}", hotelId);
                return false;
            }
            return response.data().organizationId().equals(organizationId);
        } catch (Exception e) {
            log.warn("Hotel service call failed for hotel {}: {}", hotelId, e.getMessage());
            return false;
        }
    }
}
