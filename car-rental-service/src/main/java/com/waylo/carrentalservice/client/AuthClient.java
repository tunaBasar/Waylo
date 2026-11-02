package com.waylo.carrentalservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "auth-service", path = "/internal", configuration = InternalAuthFeignConfig.class)
public interface AuthClient {

    @GetMapping("/access")
    AccessDecision check(@RequestParam("userId") UUID userId,
                         @RequestParam("organizationId") UUID organizationId,
                         @RequestParam("permission") String permission);

    @GetMapping("/organizations/{id}")
    OrgInfo organization(@PathVariable("id") UUID id);
}
