package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.ConfirmIntentRequest;
import com.waylo.paymentservice.dto.IntentResponse;
import com.waylo.paymentservice.dto.OrderPaymentResponse;
import com.waylo.paymentservice.service.PaymentIntentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Customer payment API. X-User-Id / X-User-Role are set by the api-gateway after JWT verification. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentIntentService intentService;

    public PaymentController(PaymentIntentService intentService) {
        this.intentService = intentService;
    }

    @PostMapping("/orders/{orderId}/intent")
    public ResponseEntity<ApiResponse<IntentResponse>> createIntent(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(intentService.createIntent(userId, role, orderId), "Payment intent ready"));
    }

    @PostMapping("/intents/{id}/confirm")
    public ResponseEntity<ApiResponse<IntentResponse>> confirm(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmIntentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(intentService.confirm(userId, role, id, request.paymentToken())));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> getForOrder(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.of(intentService.getForOrder(userId, orderId)));
    }
}
