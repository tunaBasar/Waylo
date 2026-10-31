package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Internal payment-service escrow API. X-Internal-Token is added by {@link PaymentFeignConfig}. */
@FeignClient(name = "payment-service", contextId = "paymentEscrowClient", path = "/internal/escrow-items",
        configuration = PaymentFeignConfig.class)
public interface PaymentEscrowClient {

    /** HELD -> REFUNDED (idempotent). 422 ESCROW_ALREADY_RELEASED when the supplier was already paid. */
    @PostMapping("/{orderItemId}/refund")
    Envelope<Object> refund(@PathVariable("orderItemId") UUID orderItemId);

    record Envelope<T>(boolean success, T data, String message) {}
}
