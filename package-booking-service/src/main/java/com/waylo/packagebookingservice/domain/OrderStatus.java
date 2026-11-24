package com.waylo.packagebookingservice.domain;

/**
 * Siparis durumu. PaymentStatus ve FulfillmentStatus ile ayni state machine degildir;
 * odeme basarisi tek basina siparis onayi anlamina gelmez (PRODUCT-ARCHITECTURE.md SS5).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED,
    /** PENDING order whose holds ran out unpaid (PendingOrderExpiryJob). Terminal, a late payment is rejected with 409 ORDER_EXPIRED. */
    EXPIRED
}
