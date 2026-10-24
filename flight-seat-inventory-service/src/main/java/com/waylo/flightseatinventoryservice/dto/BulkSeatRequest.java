package com.waylo.flightseatinventoryservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BulkSeatRequest(
        @NotBlank(message = "Flight number is required")
        String flightNumber,

        @NotBlank(message = "Seat type is required")
        String seatType,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer and 2 fraction digits")
        BigDecimal price,

        @NotNull(message = "fromRow is required")
        @Min(value = 1, message = "fromRow must be >= 1")
        @Max(value = 999, message = "fromRow must be <= 999")
        Integer fromRow,

        @NotNull(message = "toRow is required")
        @Min(value = 1, message = "toRow must be >= 1")
        @Max(value = 999, message = "toRow must be <= 999")
        Integer toRow,

        @NotBlank(message = "letters is required")
        String letters
) {}
