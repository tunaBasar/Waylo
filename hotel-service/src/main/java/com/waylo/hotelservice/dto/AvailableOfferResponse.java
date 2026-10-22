package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.MealPlan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A sellable offer combining a room type, rate plan, nightly rates, and pricing.
 */
public record AvailableOfferResponse(
        UUID roomTypeId,
        String roomTypeCode,
        String roomTypeName,
        int maxAdults,
        int maxChildren,
        int maxOccupancy,
        UUID ratePlanId,
        String ratePlanCode,
        String ratePlanName,
        MealPlan mealPlan,
        CancellationPolicy cancellationPolicy,
        Integer freeCancellationDaysBefore,
        int nights,
        int rooms,
        int occupancy,
        String currency,
        BigDecimal totalAmount,
        List<NightlyRateResponse> nightly
) {}
