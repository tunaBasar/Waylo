package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.SupplierDtos.*;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.service.SupplierBalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin supplier balance (explicit top-up only) and internal payout/credit used at ticketing. */
@RestController
public class SupplierBalanceController {

    private final SupplierBalanceService service;

    public SupplierBalanceController(SupplierBalanceService service) { this.service = service; }

    @GetMapping("/api/admin/supplier-accounts")
    public ApiResponse<List<AccountResponse>> list(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                   @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ApiResponse.of(service.list().stream().map(AccountResponse::from).toList());
    }

    @PostMapping("/api/admin/supplier-accounts/{supplier}/topups")
    public ResponseEntity<ApiResponse<AccountResponse>> topUp(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                              @RequestHeader(value = "X-User-Role", required = false) String role,
                                                              @PathVariable String supplier, @Valid @RequestBody TopUpRequest r) {
        requireAdmin(userId, role);
        var acc = service.topUp(supplier, r.amount(), r.currency(), r.reference(), r.idempotencyKey(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AccountResponse.from(acc), "Top-up recorded"));
    }

    @GetMapping("/api/admin/supplier-accounts/{supplier}/ledger")
    public ApiResponse<LedgerPage> ledger(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                          @RequestHeader(value = "X-User-Role", required = false) String role,
                                          @PathVariable String supplier,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireAdmin(userId, role);
        var p = service.ledger(supplier, page, size);
        return ApiResponse.of(new LedgerPage(p.getContent().stream().map(LedgerRow::from).toList(), p.getTotalElements(),
                p.getNumber(), p.getSize()));
    }

    @PostMapping("/internal/supplier-payouts")
    public ApiResponse<AccountResponse> payout(@Valid @RequestBody MovementRequest r) {
        return ApiResponse.of(AccountResponse.from(service.payout(r.supplier(), r.orderId(), r.orderItemId(), r.amount(),
                r.currency(), r.idempotencyKey())));
    }

    @PostMapping("/internal/supplier-credits")
    public ApiResponse<AccountResponse> credit(@Valid @RequestBody MovementRequest r) {
        return ApiResponse.of(AccountResponse.from(service.credit(r.supplier(), r.orderId(), r.orderItemId(), r.amount(),
                r.currency(), r.idempotencyKey())));
    }

    private static void requireAdmin(UUID userId, String role) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Administrator role required");
    }
}
