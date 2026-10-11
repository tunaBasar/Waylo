package com.waylo.packagebookingsagaorchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "payment-service", path = "/api/payments")
public interface PaymentServiceClient {

    @PostMapping
    Object createPayment(@org.springframework.web.bind.annotation.RequestBody PaymentRequest request);

    @PostMapping("/{id}/refund")
    Object refundPayment(@PathVariable UUID id);

    record PaymentRequest(
            UUID bookingId,
            UUID customerId,
            java.math.BigDecimal amount,
            String currency,
            String paymentMethod
    ) {}
}
