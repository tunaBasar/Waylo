package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RateUpdateItem(
        @NotNull UUID roomTypeId,
        @NotNull UUID ratePlanId,
        @NotNull LocalDate stayDate,
        @NotNull @Min(1) @Max(30) Integer occupancy,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        Long expectedVersion
) {}
