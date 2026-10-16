package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.MealPlan;

import java.time.Instant;
import java.util.UUID;

public record RatePlanResponse(
        UUID id,
        UUID hotelId,
        String code,
        String name,
        String description,
        MealPlan mealPlan,
        CancellationPolicy cancellationPolicy,
        Integer freeCancellationDaysBefore,
        CatalogStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
