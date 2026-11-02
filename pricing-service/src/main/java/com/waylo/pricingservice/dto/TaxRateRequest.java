package com.waylo.pricingservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request to create a tax rate (§4). */
public record TaxRateRequest(
        @NotBlank String vertical,
        UUID scopeOrgId,
        @NotBlank String taxType,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal rate,
        @NotNull Boolean inclusive,
        @NotNull LocalDateTime validFrom
) {}
