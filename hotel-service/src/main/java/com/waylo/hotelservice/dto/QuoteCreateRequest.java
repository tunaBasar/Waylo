package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to create a hotel quote.
 * NOTE: There is intentionally no client total/currency field.
 * If a client sends extra JSON properties they are ignored by Jackson.
 */
public record QuoteCreateRequest(
        @NotNull UUID hotelId,
        @NotNull UUID roomTypeId,
        @NotNull UUID ratePlanId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) @Max(10) Integer rooms,
        @NotNull @Min(1) @Max(20) Integer adults,
        @Size(max = 10) List<@NotNull @Min(0) @Max(17) Integer> childrenAges
) {}
