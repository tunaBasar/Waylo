package com.waylo.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** auth-service internal access check. Requires a signed X-Service-Token (added globally). */
@FeignClient(name = "auth-service", contextId = "authAccessClient", path = "/internal")
public interface AuthAccessClient {

    @GetMapping("/access")
    AccessDecision check(@RequestParam("userId") UUID userId,
                         @RequestParam("organizationId") UUID organizationId,
                         @RequestParam("permission") String permission);

    /** IDs of organizations in ACTIVE status (partner payouts are only requested by active organizations). */
    @GetMapping("/organizations/active-ids")
    List<UUID> activeOrganizationIds();

    record AccessDecision(boolean allowed, List<String> roles) {}
}
