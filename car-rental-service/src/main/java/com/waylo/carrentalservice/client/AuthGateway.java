package com.waylo.carrentalservice.client;

import com.waylo.carrentalservice.exception.ApiException;
import com.waylo.carrentalservice.exception.ForbiddenException;
import com.waylo.carrentalservice.exception.ResourceNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Fail-closed wrapper around auth-service. */
@Component
public class AuthGateway {

    private static final Logger log = LoggerFactory.getLogger(AuthGateway.class);

    private final AuthClient client;

    public AuthGateway(AuthClient client) { this.client = client; }

    /** Partner guard: FLEET_MANAGE on a CAR_RENTAL organization. 403 when denied, 503 when auth is unreachable. */
    public OrgInfo requireFleetManager(UUID userId, UUID organizationId) {
        AccessDecision decision;
        try {
            decision = client.check(userId, organizationId, "FLEET_MANAGE");
        } catch (RuntimeException ex) {
            throw unavailable(ex);
        }
        if (decision == null || !decision.allowed()) {
            throw new ForbiddenException("You are not allowed to manage this organization's fleet");
        }
        OrgInfo org = organization(organizationId);
        if (!org.carRental()) {
            throw new ForbiddenException("Organization is not a car rental partner");
        }
        return org;
    }

    /** 404 when missing, 503 when auth is unreachable. */
    public OrgInfo organization(UUID organizationId) {
        try {
            OrgInfo org = client.organization(organizationId);
            if (org == null) {
                throw new ResourceNotFoundException("Organization not found: " + organizationId);
            }
            return org;
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Organization not found: " + organizationId);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unavailable(ex);
        }
    }

    /** For query-time sellability: any failure means "not sellable". */
    public Optional<OrgInfo> lookupOrEmpty(UUID organizationId) {
        try {
            return Optional.ofNullable(client.organization(organizationId));
        } catch (RuntimeException ex) {
            log.warn("Organization lookup failed for {}: {}", organizationId, ex.getMessage());
            return Optional.empty();
        }
    }

    private ApiException unavailable(RuntimeException ex) {
        log.warn("auth-service unavailable: {}", ex.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE",
                "Authorization service is unavailable, try again later");
    }
}
