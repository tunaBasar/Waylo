package com.waylo.hotelservice.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalAuthFeignConfigTest {

    private static final String VALID_TOKEN = "a".repeat(32);

    @Test
    void interceptor_addsTokenHeader() {
        InternalAuthFeignConfig config = new InternalAuthFeignConfig();
        // Use reflection or construct directly to test
        // We'll test by creating a config and checking the bean behavior
        feign.RequestInterceptor interceptor = config.internalTokenInterceptor(VALID_TOKEN);
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/access");

        interceptor.apply(template);

        assertThat(template.headers()).containsEntry("X-Internal-Token", java.util.List.of(VALID_TOKEN));
    }

    @Test
    void shortToken_throwsIllegalStateException() {
        InternalAuthFeignConfig config = new InternalAuthFeignConfig();

        assertThatThrownBy(() -> config.internalTokenInterceptor("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankToken_throwsIllegalStateException() {
        InternalAuthFeignConfig config = new InternalAuthFeignConfig();

        assertThatThrownBy(() -> config.internalTokenInterceptor("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullToken_throwsIllegalStateException() {
        InternalAuthFeignConfig config = new InternalAuthFeignConfig();

        assertThatThrownBy(() -> config.internalTokenInterceptor((String) null))
                .isInstanceOf(IllegalStateException.class);
    }
}
