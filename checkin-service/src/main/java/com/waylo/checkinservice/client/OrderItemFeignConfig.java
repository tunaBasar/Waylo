package com.waylo.checkinservice.client;

import com.waylo.checkinservice.exception.CheckinException;
import com.waylo.checkinservice.exception.ResourceNotFoundException;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class OrderItemFeignConfig {

    @Bean
    public RequestInterceptor orderItemInternalTokenInterceptor(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        return template -> template.header("X-Internal-Token", token);
    }

    @Bean
    public ErrorDecoder orderItemErrorDecoder() {
        return (methodKey, response) -> response.status() == 404
                ? new ResourceNotFoundException("OrderItem", "id", "unknown")
                : new CheckinException(503, "SERVICE_UNAVAILABLE", "Order service returned HTTP " + response.status());
    }
}
