package com.waylo.hotelservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "auth-service", path = "/internal", configuration = InternalAuthFeignConfig.class)
public interface AuthAccessClient {

    @GetMapping("/access")
    AccessDecision check(@RequestParam("userId") UUID userId,
                         @RequestParam("organizationId") UUID organizationId,
                         @RequestParam(value = "hotelId", required = false) UUID hotelId,
                         @RequestParam("permission") String permission);
}
