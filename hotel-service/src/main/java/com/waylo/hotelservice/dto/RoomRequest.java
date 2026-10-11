package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        @NotNull(message = "Room type is required")
        RoomType roomType,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        @NotNull(message = "Price per night is required")
        @Min(value = 0, message = "Price per night must be non-negative")
        BigDecimal pricePerNight,

        Integer floor,

        String description,

        RoomStatus status
) {}
