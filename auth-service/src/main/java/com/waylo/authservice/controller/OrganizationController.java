package com.waylo.authservice.controller;

import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.CreateOrganizationRequest;
import com.waylo.authservice.dto.MembershipResponse;
import com.waylo.authservice.dto.OrganizationResponse;
import com.waylo.authservice.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/organizations")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        OrganizationResponse resp = organizationService.createOrganization(userId, req);
        return ResponseEntity.status(201).body(ApiResponse.success("Organization created", resp));
    }

    @GetMapping("/me/memberships")
    public ResponseEntity<ApiResponse<List<MembershipResponse>>> listMemberships(
            @RequestHeader("X-User-Id") UUID userId) {
        List<MembershipResponse> list = organizationService.listMemberships(userId);
        return ResponseEntity.ok(ApiResponse.success("OK", list));
    }
}
