package com.waylo.notificationservice.dto;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {
    public ApiResponse(T data) { this(true, data, null, Instant.now()); }
    public ApiResponse(T data, String message) { this(true, data, message, Instant.now()); }
    public static <T> ApiResponse<T> of(T data) { return new ApiResponse<>(data); }
    public static <T> ApiResponse<T> of(T data, String message) { return new ApiResponse<>(data, message); }
}
