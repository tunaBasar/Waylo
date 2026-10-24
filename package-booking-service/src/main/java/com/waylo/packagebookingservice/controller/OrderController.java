package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.service.OrderService;
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

import java.util.List;
import java.util.UUID;

/**
 * PRODUCT-ARCHITECTURE.md SS7 onerilen endpoint seti: POST /api/v1/orders,
 * GET /api/v1/me/orders/{id}. Bu is paketinde v1 prefix'i olmadan /api/orders altinda
 * teslim edilir (mevcut servis genelinde /api/v1 kullanilmiyor); self-service musteri
 * siparisi, X-User-Id yalniz api-gateway JWT dogrulamasindan sonra guvenilir sekilde gelir.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Hotel orders: price comes from the server-side hotel hold, never from the client. */
    @PostMapping("/hotel")
    public ResponseEntity<ApiResponse<OrderResponse>> createHotel(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody HotelOrderCreateRequest request) {
        OrderResponse response = orderService.createHotelOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Order created"));
    }

    /** Generic client-priced creation is ADMIN-only: no customer-facing caller remains. Anonymous still gets 401 from the service. */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody OrderCreateRequest request) {
        if (userId != null && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Only administrators may create orders with explicit prices");
        }
        OrderResponse response = orderService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Order created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.getById(userId, id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOwn(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(orderService.listOwn(userId)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID id,
            @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(ApiResponse.of(orderService.cancel(userId, id, expectedVersion), "Order cancelled"));
    }
}
