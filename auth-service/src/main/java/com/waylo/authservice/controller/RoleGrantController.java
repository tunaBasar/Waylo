package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.MemberResponse;
import com.waylo.authservice.dto.RoleGrantRequest;
import com.waylo.authservice.dto.RoleGrantResult;
import com.waylo.authservice.service.RoleGrantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
public class RoleGrantController {

    private final RoleGrantService roleGrantService;

    public RoleGrantController(RoleGrantService roleGrantService) {
        this.roleGrantService = roleGrantService;
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> listMembers(
            @PathVariable UUID organizationId,
            @RequestHeader("X-User-Id") UUID actorId) {
        return ResponseEntity.ok(ApiResponse.success("OK", roleGrantService.listMembers(actorId, organizationId)));
    }

    @PostMapping("/{organizationId}/role-grants")
    public ResponseEntity<ApiResponse<RoleGrantResult>> createRoleGrant(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RoleGrantRequest req,
            @RequestHeader("X-User-Id") UUID actorId) {
        RoleGrantResult result = roleGrantService.grantRole(actorId, organizationId, req);
        return ResponseEntity.status(201).body(ApiResponse.success("Role granted", result));
    }

    @DeleteMapping("/{organizationId}/role-grants/{grantId}")
    public ResponseEntity<Void> revokeRoleGrant(
            @PathVariable UUID organizationId,
            @PathVariable UUID grantId,
            @RequestHeader("X-User-Id") UUID actorId) {
        roleGrantService.revokeRole(actorId, organizationId, grantId);
        return ResponseEntity.noContent().build();
    }
}
