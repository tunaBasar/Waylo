package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.MealPlan;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record RoomTypeCreateRequest(
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Code must be 2-30 uppercase alphanumeric or underscore characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @NotNull(message = "Max adults is required")
        @Min(value = 1, message = "Max adults must be at least 1")
        @Max(value = 20, message = "Max adults must be at most 20")
        Integer maxAdults,

        @NotNull(message = "Max children is required")
        @Min(value = 0, message = "Max children must be at least 0")
        @Max(value = 10, message = "Max children must be at most 10")
        Integer maxChildren,

        @NotNull(message = "Max occupancy is required")
        @Min(value = 1, message = "Max occupancy must be at least 1")
        @Max(value = 30, message = "Max occupancy must be at most 30")
        Integer maxOccupancy,

        @Min(value = 1, message = "Size sqm must be between 1 and 2000")
        @Max(value = 2000, message = "Size sqm must be between 1 and 2000")
        Integer sizeSqm
) {}
