package com.waylo.flightseatinventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SeatReservationRequest(
        @NotBlank(message = "Seat number is required")
        @Size(max = 5, message = "Seat number must be at most 5 characters")
        String seatNumber
) {}
