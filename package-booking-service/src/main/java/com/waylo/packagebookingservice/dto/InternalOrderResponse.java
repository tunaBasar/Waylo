package com.waylo.packagebookingservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payment-service view of an order. Amounts are authoritative: payment never trusts client amounts. */
public record InternalOrderResponse(UUID id, UUID customerId, String status, String currency, BigDecimal totalAmount,
                                    List<Item> items) {
    /** amount = net (unit amount x quantity, what the customer pays). grossAmount = supplier price, commission base. */
    public record Item(UUID orderItemId, String vertical, UUID supplierOrganizationId, BigDecimal amount, String currency,
                       BigDecimal grossAmount, BigDecimal discountAmount, String discountFundedBy) {}
}
