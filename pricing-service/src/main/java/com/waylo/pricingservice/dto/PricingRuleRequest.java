package com.waylo.pricingservice.dto;

import com.waylo.pricingservice.domain.PricingType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PricingRuleRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Pricing type is required")
        PricingType pricingType,

        @NotNull(message = "Base price is required")
        @Min(value = 0, message = "Base price must be non-negative")
        BigDecimal basePrice,

        BigDecimal discountPercentage,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
        String currency,

        String description
) {}
