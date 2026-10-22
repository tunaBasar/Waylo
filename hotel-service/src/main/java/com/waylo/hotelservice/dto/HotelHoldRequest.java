package com.waylo.hotelservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for creating a hotel hold.
 * Rooms, stay dates, and pricing come from the quote only.
 */
public record HotelHoldRequest(
        @NotNull UUID quoteId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        UUID principalUserId,
        UUID orderCorrelationId
) {}
