package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.BusRejectedException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;

public class BusHoldFeignConfig {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    @Bean public ErrorDecoder busHoldErrorDecoder() { return (methodKey, response) -> {
        int status = response.status(); String code = status == 409 ? "HOLD_CONFLICT" : "BUSINESS_RULE_VIOLATION"; String message = "Bus service rejected the request";
        try { String body = response.body() == null ? "" : new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8); JsonNode n = MAPPER.readTree(body); if (n.hasNonNull("error")) code = n.get("error").asString(); if (n.hasNonNull("message")) message = n.get("message").asString(); } catch (Exception ignored) { }
        if (status == 409 || status == 422) return new BusRejectedException(status, code, message);
        return new RuntimeException("Bus service returned HTTP " + status);
    }; }
}
