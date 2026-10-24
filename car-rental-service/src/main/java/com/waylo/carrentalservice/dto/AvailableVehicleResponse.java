package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.Vehicle;
import com.waylo.carrentalservice.domain.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableVehicleResponse(
        UUID id,
        String plateNumber,
        String brand,
        String model,
        VehicleType vehicleType,
        int seatCount,
        BigDecimal dailyRate,
        String currency,
        String location
) {
    public static AvailableVehicleResponse from(Vehicle v) {
        return new AvailableVehicleResponse(v.getId(), v.getPlateNumber(), v.getBrand(), v.getModel(),
                v.getVehicleType(), v.getSeatCount(), v.getDailyRate(), v.getCurrency(), v.getLocation());
    }
}
