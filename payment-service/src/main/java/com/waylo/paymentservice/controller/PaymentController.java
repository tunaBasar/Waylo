package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.domain.PaymentStatus;
import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PaymentRequest;
import com.waylo.paymentservice.dto.PaymentResponse;
import com.waylo.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;
    public PaymentController(PaymentService paymentService) { this.paymentService = paymentService; }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(paymentService.create(request), "Payment created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(paymentService.getAll()));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.getByBookingId(bookingId)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.getByCustomerId(customerId)));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.refund(id), "Payment refunded"));
    }
}
