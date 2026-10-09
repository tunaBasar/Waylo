package com.waylo.flightseatinventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FlightSeatRequest(
        @NotBlank(message = "Flight number is required")
        @Size(max = 10, message = "Flight number must be at most 10 characters")
        String flightNumber,

        @NotBlank(message = "Seat number is required")
        @Size(max = 5, message = "Seat number must be at most 5 characters")
        String seatNumber,

        @NotBlank(message = "Seat type is required")
        String seatType,

        @NotNull(message = "Price is required")
        BigDecimal price
) {}
