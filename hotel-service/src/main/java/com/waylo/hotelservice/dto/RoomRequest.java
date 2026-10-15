package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RoomRequest(
        @NotBlank(message = "Room number is required")
        @Size(max = 20)
        String roomNumber,

        @NotNull(message = "Room type is required")
        RoomType roomType,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        @NotNull(message = "Price per night is required")
        @DecimalMin(value = "0.00", message = "Price per night must be non-negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerNight,

        Integer floor,

        String description,

        RoomStatus status
) {}
