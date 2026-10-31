package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.PaymentUnavailableException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

public class PaymentFeignConfig {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Bean
    public RequestInterceptor paymentInternalTokenInterceptor(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        return template -> template.header("X-Internal-Token", token);
    }

    @Bean
    public ErrorDecoder paymentErrorDecoder() {
        return (methodKey, response) -> decode(response.status(), readBody(response));
    }

    /** 422 is a business answer (code passed through). 404, 401 (bad token) and 5xx are treated as payment unavailable. */
    static RuntimeException decode(int status, String body) {
        if (status == 422) {
            String code = "PAYMENT_REJECTED";
            String message = "Payment service rejected the request";
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("error")) code = node.get("error").asString();
                if (node.hasNonNull("message")) message = node.get("message").asString();
            } catch (RuntimeException ignored) {
                // keep defaults
            }
            return new BusinessRuleException(code, message);
        }
        return new PaymentUnavailableException("Payment service returned HTTP " + status);
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
