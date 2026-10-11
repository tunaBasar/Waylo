package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.VehicleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank(message = "Plate number is required")
        String plateNumber,

        @NotBlank(message = "Brand is required")
        String brand,

        @NotBlank(message = "Model is required")
        String model,

        @NotNull(message = "Year is required")
        @Min(value = 1990, message = "Year must be 1990 or later")
        Integer year,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        String color,

        @NotNull(message = "Seat count is required")
        @Min(value = 1, message = "Seat count must be at least 1")
        Integer seatCount,

        @NotNull(message = "Daily rate is required")
        @Min(value = 0, message = "Daily rate must be non-negative")
        BigDecimal dailyRate,

        @NotBlank(message = "Location is required")
        String location,

        Integer mileage
) {}
