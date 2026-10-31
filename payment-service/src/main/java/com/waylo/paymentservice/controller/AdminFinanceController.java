package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.CommissionRateRequest;
import com.waylo.paymentservice.dto.CommissionRateResponse;
import com.waylo.paymentservice.dto.LedgerResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.service.CommissionService;
import com.waylo.paymentservice.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN-only finance API. Routed by the api-gateway: /api/admin/commission-rates and /api/admin/ledger go to payment-service
 * (more specific routes placed before the auth-service /api/admin/** route).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFinanceController {

    private final CommissionService commissionService;
    private final LedgerService ledgerService;

    public AdminFinanceController(CommissionService commissionService, LedgerService ledgerService) {
        this.commissionService = commissionService;
        this.ledgerService = ledgerService;
    }

    @GetMapping("/commission-rates")
    public ResponseEntity<ApiResponse<List<CommissionRateResponse>>> listRates(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(commissionService.list().stream().map(CommissionRateResponse::from).toList()));
    }

    @PostMapping("/commission-rates")
    public ResponseEntity<ApiResponse<CommissionRateResponse>> createRate(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CommissionRateRequest request) {
        requireAdmin(userId, role);
        var created = commissionService.create(request.vertical(), request.supplierOrganizationId(), request.rate(),
                request.validFrom(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(CommissionRateResponse.from(created), "Commission rate saved"));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<LedgerResponse>> ledger(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam UUID orderId) {
        requireAdmin(userId, role);
        var entries = ledgerService.entriesOfOrder(orderId).stream().map(LedgerResponse.Entry::from).toList();
        return ResponseEntity.ok(ApiResponse.of(new LedgerResponse(orderId, ledgerService.balanceOfOrder(orderId), entries)));
    }

    private static void requireAdmin(UUID userId, String role) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Administrator role required");
    }
}
