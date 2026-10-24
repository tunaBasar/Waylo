package com.waylo.carrentalservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRentalRequest(
        @NotNull UUID vehicleId,
        @NotNull LocalDate pickupDate,
        @NotNull LocalDate returnDate
) {}
