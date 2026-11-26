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
        long reviewCount,
        String publicationStatus,
        long contentVersion,
        UUID coverMediaId
) {
    public HotelResponse(UUID id, UUID organizationId, String name, String city, String country, String address,
                         int starRating, String description, BigDecimal latitude, BigDecimal longitude,
                         boolean active, int roomCount, Instant createdAt, Instant updatedAt,
                         Double averageScore, long reviewCount, String publicationStatus, long contentVersion) {
        this(id, organizationId, name, city, country, address, starRating, description, latitude, longitude,
                active, roomCount, createdAt, updatedAt, averageScore, reviewCount, publicationStatus, contentVersion, null);
    }

    /** Review-less view: averageScore null, reviewCount 0. ReviewService.enrich fills the real aggregate. */
    public HotelResponse(UUID id, UUID organizationId, String name, String city, String country, String address,
                         int starRating, String description, BigDecimal latitude, BigDecimal longitude,
                         boolean active, int roomCount, Instant createdAt, Instant updatedAt) {
        this(id, organizationId, name, city, country, address, starRating, description, latitude, longitude,
                active, roomCount, createdAt, updatedAt, null, 0L, null, 0L, null);
    }

    public HotelResponse(UUID id, UUID organizationId, String name, String city, String country, String address,
                         int starRating, String description, BigDecimal latitude, BigDecimal longitude,
                         boolean active, int roomCount, Instant createdAt, Instant updatedAt,
                         Double averageScore, long reviewCount) {
        this(id, organizationId, name, city, country, address, starRating, description, latitude, longitude,
                active, roomCount, createdAt, updatedAt, averageScore, reviewCount, null, 0L, null);
    }

    public HotelResponse withReviews(Double averageScore, long reviewCount) {
        return new HotelResponse(id, organizationId, name, city, country, address, starRating, description, latitude,
                longitude, active, roomCount, createdAt, updatedAt, averageScore, reviewCount, publicationStatus,
                contentVersion, coverMediaId);
    }

    public HotelResponse withCover(UUID coverMediaId) {
        return new HotelResponse(id, organizationId, name, city, country, address, starRating, description, latitude,
                longitude, active, roomCount, createdAt, updatedAt, averageScore, reviewCount, publicationStatus,
                contentVersion, coverMediaId);
    }
}
