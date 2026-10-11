package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        UUID hotelId,
        String hotelName,
        String roomNumber,
        RoomType roomType,
        RoomStatus status,
        int capacity,
        BigDecimal pricePerNight,
        Integer floor,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
