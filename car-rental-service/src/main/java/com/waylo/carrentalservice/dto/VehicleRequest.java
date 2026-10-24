package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank(message = "Plate number is required")
        @Size(max = 20) String plateNumber,

        @NotBlank(message = "Brand is required")
        @Size(max = 100) String brand,

        @NotBlank(message = "Model is required")
        @Size(max = 100) String model,

        @NotNull(message = "Year is required")
        @jakarta.validation.constraints.Min(value = 1990, message = "Year must be 1990 or later")
        Integer year,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @Size(max = 50) String color,

        @NotNull(message = "Seat count is required")
        @jakarta.validation.constraints.Min(value = 1, message = "Seat count must be at least 1")
        @jakarta.validation.constraints.Max(value = 60)
        Integer seatCount,

        @NotNull(message = "Daily rate is required")
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal dailyRate,

        @NotBlank(message = "Location is required")
        @Size(max = 255) String location,

        @PositiveOrZero Integer mileage
) {}
