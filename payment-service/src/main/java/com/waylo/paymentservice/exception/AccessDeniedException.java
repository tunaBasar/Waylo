package com.waylo.paymentservice.exception;

public class AccessDeniedException extends RuntimeException {

    public static final String ADMIN_CANNOT_PURCHASE = "ADMIN_CANNOT_PURCHASE";

    private final String code;

    public AccessDeniedException(String message) { this("FORBIDDEN", message); }

    public AccessDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
