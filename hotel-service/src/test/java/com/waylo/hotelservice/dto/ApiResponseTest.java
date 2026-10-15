package com.waylo.hotelservice.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void of_withData_only() {
        ApiResponse<String> response = ApiResponse.of("test");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("test");
        assertThat(response.message()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void of_withData_and_message() {
        ApiResponse<String> response = ApiResponse.of("result", "Success");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("result");
        assertThat(response.message()).isEqualTo("Success");
    }

    @Test
    void recordConstructor_withAllFields() {
        UUID id = UUID.randomUUID();
        ApiResponse<String> response = new ApiResponse<>(true, id.toString(), "Created", null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(id.toString());
        assertThat(response.message()).isEqualTo("Created");
    }
}
