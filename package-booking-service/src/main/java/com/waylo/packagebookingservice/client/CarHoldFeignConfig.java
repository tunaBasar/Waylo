package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.CarRejectedException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;

public class CarHoldFeignConfig {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    @Bean public RequestInterceptor internalTokenInterceptor(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        return template -> template.header("X-Internal-Token", token);
    }
    @Bean public ErrorDecoder carHoldErrorDecoder() { return (methodKey, response) -> {
        int status = response.status(); String code = status == 409 ? "HOLD_CONFLICT" : "BUSINESS_RULE_VIOLATION"; String message = "Car service rejected the request";
        try { String body = response.body() == null ? "" : new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8); JsonNode n = MAPPER.readTree(body); if (n.hasNonNull("error")) code = n.get("error").asString(); if (n.hasNonNull("message")) message = n.get("message").asString(); } catch (Exception ignored) { }
        if (status == 409 || status == 422) return new CarRejectedException(status, code, message);
        return new RuntimeException("Car service returned HTTP " + status);
    }; }
}
