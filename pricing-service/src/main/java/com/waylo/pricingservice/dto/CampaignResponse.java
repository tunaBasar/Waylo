package com.waylo.pricingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Response for a campaign. */
public record CampaignResponse(
        UUID id,
        String name,
        String vertical,
        UUID scopeOrgId,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer maxUsesGlobal,
        Integer maxUsesPerUser,
        String fundedBy,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
