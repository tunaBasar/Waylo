package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.domain.PaymentReconciliation;
import com.waylo.paymentservice.domain.ReconciliationStatus;
import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.repository.PaymentReconciliationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ADMIN-only report of PaymentReconciliationJob decisions (RM-27). Needs a gateway route for /api/admin/payment-reconciliation. */
@RestController
@RequestMapping("/api/admin")
public class AdminReconciliationController {

    private final PaymentReconciliationRepository repository;

    public AdminReconciliationController(PaymentReconciliationRepository repository) {
        this.repository = repository;
    }

    public record Item(UUID id, UUID paymentIntentId, UUID orderId, String providerPaymentId, String intentStatus,
                       String orderStatus, String status, String detail, Instant createdAt) {
        static Item from(PaymentReconciliation r) {
            return new Item(r.getId(), r.getPaymentIntentId(), r.getOrderId(), r.getProviderPaymentId(),
                    r.getIntentStatus().name(), r.getOrderStatus(), r.getStatus().name(), r.getDetail(), r.getCreatedAt());
        }
    }

    public record PageResponse(List<Item> items, int page, int size, long total) {}

    @GetMapping("/payment-reconciliation")
    public ResponseEntity<ApiResponse<PageResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) ReconciliationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Administrator role required");
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        Page<PaymentReconciliation> result = repository.search(status, PageRequest.of(p, s));
        return ResponseEntity.ok(ApiResponse.of(new PageResponse(result.getContent().stream().map(Item::from).toList(),
                p, s, result.getTotalElements())));
    }
}
