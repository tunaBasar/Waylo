package com.waylo.packagebookingservice.exception;

/** payment-service could not be reached or failed. */
public class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException(String message) { super(message); }
}
