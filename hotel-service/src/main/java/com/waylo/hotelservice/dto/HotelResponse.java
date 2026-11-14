package com.waylo.hotelservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HotelResponse(
        UUID id,
        UUID organizationId,
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
        Instant updatedAt,
        Double averageScore,
        long reviewCount
) {
    /** Review-less view: averageScore null, reviewCount 0. ReviewService.enrich fills the real aggregate. */
    public HotelResponse(UUID id, UUID organizationId, String name, String city, String country, String address,
                         int starRating, String description, BigDecimal latitude, BigDecimal longitude,
                         boolean active, int roomCount, Instant createdAt, Instant updatedAt) {
        this(id, organizationId, name, city, country, address, starRating, description, latitude, longitude,
                active, roomCount, createdAt, updatedAt, null, 0L);
    }

    public HotelResponse withReviews(Double averageScore, long reviewCount) {
        return new HotelResponse(id, organizationId, name, city, country, address, starRating, description, latitude,
                longitude, active, roomCount, createdAt, updatedAt, averageScore, reviewCount);
    }
}
