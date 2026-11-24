package com.waylo.paymentservice.client;

import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.exception.OrderServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFeignConfigTest {

    @Test
    void decode_409OrderExpired_isConflict_otherwise409IsUnavailable() {
        assertThat(OrderFeignConfig.decode(409, "{\"error\":\"ORDER_EXPIRED\",\"message\":\"x\"}"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_EXPIRED"));
        assertThat(OrderFeignConfig.decode(409, "{\"error\":\"CONFLICT\"}")).isInstanceOf(OrderServiceUnavailableException.class);
        assertThat(OrderFeignConfig.decode(409, "")).isInstanceOf(OrderServiceUnavailableException.class);
    }
}
