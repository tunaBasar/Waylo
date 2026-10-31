package com.waylo.paymentservice.exception;

/** Thrown when a duplicate commission rate is created (same vertical, org, validFrom). */
public class CommissionRateConflictException extends RuntimeException {

    public CommissionRateConflictException(String message) {
        super(message);
    }
}
