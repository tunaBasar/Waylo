package com.waylo.flightseatinventoryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class FlightDtos {
    private FlightDtos() {}

    /** seatNumber optional: omitted = cheapest free seat of the offered cabin. */
    public record HoldRequest(
            @NotBlank String offerId,
            String seatNumber,
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} '\\-]+$") String passengerFirstName,
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[\\p{L} '\\-]+$") String passengerLastName,
            @NotNull UUID principalUserId,
            @NotBlank @Size(max = 100) String idempotencyKey,
            @Size(max = 100) String orderCorrelationId) {}
}
