package com.waylo.packagebookingservice.exception;

public class AccessDeniedException extends RuntimeException {

    public static final String ADMIN_CANNOT_PURCHASE = "ADMIN_CANNOT_PURCHASE";

    private final String code;

    public AccessDeniedException(String message) {
        this(message, "FORBIDDEN");
    }

    public AccessDeniedException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
