package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.dto.PagedApiResponse;
import org.springframework.data.domain.Page;
import com.waylo.authservice.dto.SuspendOrganizationRequest;
import com.waylo.authservice.service.AdminOrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/organizations")
public class AdminOrganizationController {

    private final AdminOrganizationService adminOrganizationService;

    public AdminOrganizationController(AdminOrganizationService adminOrganizationService) {
        this.adminOrganizationService = adminOrganizationService;
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<OrganizationResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin required");
        }
        Page<OrganizationResponse> result = adminOrganizationService.list(status, page, size);
        return ResponseEntity.ok(PagedApiResponse.of(result.getContent(), result.getTotalElements()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin required");
        }
        adminOrganizationService.approve(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Organization approved"));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspend(
            @PathVariable UUID id,
            @Valid @RequestBody SuspendOrganizationRequest req,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin required");
        }
        adminOrganizationService.suspend(id, userId, req.reason());
        return ResponseEntity.ok(ApiResponse.success("Organization suspended"));
    }
}
