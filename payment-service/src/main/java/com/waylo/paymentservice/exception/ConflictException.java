package com.waylo.paymentservice.exception;

import java.util.Map;

/** 409 with a business error code and optional extra fields (from, to, openPayoutId...). */
public class ConflictException extends RuntimeException {

    private final String code;
    private final transient Map<String, Object> details;

    public ConflictException(String code, String message) { this(code, message, null); }

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
