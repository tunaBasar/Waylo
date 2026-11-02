package com.waylo.checkinservice.controller;

import com.waylo.checkinservice.dto.ApiResponse;
import com.waylo.checkinservice.dto.CheckinPage;
import com.waylo.checkinservice.dto.OrderCheckinResponse;
import com.waylo.checkinservice.service.OrderCheckinService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class OrderCheckinController {
    private final OrderCheckinService service;

    public OrderCheckinController(OrderCheckinService service) { this.service = service; }

    /** Idempotent: a repeat returns the existing check-in (200). */
    @PostMapping("/api/checkins/order-items/{orderItemId}")
    public ResponseEntity<ApiResponse<OrderCheckinResponse>> checkIn(@PathVariable UUID orderItemId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(service.checkIn(orderItemId, userId), "Checked in"));
    }

    @GetMapping("/api/checkins/me")
    public ResponseEntity<ApiResponse<List<OrderCheckinResponse>>> mine(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(service.mine(userId)));
    }

    @GetMapping("/api/admin/checkins")
    public ResponseEntity<ApiResponse<CheckinPage>> adminList(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(service.adminList(role, page, size)));
    }
}
