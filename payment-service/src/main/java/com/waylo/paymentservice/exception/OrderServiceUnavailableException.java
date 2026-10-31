package com.waylo.paymentservice.exception;

/** package-booking-service could not be reached or failed with a non business error. */
public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message) { super(message); }
}
