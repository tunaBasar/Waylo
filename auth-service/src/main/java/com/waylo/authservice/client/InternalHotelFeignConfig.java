package com.waylo.authservice.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Not a @Configuration: applied only to HotelCatalogClient, adds the shared service-to-service token. */
public class InternalHotelFeignConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < 32) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        return template -> template.header("X-Internal-Token", token);
    }
}
