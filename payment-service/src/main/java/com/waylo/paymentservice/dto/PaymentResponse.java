package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.PaymentMethod;
import com.waylo.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id, UUID bookingId, UUID customerId, BigDecimal amount, String currency,
        PaymentMethod paymentMethod, PaymentStatus status, String transactionRef,
        Instant createdAt, Instant updatedAt
) {}
