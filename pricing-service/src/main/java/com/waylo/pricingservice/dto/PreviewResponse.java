package com.waylo.pricingservice.dto;

import java.math.BigDecimal;
import java.util.List;

/** Customer-facing preview response — no reservation ID (§5). */
public record PreviewResponse(
        BigDecimal netAmount,
        List<PreviewDiscountLine> discountLines,
        BigDecimal taxAmount
) {}

