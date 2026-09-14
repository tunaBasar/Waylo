package com.waylo.flightservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AirportResponse(
        UUID id,
        String iataCode,
        String name,
        String city,
        String country,
        String timezone,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        Instant updatedAt
) {}
