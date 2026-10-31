package com.waylo.paymentservice.domain;

/**
 * REQUIRES_PAYMENT -> AUTHORIZED (captured into escrow) -> PARTIALLY_RELEASED -> RELEASED
 *                                                       -> PARTIALLY_REFUNDED / REFUNDED
 * REQUIRES_PAYMENT -> FAILED (card declined, may be retried via the intent endpoint) | CANCELLED (terminal).
 */
public enum PaymentIntentStatus {
    REQUIRES_PAYMENT, AUTHORIZED, PARTIALLY_RELEASED, RELEASED, REFUNDED, PARTIALLY_REFUNDED, FAILED, CANCELLED
}
