package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.CatalogStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomTypeResponse(
        UUID id,
        UUID hotelId,
        String code,
        String name,
        String description,
        int maxAdults,
        int maxChildren,
        int maxOccupancy,
        Integer sizeSqm,
        CatalogStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
