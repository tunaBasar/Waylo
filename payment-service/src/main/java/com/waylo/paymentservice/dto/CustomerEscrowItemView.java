package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.ReleaseTrigger;
import com.waylo.paymentservice.domain.Vertical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Customer facing projection: no commission, no supplier split, no ledger. */
public record CustomerEscrowItemView(UUID orderItemId, Vertical vertical, BigDecimal amount, String currency,
                                     EscrowStatus status, ReleaseTrigger releaseTrigger,
                                     Instant heldAt, Instant releasedAt, Instant refundedAt) {
    public static CustomerEscrowItemView from(EscrowItem e) {
        return new CustomerEscrowItemView(e.getOrderItemId(), e.getVertical(), e.getGrossAmount(), e.getCurrency(),
                e.getStatus(), e.getReleaseTrigger(), e.getHeldAt(), e.getReleasedAt(), e.getRefundedAt());
    }
}
