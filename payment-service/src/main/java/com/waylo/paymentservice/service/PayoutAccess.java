package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.AuthAccessClient;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Authorization for partner payout endpoints (PAYOUT-CONTRACT.md sec. 3.2). Reads need RESERVATION_READ on the organization,
 * writes additionally need the ORGANIZATION_OWNER role (so HOTEL_STAFF / HOTEL_MANAGER never move money). Fails closed:
 * a denied or missing decision is 403, an auth-service outage surfaces as FeignException (503).
 */
@Component
public class PayoutAccess {

    public static final String OWNER_ROLE = "ORGANIZATION_OWNER";

    private final AuthAccessClient authAccessClient;

    public PayoutAccess(AuthAccessClient authAccessClient) {
        this.authAccessClient = authAccessClient;
    }

    public AuthAccessClient.AccessDecision requireRead(UUID userId, UUID organizationId) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        AuthAccessClient.AccessDecision decision = authAccessClient.check(userId, organizationId, "RESERVATION_READ");
        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("ACCESS_DENIED", "Not allowed to access payouts of this organization");
        }
        return decision;
    }

    public void requireOwner(UUID userId, UUID organizationId) {
        AuthAccessClient.AccessDecision decision = requireRead(userId, organizationId);
        if (!isOwner(decision)) {
            throw new AccessDeniedException("PAYOUT_OWNER_ONLY", "Only the organization owner can change payout settings");
        }
    }

    public static boolean isOwner(AuthAccessClient.AccessDecision decision) {
        List<String> roles = decision == null ? null : decision.roles();
        return roles != null && roles.contains(OWNER_ROLE);
    }

    public boolean isActive(UUID organizationId) {
        List<UUID> active = authAccessClient.activeOrganizationIds();
        return active != null && active.contains(organizationId);
    }

    public void requireActive(UUID organizationId) {
        if (!isActive(organizationId)) {
            throw new ConflictException("ORGANIZATION_NOT_ACTIVE", "Organization is not active");
        }
    }

    public static void requireAdmin(UUID userId, String role) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("ACCESS_DENIED", "Administrator role required");
    }
}
