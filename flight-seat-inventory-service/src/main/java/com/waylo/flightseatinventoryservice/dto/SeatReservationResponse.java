package com.waylo.flightseatinventoryservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SeatReservationResponse(
        String flightNumber,
        String seatNumber,
        String reservationId,
        BigDecimal price,
        Instant reservedAt
) {}
