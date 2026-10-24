package com.waylo.authservice.dto;

import java.util.List;

/** ApiResponse-compatible envelope for page-able lists: data is the page items, total the full match count. */
public record PagedApiResponse<T>(boolean success, String message, List<T> data, long total) {
    public static <T> PagedApiResponse<T> of(List<T> items, long total) {
        return new PagedApiResponse<>(true, "OK", items, total);
    }
}
