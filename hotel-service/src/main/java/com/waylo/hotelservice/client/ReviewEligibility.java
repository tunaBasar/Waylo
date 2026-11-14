package com.waylo.hotelservice.client;

import java.time.LocalDate;
import java.util.UUID;

/** package-booking-service answer for GET /internal/orders/items/{id}/review-eligibility. */
public record ReviewEligibility(UUID orderItemId, boolean eligible, String reason, UUID customerId, UUID fulfillmentRef,
                                UUID supplierOrganizationId, String description, LocalDate checkIn, LocalDate checkOut) {}
