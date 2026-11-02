package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.InternalOrderResponse;
import com.waylo.packagebookingservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.waylo.packagebookingservice.dto.FlightAffectedItem;
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

    /** Used by notification-service: CONFIRMED, not-cancelled FLIGHT items of a flight (departureDate = UTC yyyy-MM-dd, optional). */
    @GetMapping("/flight-items")
    public ResponseEntity<ApiResponse<java.util.List<FlightAffectedItem>>> flightItems(
            @RequestParam String flightNumber,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate departureDate) {
        return ResponseEntity.ok(ApiResponse.of(orderService.findAffectedFlightItems(flightNumber, departureDate)));
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
