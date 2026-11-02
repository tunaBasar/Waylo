package com.waylo.pricingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Response for a tax rate. */
public record TaxRateResponse(
        UUID id,
        String vertical,
        UUID scopeOrgId,
        String taxType,
        BigDecimal rate,
        Boolean inclusive,
        LocalDateTime validFrom
) {}
