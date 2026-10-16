package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.MealPlan;
import jakarta.validation.constraints.*;

public record RatePlanUpdateRequest(
        @Pattern(regexp = "^(?=.*\\S).+$", message = "Name must not be blank")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        MealPlan mealPlan,

        CancellationPolicy cancellationPolicy,

        @Min(value = 0, message = "Free cancellation days must be between 0 and 365")
        @Max(value = 365, message = "Free cancellation days must be between 0 and 365")
        Integer freeCancellationDaysBefore,

        CatalogStatus status,

        Long expectedVersion
) {}
