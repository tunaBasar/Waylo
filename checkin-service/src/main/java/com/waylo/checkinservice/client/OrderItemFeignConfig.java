package com.waylo.checkinservice.client;

import com.waylo.checkinservice.exception.CheckinException;
import com.waylo.checkinservice.exception.ResourceNotFoundException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class OrderItemFeignConfig {

    @Bean
    public ErrorDecoder orderItemErrorDecoder() {
        return (methodKey, response) -> response.status() == 404
                ? new ResourceNotFoundException("OrderItem", "id", "unknown")
                : new CheckinException(503, "SERVICE_UNAVAILABLE", "Order service returned HTTP " + response.status());
    }
}
