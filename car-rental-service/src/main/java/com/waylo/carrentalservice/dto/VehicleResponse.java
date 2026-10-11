package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String plateNumber,
        String brand,
        String model,
        int year,
        VehicleType vehicleType,
        VehicleStatus status,
        String color,
        int seatCount,
        BigDecimal dailyRate,
        String location,
        Integer mileage,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
