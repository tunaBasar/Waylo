package com.waylo.pricingservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Customer-facing preview request (§5). */
public record PreviewRequest(
        String vertical,
        UUID supplierOrganizationId,
        BigDecimal grossAmount,
        String currency,
        String promoCode
) {}
