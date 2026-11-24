package com.waylo.crmservice.controller;

import com.waylo.crmservice.domain.SupportCase;
import com.waylo.crmservice.dto.ApiResponse;
import com.waylo.crmservice.dto.SupportDtos.*;
import com.waylo.crmservice.exception.AccessDeniedException;
import com.waylo.crmservice.service.SupportCaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** RM-36 support cases. Identity comes from the gateway (X-User-Id / X-User-Role). There is no live chat. */
@RestController
public class SupportCaseController {

    private final SupportCaseService service;

    public SupportCaseController(SupportCaseService service) {
        this.service = service;
    }

    @GetMapping("/api/support/capabilities")
    public ResponseEntity<ApiResponse<Capabilities>> capabilities() {
        return ResponseEntity.ok(ApiResponse.of(Capabilities.current()));
    }

    // ---- customer ----

    @PostMapping("/api/me/support-cases")
    public ResponseEntity<ApiResponse<CaseResponse>> open(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody OpenCaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.open(userId, req)));
    }

    @GetMapping("/api/me/support-cases")
    public ResponseEntity<ApiResponse<List<CaseResponse>>> mine(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(service.listMine(userId)));
    }

    @GetMapping("/api/me/support-cases/{id}")
    public ResponseEntity<ApiResponse<CaseResponse>> mineById(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.getMine(userId, id)));
    }

    @PostMapping("/api/me/support-cases/{id}/messages")
    public ResponseEntity<ApiResponse<CaseResponse>> mineReply(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id,
                                                              @Valid @RequestBody MessageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.customerReply(userId, id, req.body())));
    }

    @PostMapping("/api/me/support-cases/{id}/close")
    public ResponseEntity<ApiResponse<CaseResponse>> mineClose(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.closeMine(userId, id)));
    }

    // ---- partner (RESERVATION_READ on the organization) ----

    @GetMapping("/api/partner/organizations/{orgId}/support-cases")
    public ResponseEntity<ApiResponse<List<CaseResponse>>> partnerList(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForOrganization(userId, orgId)));
    }

    @GetMapping("/api/partner/organizations/{orgId}/support-cases/{id}")
    public ResponseEntity<ApiResponse<CaseResponse>> partnerGet(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID orgId,
                                                               @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.getForOrganization(userId, orgId, id)));
    }

    @PostMapping("/api/partner/organizations/{orgId}/support-cases/{id}/messages")
    public ResponseEntity<ApiResponse<CaseResponse>> partnerReply(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID orgId,
                                                                 @PathVariable UUID id, @Valid @RequestBody MessageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.partnerReply(userId, orgId, id, req.body())));
    }

    // ---- admin ----

    @GetMapping("/api/admin/support-cases")
    public ResponseEntity<ApiResponse<List<CaseResponse>>> adminList(@RequestHeader("X-User-Id") UUID userId,
                                                                    @RequestHeader(value = "X-User-Role", required = false) String role,
                                                                    @RequestParam(required = false) SupportCase.Status status) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.listAll(status)));
    }

    @GetMapping("/api/admin/support-cases/{id}")
    public ResponseEntity<ApiResponse<CaseResponse>> adminGet(@RequestHeader("X-User-Id") UUID userId,
                                                             @RequestHeader(value = "X-User-Role", required = false) String role,
                                                             @PathVariable UUID id) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.getAny(id)));
    }

    @PostMapping("/api/admin/support-cases/{id}/messages")
    public ResponseEntity<ApiResponse<CaseResponse>> adminReply(@RequestHeader("X-User-Id") UUID userId,
                                                               @RequestHeader(value = "X-User-Role", required = false) String role,
                                                               @PathVariable UUID id, @Valid @RequestBody MessageRequest req) {
        requireAdmin(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.adminReply(userId, id, req.body())));
    }

    @PostMapping("/api/admin/support-cases/{id}/close")
    public ResponseEntity<ApiResponse<CaseResponse>> adminClose(@RequestHeader("X-User-Id") UUID userId,
                                                               @RequestHeader(value = "X-User-Role", required = false) String role,
                                                               @PathVariable UUID id) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.of(service.adminClose(id)));
    }

    static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Admin role required");
    }
}
