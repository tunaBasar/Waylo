package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.FlightRejectedException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

public class FlightHoldFeignConfig {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Bean
    public ErrorDecoder flightHoldErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            String code = status == 409 ? "HOLD_CONFLICT" : "BUSINESS_RULE_VIOLATION";
            String message = "Flight service rejected the request";
            try {
                String body = response.body() == null ? ""
                        : new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode n = MAPPER.readTree(body);
                if (n.hasNonNull("error")) code = n.get("error").asString();
                if (n.hasNonNull("message")) message = n.get("message").asString();
            } catch (Exception ignored) { }
            if (status == 404 || status == 409 || status == 422 || status == 400) {
                return new FlightRejectedException(status, code, message);
            }
            return new RuntimeException("Flight service returned HTTP " + status);
        };
    }
}
