package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.Vertical;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionRateRequest(
        @NotNull Vertical vertical,
        UUID supplierOrganizationId,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal rate,
        Instant validFrom) {}
