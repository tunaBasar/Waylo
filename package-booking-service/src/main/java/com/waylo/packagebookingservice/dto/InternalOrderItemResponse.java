package com.waylo.packagebookingservice.dto;

import java.time.Instant;
import java.util.UUID;

/** Checkin-service view of one order item (CHECKIN-CONTRACT.md). Flight fields are null for other verticals. */
public record InternalOrderItemResponse(UUID orderItemId, UUID orderId, UUID customerId, String orderStatus, String vertical,
                                        String cancelOutcome, String supplier, String flightNumber, Instant departureAt,
                                        String seatNumber, String pnr, String passengerFirstName, String passengerLastName,
                                        String voucherStatus) {}
