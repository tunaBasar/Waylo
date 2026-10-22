package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.MealPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response returned by createQuote and getQuote.
 */
public record QuoteResponse(
        UUID quoteId,
        UUID hotelId,
        UUID roomTypeId,
        String roomTypeCode,
        String roomTypeName,
        UUID ratePlanId,
        String ratePlanCode,
        String ratePlanName,
        MealPlan mealPlan,
        CancellationPolicy cancellationPolicy,
        Integer freeCancellationDaysBefore,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int rooms,
        int adults,
        List<Integer> childrenAges,
        int occupancy,
        String currency,
        BigDecimal totalAmount,
        List<NightlyRateResponse> nightly,
        Instant createdAt,
        Instant expiresAt,
        boolean expired
) {}
