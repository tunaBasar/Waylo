package com.waylo.pricingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Request to create a campaign (§4). */
public record CampaignRequest(
        @NotBlank String name,
        @NotBlank String vertical,
        UUID scopeOrgId,
        @NotBlank String discountType,
        @NotNull BigDecimal discountValue,
        BigDecimal minOrderAmount,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        Integer maxUsesGlobal,
        Integer maxUsesPerUser,
        @NotBlank String fundedBy,
        String description
) {}
