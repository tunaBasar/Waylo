package com.waylo.flightseatinventoryservice.dto;

import jakarta.validation.constraints.NotBlank;

public record SeatReservationRequest(
        @NotBlank(message = "Seat number is required")
        String seatNumber
) {}
