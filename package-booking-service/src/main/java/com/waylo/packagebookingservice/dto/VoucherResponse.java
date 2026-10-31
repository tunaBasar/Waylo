package com.waylo.packagebookingservice.dto;

import java.time.Instant;
import java.util.UUID;

/** Customer view. payload is the QR content (WAYLO1:voucherId:code) and must never be logged. */
public record VoucherResponse(UUID voucherId, UUID orderItemId, String description, String payload, String status,
                              Instant validFrom, Instant validUntil, Instant redeemedAt) {}
