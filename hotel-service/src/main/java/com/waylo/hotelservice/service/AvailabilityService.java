package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.AvailabilityResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public availability endpoint - finds all sellable offers for a hotel and stay window.
 * Requires the hotel to be ACTIVE and its owning organization to be ACTIVE via AuthAccessClient.
 */
public interface AvailabilityService {

    /**
     * Find all sellable offers for the given hotel and stay window.
     * Fails closed if AuthAccessClient is unavailable.
     */
    AvailabilityResponse getAvailability(UUID hotelId, LocalDate checkIn, LocalDate checkOut,
                                         int rooms, int adults, List<Integer> childrenAges);
}
