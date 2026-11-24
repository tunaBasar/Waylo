package com.waylo.crmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** auth-service internal access check. Requires a signed X-Service-Token (added globally). */
@FeignClient(name = "auth-service", contextId = "crmAuthAccessClient", path = "/internal")
public interface AuthAccessClient {

    @GetMapping("/access")
    AccessDecision check(@RequestParam("userId") UUID userId,
                         @RequestParam("organizationId") UUID organizationId,
                         @RequestParam("permission") String permission);

    record AccessDecision(boolean allowed, List<String> roles) {}
}
