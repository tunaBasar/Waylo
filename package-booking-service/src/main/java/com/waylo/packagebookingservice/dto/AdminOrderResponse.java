package com.waylo.packagebookingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /api/admin/orders row (PACKAGE-ORDER-CONTRACT.md section 7). */
public record AdminOrderResponse(UUID id, UUID customerId, String status, String paymentState, String currency,
                                 BigDecimal totalAmount, Instant createdAt, List<Item> items) {
    public record Item(UUID id, String vertical, String description, BigDecimal netAmount, String promoCode,
                       String cancelOutcome, BigDecimal refundAmount) {}

    public record Page(List<AdminOrderResponse> items, int page, int size, long total) {}
}
