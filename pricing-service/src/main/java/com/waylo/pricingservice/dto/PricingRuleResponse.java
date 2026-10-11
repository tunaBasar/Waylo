package com.waylo.pricingservice.dto;

import com.waylo.pricingservice.domain.PricingType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingRuleResponse(
        UUID id,
        String name,
        PricingType pricingType,
        BigDecimal basePrice,
        BigDecimal discountPercentage,
        String currency,
        boolean active,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
