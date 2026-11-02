package com.waylo.pricingservice.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request to create promo code(s) (§4). */
public record PromoCodeRequest(
        @NotNull UUID campaignId,
        @NotNull List<CodeItem> codes
) {
    public record CodeItem(
            @NotBlank String code,
            Integer maxUsesTotal,
            @NotNull LocalDateTime validFrom,
            @NotNull LocalDateTime validUntil
    ) {}
}
