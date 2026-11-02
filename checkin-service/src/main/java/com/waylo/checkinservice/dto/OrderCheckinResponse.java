package com.waylo.checkinservice.dto;

import com.waylo.checkinservice.domain.CheckinStatus;
import java.time.Instant;
import java.util.UUID;

/** Check-in of one FLIGHT order item. The boarding credential stays the order item's voucher QR. */
public record OrderCheckinResponse(UUID id, UUID orderItemId, UUID orderId, UUID customerId, String pnr, String passengerName,
                                   String flightNumber, Instant departureAt, String seatNumber, CheckinStatus status,
                                   Instant checkedInAt) {}
