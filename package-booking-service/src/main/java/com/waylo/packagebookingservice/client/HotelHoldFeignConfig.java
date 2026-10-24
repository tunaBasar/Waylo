package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.HotelRejectedException;
import com.waylo.packagebookingservice.exception.HotelUnavailableException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

public class HotelHoldFeignConfig {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Bean
    public RequestInterceptor internalTokenInterceptor(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        return template -> template.header("X-Internal-Token", token);
    }

    @Bean
    public ErrorDecoder hotelHoldErrorDecoder() {
        return (methodKey, response) -> decode(response.status(), readBody(response));
    }

    /** 404 and 422 are business answers from hotel-service. Everything else (401 bad token, 5xx) is treated as unavailable. */
    static RuntimeException decode(int status, String body) {
        if (status == 404 || status == 422) {
            String code = status == 404 ? "NOT_FOUND" : "BUSINESS_RULE_VIOLATION";
            String message = "Hotel service rejected the request";
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("error")) code = node.get("error").asString();
                if (node.hasNonNull("message")) message = node.get("message").asString();
            } catch (RuntimeException ignored) {
                // keep defaults
            }
            return new HotelRejectedException(status, code, message);
        }
        return new HotelUnavailableException("Hotel service returned HTTP " + status);
    }

    private static String readBody(feign.Response response) {
        try {
            if (response.body() == null) return "";
            return new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
