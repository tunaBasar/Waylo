package com.waylo.authservice.controller;

import com.waylo.authservice.domain.Permission;
import com.waylo.authservice.dto.AccessDecision;
import com.waylo.authservice.service.AccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal access-check endpoint for domain services.
 *
 * <p>NOT routed by the API gateway (gateway routes only /api/**).
 * This path must be permitted in SecurityConfig (see coordinator notes).
 */
@RestController
@RequestMapping("/internal")
public class InternalAccessController {

    private final AccessService accessService;

    public InternalAccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    /**
     * Check access. Returns plain AccessDecision JSON (no ApiResponse wrapper).
     *
     * <p>Example: GET /internal/access?userId=abc-123&organizationId=def-456&permission=HOTEL_MANAGE
     */
    @GetMapping("/access")
    public ResponseEntity<AccessDecision> checkAccess(
            @RequestParam UUID userId,
            @RequestParam UUID organizationId,
            @RequestParam(required = false) UUID hotelId,
            @RequestParam Permission permission) {

        AccessDecision decision = accessService.check(userId, organizationId, hotelId, permission);
        return ResponseEntity.ok(decision);
    }
}
