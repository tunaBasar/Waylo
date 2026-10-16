package com.waylo.packagebookingservice.dto;

import com.waylo.packagebookingservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        UUID quoteId,
        BigDecimal totalAmount,
        String notes,
        Long version,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}
