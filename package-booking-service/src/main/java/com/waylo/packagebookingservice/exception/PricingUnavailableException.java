package com.waylo.packagebookingservice.exception;

/** pricing-service could not be reached or failed. */
public class PricingUnavailableException extends RuntimeException {
    public PricingUnavailableException(String message) { super(message); }
}
