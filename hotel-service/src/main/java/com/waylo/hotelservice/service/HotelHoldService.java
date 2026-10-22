package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.HotelHoldRequest;
import com.waylo.hotelservice.dto.HotelHoldResponse;

import java.util.UUID;

/**
 * Service interface for hotel hold operations.
 * Holds reserve inventory for a quote within a time window.
 */
public interface HotelHoldService {

    /**
     * Create a hold for the given quote.
     * Validates quote existence, expiry, inventory availability,
     * idempotency, and principal user ownership.
     */
    HotelHoldResponse hold(HotelHoldRequest request);

    /**
     * Transition a held hold to CONFIRMED.
     */
    HotelHoldResponse confirm(UUID holdId);

    /**
     * Transition a held hold to RELEASED.
     */
    HotelHoldResponse release(UUID holdId);

    /**
     * Expire all HELD holds whose expiresAt <= now.
     * Returns the number of holds expired.
     */
    int releaseExpired(int batchSize);

    /**
     * Get a hold by its ID.
     */
    HotelHoldResponse findById(UUID holdId);
}
