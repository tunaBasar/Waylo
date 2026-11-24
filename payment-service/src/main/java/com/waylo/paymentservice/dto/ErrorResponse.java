package com.waylo.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** {@code details} carries extra machine readable fields of some business errors (payable, minimum, from, to...). Omitted when null. */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now(), null);
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, Object> details) {
        this(status, error, message, path, Instant.now(), details);
    }
}
