package com.waylo.packagebookingsagaorchestrator.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SagaStartRequest(
        @NotNull(message = "Booking ID is required") UUID bookingId,
        @NotNull(message = "Customer ID is required") UUID customerId,
        UUID flightId,
        UUID hotelReservationId,
        UUID vehicleReservationId
) {}
