package com.waylo.hotelservice.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Availability response listing all sellable offers for a hotel and stay window.
 */
public record AvailabilityResponse(
        UUID hotelId,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int rooms,
        int adults,
        List<Integer> childrenAges,
        int occupancy,
        List<AvailableOfferResponse> offers
) {}
