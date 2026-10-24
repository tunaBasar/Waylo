package com.waylo.authservice.service;

import com.waylo.authservice.domain.*;
import com.waylo.authservice.dto.AccessDecision;
import com.waylo.authservice.repository.AccessQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fail-closed access evaluation service.
 *
 * Called by domain services (e.g. hotel-service) to ask whether a user may perform
 * a permission on an organization or hotel.
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessService.class);
    private static final List<String> EMPTY_ROLES = List.of();

    private final AccessQueryRepository accessQueryRepository;

    public AccessService(AccessQueryRepository accessQueryRepository) {
        this.accessQueryRepository = accessQueryRepository;
    }

    /**
     * Check whether {@code userId} may perform {@code permission} on
     * {@code organizationId} (and optionally {@code hotelId}).
     *
     * @param userId        user to check (nullable -> denied)
     * @param organizationId organization to check (nullable -> denied)
     * @param hotelId        hotel scope (nullable -> ORGANIZATION_OWNER only for HOTEL_MANAGE/ROOM_MANAGE)
     * @param permission     permission to evaluate (nullable -> denied)
     * @return access decision (always non-null, fail-closed)
     */
    public AccessDecision check(
            UUID userId,
            UUID organizationId,
            UUID hotelId,
            Permission permission) {

        // Null-guard: deny on missing inputs
        if (userId == null || organizationId == null || permission == null) {
            return new AccessDecision(false, EMPTY_ROLES);
        }

        try {
            List<RoleAssignment> grants = accessQueryRepository.findActiveGrants(userId, organizationId);

            if (grants.isEmpty()) {
                return new AccessDecision(false, EMPTY_ROLES);
            }

            boolean allow = evaluate(grants, hotelId, permission);
            List<String> roleNames = roleNames(grants);
            return new AccessDecision(allow, roleNames);

        } catch (Exception e) {
            // Fail closed: log.warn without stack trace or user data
            log.warn("Access evaluation error for user {} on organization {}: {}",
                    userId, organizationId, e.getMessage());
            return new AccessDecision(false, EMPTY_ROLES);
        }
    }

    /**
     * Core evaluation logic (extracted for testability and clarity).
     */
    static boolean evaluate(List<RoleAssignment> grants, UUID hotelId, Permission permission) {
        for (RoleAssignment grant : grants) {
            if (isAllowed(grant, hotelId, permission)) {
                return true;
            }
        }
        return false;
    }

    /** Check a single grant against the requested permission. */
    private static boolean isAllowed(RoleAssignment grant, UUID hotelId, Permission permission) {
        Role role = grant.getRole();
        ScopeType scopeType = grant.getScopeType();
        UUID scopeId = grant.getScopeId();

        if (permission == Permission.HOTEL_READ) {
            return true; // any active grant on the organization
        }

        if (permission == Permission.RESERVATION_READ) {
            return role == Role.ORGANIZATION_OWNER || role == Role.HOTEL_OWNER
                    || role == Role.HOTEL_MANAGER || role == Role.HOTEL_STAFF;
        }

        switch (role) {
            case ORGANIZATION_OWNER:
                // ORGANIZATION_OWNER with scope ORGANIZATION -> allows all permissions for any hotel
                if (scopeType == ScopeType.ORGANIZATION) {
                    return true;
                }
                return false;

            case HOTEL_OWNER:
            case HOTEL_MANAGER:
                // Requires scope HOTEL and matching scopeId == hotelId
                if (scopeType == ScopeType.HOTEL && scopeId != null && scopeId.equals(hotelId)) {
                    // HOTEL_CREATE is not allowed for hotel-scoped roles
                    if (permission == Permission.HOTEL_CREATE) {
                        return false;
                    }
                    // HOTEL_MANAGE and ROOM_MANAGE are allowed for own hotel
                    return (permission == Permission.HOTEL_MANAGE || permission == Permission.ROOM_MANAGE);
                }
                return false;

            case HOTEL_STAFF:
                // No permissions in this version
                return false;

            default:
                return false;
        }
    }

    /** Collect role names from the grant set. */
    private static List<String> roleNames(List<RoleAssignment> grants) {
        List<String> names = new ArrayList<>(grants.size());
        for (RoleAssignment grant : grants) {
            names.add(grant.getRole().name());
        }
        return names;
    }
}
