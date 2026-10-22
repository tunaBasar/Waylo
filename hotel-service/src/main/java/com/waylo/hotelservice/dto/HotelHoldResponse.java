package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.HoldStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for a hotel hold operation.
 */
public record HotelHoldResponse(
        UUID holdId,
        UUID quoteId,
        UUID hotelId,
        UUID roomTypeId,
        UUID ratePlanId,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int rooms,
        HoldStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant confirmedAt,
        Instant releasedAt,
        Instant createdAt
) {}
