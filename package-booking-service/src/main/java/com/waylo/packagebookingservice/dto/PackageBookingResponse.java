package com.waylo.packagebookingservice.dto;

import com.waylo.packagebookingservice.domain.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PackageBookingResponse(
        UUID id,
        UUID customerId,
        UUID flightId,
        UUID hotelReservationId,
        UUID vehicleReservationId,
        BookingStatus status,
        BigDecimal totalPrice,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
