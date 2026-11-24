package com.waylo.paymentservice.exception;

/** 400 with a business error code (VALIDATION_ERROR, INVALID_IBAN). */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
