package com.waylo.packagebookingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** amountReleased is the supplier share (gross minus commission) paid out by payment-service. */
public record RedeemResponse(UUID voucherId, UUID orderItemId, String description, BigDecimal amountReleased,
                             String currency, Instant redeemedAt) {}
