package com.waylo.packagebookingservice.dto;

import java.util.UUID;

/** A CONFIRMED, not-cancelled flight order item affected by a flight event (internal, for notification-service). */
public record FlightAffectedItem(UUID orderItemId, UUID orderId, UUID customerId) {}
