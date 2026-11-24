package com.waylo.packagebookingservice.exception;

/** The order's holds ran out and the order was expired. 409 ORDER_EXPIRED: payment-service cancels/refunds the provider payment. */
public class OrderExpiredException extends RuntimeException {
    public OrderExpiredException(String message) { super(message); }
}
