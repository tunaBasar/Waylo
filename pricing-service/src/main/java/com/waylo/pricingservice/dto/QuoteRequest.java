package com.waylo.pricingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Request for internal pricing quote (§3). */
public record QuoteRequest(
        @NotBlank String vertical,
        @NotNull UUID supplierOrganizationId,
        @NotNull @DecimalMin("0") BigDecimal grossAmount,
        @NotBlank String currency,
        @NotBlank String customerId,
        String promoCode,
        UUID orderId,
        BigDecimal commissionRate
) {}
