package com.waylo.packagebookingservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** refundAmount is what the customer gets back (charged net amount, or 0). cancellable=false carries a reason code. */
public record CancellationPreviewResponse(UUID orderId, boolean cancellable, String blockedReason, String currency,
                                          BigDecimal totalRefund, List<Item> items) {
    public record Item(UUID orderItemId, String description, String policy, Integer freeHoursBefore, String outcome,
                       BigDecimal chargedAmount, BigDecimal refundAmount) {}
}
