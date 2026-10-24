package com.waylo.flightseatinventoryservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlightSeatResponse(
        UUID id,
        String flightNumber,
        String seatNumber,
        String seatType,
        BigDecimal price,
        boolean available,
        Instant createdAt,
        Instant updatedAt
) {}
