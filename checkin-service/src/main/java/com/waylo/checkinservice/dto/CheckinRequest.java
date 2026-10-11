package com.waylo.checkinservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckinRequest(
        @NotNull(message = "Booking ID is required") UUID bookingId,
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Flight ID is required") UUID flightId,
        String seatNumber
) {}
