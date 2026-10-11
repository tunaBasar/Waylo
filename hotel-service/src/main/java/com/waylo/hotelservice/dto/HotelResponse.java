package com.waylo.hotelservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HotelResponse(
        UUID id,
        String name,
        String city,
        String country,
        String address,
        int starRating,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active,
        int roomCount,
        Instant createdAt,
        Instant updatedAt
) {}
