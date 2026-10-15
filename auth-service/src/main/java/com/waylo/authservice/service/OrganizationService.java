package com.waylo.authservice.service;

import com.waylo.authservice.domain.Organization;
import com.waylo.authservice.domain.OrganizationMembership;
import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.RoleAssignment;
import com.waylo.authservice.domain.ScopeType;
import com.waylo.authservice.domain.User;
import com.waylo.authservice.dto.CreateOrganizationRequest;
import com.waylo.authservice.dto.MembershipResponse;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.dto.RoleGrantResponse;
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

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public OrganizationService(UserRepository userRepository,
                               OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               RoleAssignmentRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(UUID userId, CreateOrganizationRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        long count = membershipRepository.countOwnedOrganizations(userId);
        if (count >= 5) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization limit reached");
        }

        Organization organization = new Organization(req.name().trim());
        organization = organizationRepository.saveAndFlush(organization);

        OrganizationMembership membership = new OrganizationMembership(user, organization);
        membership = membershipRepository.save(membership);

        RoleAssignment assignment = new RoleAssignment(membership,
                Role.ORGANIZATION_OWNER, ScopeType.ORGANIZATION, organization.getId());
        roleAssignmentRepository.save(assignment);

        log.info("Organization {} created for user {} as ORGANIZATION_OWNER",
                organization.getId(), user.getId());

        return new OrganizationResponse(organization.getId(),
                organization.getName(), organization.getStatus());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMemberships(UUID userId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(userId);

        return memberships.stream().map(membership -> {
            Organization org = membership.getOrganization();
            List<RoleAssignment> assignments = roleAssignmentRepository.findByMembershipId(membership.getId());

            List<RoleGrantResponse> roles = assignments.stream()
                    .map(a -> new RoleGrantResponse(a.getRole(), a.getScopeType(), a.getScopeId()))
                    .toList();

            return new MembershipResponse(membership.getId(),
                    org.getId(), org.getName(), org.getStatus(),
                    membership.getStatus(), roles);
        }).toList();
    }
}
