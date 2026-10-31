package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, ADMIN-only view of the old mock `payments` table (history). The mock POST /api/payments and
 * POST /api/payments/{id}/refund were removed: they let anyone invent a completed payment.
 */
@RestController
@RequestMapping("/api/payments")
public class LegacyPaymentController {

    private final PaymentService paymentService;

    public LegacyPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAll(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(paymentService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(paymentService.getById(id)));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByBookingId(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID bookingId) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(paymentService.getByBookingId(bookingId)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByCustomerId(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID customerId) {
        requireAdmin(userId, role);
        return ResponseEntity.ok(ApiResponse.of(paymentService.getByCustomerId(customerId)));
    }

    static void requireAdmin(UUID userId, String role) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Administrator role required");
    }
}
