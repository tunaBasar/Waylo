package com.waylo.hotelservice.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;

public class InternalAuthFeignConfig {

    @org.springframework.context.annotation.Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        return template -> template.header("X-Internal-Token", token);
    }
}
