package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.HotelRejectedException;
import com.waylo.packagebookingservice.exception.HotelUnavailableException;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelHoldFeignConfigTest {

    private static final String TOKEN = "a".repeat(32);

    @Test
    void interceptor_sendsInternalToken() {
        RequestTemplate template = new RequestTemplate();
        new HotelHoldFeignConfig().internalTokenInterceptor(TOKEN).apply(template);
        assertThat(template.headers()).containsEntry("X-Internal-Token", java.util.List.of(TOKEN));
    }

    @Test
    void shortToken_rejected() {
        assertThatThrownBy(() -> new HotelHoldFeignConfig().internalTokenInterceptor("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decode_passesThroughHotelCode() {
        RuntimeException ex = HotelHoldFeignConfig.decode(422,
                "{\"status\":422,\"error\":\"QUOTE_EXPIRED\",\"message\":\"expired\"}");
        assertThat(ex).isInstanceOf(HotelRejectedException.class);
        assertThat(((HotelRejectedException) ex).getCode()).isEqualTo("QUOTE_EXPIRED");
    }

    @Test
    void decode_404_and_5xx() {
        assertThat(HotelHoldFeignConfig.decode(404, "garbage")).isInstanceOf(HotelRejectedException.class);
        assertThat(HotelHoldFeignConfig.decode(401, "")).isInstanceOf(HotelUnavailableException.class);
        assertThat(HotelHoldFeignConfig.decode(503, "")).isInstanceOf(HotelUnavailableException.class);
    }
}
