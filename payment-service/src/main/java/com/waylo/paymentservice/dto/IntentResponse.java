package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** testMode is true when provider is TEST: the UI must then show the "TEST ODEME" label. */
public record IntentResponse(UUID id, UUID orderId, BigDecimal amount, String currency, PaymentIntentStatus status,
                             String provider, boolean testMode, String failureCode, Instant createdAt, Instant updatedAt) {
    public static IntentResponse from(PaymentIntent p) {
        return new IntentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getProvider(), "TEST".equals(p.getProvider()), p.getFailureCode(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
