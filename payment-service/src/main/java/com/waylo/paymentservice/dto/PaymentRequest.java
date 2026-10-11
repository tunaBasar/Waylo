package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        @NotNull(message = "Booking ID is required") UUID bookingId,
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Amount is required") @Min(value = 0, message = "Amount must be non-negative") BigDecimal amount,
        @NotBlank(message = "Currency is required") String currency,
        PaymentMethod paymentMethod
) {}
