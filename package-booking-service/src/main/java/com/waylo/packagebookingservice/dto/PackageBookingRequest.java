package com.waylo.packagebookingservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PackageBookingRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        UUID flightId,

        UUID hotelReservationId,

        UUID vehicleReservationId,

        String notes
) {}
