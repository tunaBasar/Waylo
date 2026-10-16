package com.waylo.packagebookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * v1 tek order/tek item kullanir (PRODUCT-ARCHITECTURE.md SS5 "ilk surum bir order/bir otel
 * item kullanir"), ancak liste modeli coklu item'a hazirdir. customerId istekte tasinir;
 * gateway'den gelen X-User-Id ile eslesmezse 403 (self-service musteri siparisi).
 */
public record OrderCreateRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Currency is required")
        String currency,

        UUID quoteId,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @NotEmpty(message = "At least one order item is required")
        @Size(max = 1, message = "v1 supports exactly one order item per order")
        @Valid
        List<OrderItemCreateRequest> items
) {}
