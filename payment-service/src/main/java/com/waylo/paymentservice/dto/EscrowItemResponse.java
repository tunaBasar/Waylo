package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.ReleaseTrigger;
import com.waylo.paymentservice.domain.Vertical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Internal / admin projection with the commission snapshot. */
public record EscrowItemResponse(UUID id, UUID orderId, UUID orderItemId, Vertical vertical, UUID supplierOrganizationId,
                                 BigDecimal grossAmount, BigDecimal commissionRate, BigDecimal commissionAmount,
                                 BigDecimal supplierAmount, String currency, EscrowStatus status,
                                 ReleaseTrigger releaseTrigger, Instant heldAt, Instant releasedAt, Instant refundedAt,
                                 BigDecimal chargedAmount, BigDecimal discountAmount, String discountFundedBy) {
    public static EscrowItemResponse from(EscrowItem e) {
        return new EscrowItemResponse(e.getId(), e.getOrderId(), e.getOrderItemId(), e.getVertical(), e.getSupplierOrganizationId(),
                e.getGrossAmount(), e.getCommissionRate(), e.getCommissionAmount(), e.getSupplierAmount(), e.getCurrency(),
                e.getStatus(), e.getReleaseTrigger(), e.getHeldAt(), e.getReleasedAt(), e.getRefundedAt(),
                e.getChargedAmount(), e.getDiscountAmount(), e.getDiscountFundedBy());
    }
}
