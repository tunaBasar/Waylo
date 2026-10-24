package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.Rental;
import com.waylo.carrentalservice.domain.RentalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RentalResponse(
        UUID id,
        UUID vehicleId,
        UUID customerId,
        LocalDate pickupDate,
        LocalDate returnDate,
        BigDecimal dailyRate,
        BigDecimal totalPrice,
        RentalStatus status,
        Instant createdAt
) {
    public static RentalResponse from(Rental r) {
        return new RentalResponse(r.getId(), r.getVehicleId(), r.getCustomerId(), r.getPickupDate(),
                r.getReturnDate(), r.getDailyRate(), r.getTotalPrice(), r.getStatus(), r.getCreatedAt());
    }
}
