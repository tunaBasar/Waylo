package com.waylo.hotelservice.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        Map<String, Object> details
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now(), null);
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, Object> details) {
        this(status, error, message, path, Instant.now(), details);
    }
}
