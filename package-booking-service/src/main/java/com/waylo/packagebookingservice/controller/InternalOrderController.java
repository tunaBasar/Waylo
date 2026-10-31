package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.InternalOrderResponse;
import com.waylo.packagebookingservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service only (X-Internal-Token enforced by InternalTokenFilter, never routed by the gateway). */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InternalOrderResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.getInternal(id)));
    }

    /** Called by payment-service after money is captured into escrow. Confirms hotel holds, sets CONFIRMED. Idempotent. */
    @PostMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<InternalOrderResponse>> paid(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.markPaid(id), "Order marked paid"));
    }
}
