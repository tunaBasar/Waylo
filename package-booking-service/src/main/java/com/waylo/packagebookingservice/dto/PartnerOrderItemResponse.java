package com.waylo.packagebookingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Supplier-facing view of an order item. Deliberately carries no customer, payment or other-item data. */
public record PartnerOrderItemResponse(
        UUID orderItemId,
        UUID orderId,
        String orderStatus,
        Instant orderCreatedAt,
        String description,
        BigDecimal amount,
        String currency,
        UUID fulfillmentRef
) {}
