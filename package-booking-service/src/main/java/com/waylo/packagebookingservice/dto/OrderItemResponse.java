package com.waylo.packagebookingservice.dto;

import com.waylo.packagebookingservice.domain.OrderItemVertical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID orderId,
        OrderItemVertical vertical,
        UUID supplierOrganizationId,
        UUID productRef,
        UUID fulfillmentRef,
        String description,
        BigDecimal unitAmount,
        int quantity,
        BigDecimal subtotal,
        String currency,
        Instant createdAt
) {}
