package com.waylo.hotelservice.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void recordConstructor() {
        ErrorResponse error = new ErrorResponse(404, "NOT_FOUND", "Not found", "/api/test");

        assertThat(error.status()).isEqualTo(404);
        assertThat(error.error()).isEqualTo("NOT_FOUND");
        assertThat(error.message()).isEqualTo("Not found");
        assertThat(error.path()).isEqualTo("/api/test");
        assertThat(error.timestamp()).isNotNull();
    }

    @Test
    void shorthandConstructor_setsTimestamp() {
        ErrorResponse error = new ErrorResponse(400, "BAD_REQUEST", "Error", "/api/test");

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.timestamp()).isNotNull();
    }
}
