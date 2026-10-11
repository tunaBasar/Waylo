package com.waylo.checkinservice.dto;

import com.waylo.checkinservice.domain.CheckinStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckinResponse(
        UUID id, UUID bookingId, UUID customerId, UUID flightId,
        String seatNumber, String boardingPassUrl, CheckinStatus status,
        Instant checkedInAt, Instant createdAt, Instant updatedAt
) {}
