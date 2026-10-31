package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.EscrowItemResponse;
import com.waylo.paymentservice.service.EscrowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service only (X-Internal-Token enforced by InternalTokenFilter, never routed by the gateway). */
@RestController
@RequestMapping("/internal/escrow-items")
public class InternalEscrowController {

    private final EscrowService escrowService;

    public InternalEscrowController(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @PostMapping("/{orderItemId}/release")
    public ResponseEntity<ApiResponse<EscrowItemResponse>> release(@PathVariable UUID orderItemId) {
        return ResponseEntity.ok(ApiResponse.of(escrowService.release(orderItemId), "Escrow item released"));
    }

    @PostMapping("/{orderItemId}/refund")
    public ResponseEntity<ApiResponse<EscrowItemResponse>> refund(@PathVariable UUID orderItemId) {
        return ResponseEntity.ok(ApiResponse.of(escrowService.refund(orderItemId), "Escrow item refunded"));
    }
}
