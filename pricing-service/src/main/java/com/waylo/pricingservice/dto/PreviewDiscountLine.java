package com.waylo.pricingservice.dto;

import java.math.BigDecimal;

/** Discount line item in preview responses. */
public record PreviewDiscountLine(
        String description,
        BigDecimal amount,
        String fundedBy
) {}
