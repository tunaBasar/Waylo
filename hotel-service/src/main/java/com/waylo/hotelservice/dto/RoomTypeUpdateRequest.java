package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CatalogStatus;
import jakarta.validation.constraints.*;

public record RoomTypeUpdateRequest(
        @Pattern(regexp = "^(?=.*\\S).+$", message = "Name must not be blank")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Min(value = 1, message = "Max adults must be at least 1")
        @Max(value = 20, message = "Max adults must be at most 20")
        Integer maxAdults,

        @Min(value = 0, message = "Max children must be at least 0")
        @Max(value = 10, message = "Max children must be at most 10")
        Integer maxChildren,

        @Min(value = 1, message = "Max occupancy must be at least 1")
        @Max(value = 30, message = "Max occupancy must be at most 30")
        Integer maxOccupancy,

        @Min(value = 1, message = "Size sqm must be between 1 and 2000")
        @Max(value = 2000, message = "Size sqm must be between 1 and 2000")
        Integer sizeSqm,

        CatalogStatus status,

        Long expectedVersion
) {}
