package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.MealPlan;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record RatePlanCreateRequest(
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Code must be 2-30 uppercase alphanumeric or underscore characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "Meal plan is required")
        MealPlan mealPlan,

        @NotNull(message = "Cancellation policy is required")
        CancellationPolicy cancellationPolicy,

        @Min(value = 0, message = "Free cancellation days must be between 0 and 365")
        @Max(value = 365, message = "Free cancellation days must be between 0 and 365")
        Integer freeCancellationDaysBefore
) {}
