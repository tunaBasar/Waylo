package com.waylo.hotelservice.dto;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record DailyInventoryResponse(
        UUID id,
        UUID hotelId,
        UUID roomTypeId,
        LocalDate stayDate,
        int allotment,
        int outOfService,
        int held,
        int confirmed,
        int available,
        boolean stopSell,
        Long version,
        Instant updatedAt
) {}
