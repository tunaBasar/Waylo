package com.waylo.hotelservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyRateResponse(
        UUID id,
        UUID hotelId,
        UUID roomTypeId,
        UUID ratePlanId,
        LocalDate stayDate,
        Integer occupancy,
        BigDecimal amount,
        String currency,
        Long version,
        Instant updatedAt
) {}
