package com.waylo.hotelservice.exception;

import java.util.Map;

/** Publication workflow error with an explicit HTTP status, stable error code and optional details (contract sec. 4). */
public class PublicationException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public PublicationException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public PublicationException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public static PublicationException conflict(String code, String message) {
        return new PublicationException(409, code, message);
    }

    public static PublicationException validation(String message) {
        return new PublicationException(400, "VALIDATION_ERROR", message);
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
