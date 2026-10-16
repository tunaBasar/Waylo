package com.waylo.hotelservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", path = "/internal", configuration = InternalAuthFeignConfig.class)
public interface AuthAccessClient {

    @GetMapping("/access")
    AccessDecision check(@RequestParam("userId") UUID userId,
                         @RequestParam("organizationId") UUID organizationId,
                         @RequestParam(value = "hotelId", required = false) UUID hotelId,
                         @RequestParam("permission") String permission);

    /** IDs of organizations currently in ACTIVE status, used to build the public hotel projection. */
    @GetMapping("/organizations/active-ids")
    List<UUID> activeOrganizationIds();
}
