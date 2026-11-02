package com.waylo.pricingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response for internal pricing quote (§3). */
public record QuoteResponse(
        BigDecimal netAmount,
        List<DiscountLine> discountLines,
        BigDecimal taxAmount,
        UUID promoReservationId
) {
    public record DiscountLine(
            String description,
            BigDecimal amount,
            String fundedBy
    ) {}
}
