package com.waylo.pricingservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response for a promo code. */
public record PromoCodeResponse(
        UUID id,
        UUID campaignId,
        String code,
        Integer maxUsesTotal,
        Integer usesCount,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {}
