package com.waylo.paymentservice.domain;

/** HELD -> RELEASED | REFUNDED. Both are terminal. */
public enum EscrowStatus {
    HELD, RELEASED, REFUNDED
}
