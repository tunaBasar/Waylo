package com.waylo.apigateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitFilterTest {

    /** In-memory emulation of the Lua script: counts per key, returns retry seconds when over limit. */
    private final Map<String, Integer> counters = new HashMap<>();
    private final AtomicInteger chainCalls = new AtomicInteger();
    private final GatewayFilterChain chain = ex -> {
        chainCalls.incrementAndGet();
        return Mono.empty();
    };

    @SuppressWarnings("unchecked")
    private AuthRateLimitFilter filter(RateLimitProperties props, boolean redisDown) {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyList()))
                .thenAnswer(inv -> {
                    if (redisDown) {
                        return Flux.error(new IllegalStateException("redis down"));
                    }
                    String key = ((List<String>) inv.getArgument(1)).get(0);
                    int limit = Integer.parseInt(((List<String>) inv.getArgument(2)).get(0));
                    int c = counters.merge(key, 1, Integer::sum);
                    return Flux.just(c > limit ? 42L : 0L);
                });
        return new AuthRateLimitFilter(redis, new ObjectMapper(), props);
    }

    private MockServerWebExchange post(String path, String ip, String xff) {
        MockServerHttpRequest.BaseBuilder<?> b = MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(ipAddr(ip), 1234));
        if (xff != null) {
            b.header("X-Forwarded-For", xff);
        }
        return MockServerWebExchange.from(b);
    }

    private static InetAddress ipAddr(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpStatus run(AuthRateLimitFilter f, MockServerWebExchange ex) {
        f.filter(ex, chain).block();
        return ex.getResponse().getStatusCode() == null ? HttpStatus.OK : HttpStatus.valueOf(ex.getResponse().getStatusCode().value());
    }

    @Test
    void limitReachedReturns429WithRetryAfterAndBody() throws Exception {
        AuthRateLimitFilter f = filter(new RateLimitProperties(), false);
        for (int i = 0; i < 10; i++) {
            assertThat(run(f, post("/api/auth/login", "1.1.1.1", null))).isEqualTo(HttpStatus.OK);
        }
        MockServerWebExchange ex = post("/api/auth/login", "1.1.1.1", null);
        assertThat(run(f, ex)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("42");
        String body = ex.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":429", "TOO_MANY_REQUESTS", "\"path\":\"/api/auth/login\"");
        assertThat(chainCalls.get()).isEqualTo(10);
    }

    @Test
    void differentIpsAreIndependent() {
        AuthRateLimitFilter f = filter(new RateLimitProperties(), false);
        for (int i = 0; i < 3; i++) {
            run(f, post("/api/auth/forgot-password", "1.1.1.1", null));
        }
        assertThat(run(f, post("/api/auth/forgot-password", "1.1.1.1", null))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(run(f, post("/api/auth/forgot-password", "2.2.2.2", null))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void forwardedForIgnoredByDefault() {
        AuthRateLimitFilter f = filter(new RateLimitProperties(), false);
        for (int i = 0; i < 3; i++) {
            run(f, post("/api/auth/resend-verification", "1.1.1.1", "9.9.9." + i));
        }
        assertThat(run(f, post("/api/auth/resend-verification", "1.1.1.1", "9.9.9.99")))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void forwardedForHonoredWhenTrusted() {
        RateLimitProperties p = new RateLimitProperties();
        p.setTrustForwardedFor(true);
        AuthRateLimitFilter f = filter(p, false);
        for (int i = 0; i < 3; i++) {
            run(f, post("/api/auth/resend-verification", "1.1.1.1", "9.9.9.9, 10.0.0.1"));
        }
        assertThat(run(f, post("/api/auth/resend-verification", "1.1.1.1", "9.9.9.9"))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(run(f, post("/api/auth/resend-verification", "1.1.1.1", "8.8.8.8"))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void otherPathsAndMethodsNeverLimited() {
        AuthRateLimitFilter f = filter(new RateLimitProperties(), false);
        for (int i = 0; i < 100; i++) {
            assertThat(run(f, post("/api/bookings", "1.1.1.1", null))).isEqualTo(HttpStatus.OK);
            MockServerWebExchange get = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login"));
            assertThat(run(f, get)).isEqualTo(HttpStatus.OK);
        }
        assertThat(counters).isEmpty();
    }

    @Test
    void redisErrorFailsOpen() {
        AuthRateLimitFilter f = filter(new RateLimitProperties(), true);
        for (int i = 0; i < 20; i++) {
            assertThat(run(f, post("/api/auth/login", "1.1.1.1", null))).isEqualTo(HttpStatus.OK);
        }
        assertThat(chainCalls.get()).isEqualTo(20);
    }
}
