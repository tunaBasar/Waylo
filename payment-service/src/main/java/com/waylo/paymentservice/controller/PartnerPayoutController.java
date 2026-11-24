package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.domain.PayoutStatus;
import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.service.PayoutService;
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

@RestController
@RequestMapping("/api/partner/organizations/{organizationId}/payouts")
public class PartnerPayoutController {

    private final PayoutService service;

    public PartnerPayoutController(PayoutService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<PayoutDtos.BalanceResponse>> balance(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(ApiResponse.of(service.balance(userId, organizationId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PayoutDtos.PageResponse<PayoutDtos.PayoutResponse>>> list(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId,
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(service.list(userId, organizationId, status, page, size)));
    }

    @GetMapping("/{payoutId}")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> get(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId, @PathVariable UUID payoutId) {
        return ResponseEntity.ok(ApiResponse.of(service.get(userId, organizationId, payoutId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> request(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID organizationId, @Valid @RequestBody PayoutDtos.RequestPayoutRequest body) {
        PayoutService.RequestResult r = service.request(userId, userEmail, organizationId, idempotencyKey, body.currency(), body.amount());
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(r.payout(), r.created() ? "Payout requested" : "Payout already requested"));
    }

    @PostMapping("/{payoutId}/cancel")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> cancel(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId, @PathVariable UUID payoutId) {
        return ResponseEntity.ok(ApiResponse.of(service.partnerCancel(userId, organizationId, payoutId), "Payout cancelled"));
    }
}
