package com.waylo.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private String[] run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new RequestIdFilter().filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        }).block();
        return new String[]{forwarded.get().getRequest().getHeaders().getFirst("X-Request-Id"),
                exchange.getResponse().getHeaders().getFirst("X-Request-Id")};
    }

    @Test
    void generatesIdWhenAbsentAndForwardsSameValue() {
        String[] ids = run(MockServerHttpRequest.get("/api/hotels").build());
        assertThat(ids[0]).matches("[0-9a-f-]{36}").isEqualTo(ids[1]);
    }

    @Test
    void keepsSafeClientId() {
        String[] ids = run(MockServerHttpRequest.get("/api/hotels").header("X-Request-Id", "client-abc-1").build());
        assertThat(ids).containsExactly("client-abc-1", "client-abc-1");
    }

    @Test
    void replacesUnsafeOrTooLongClientId() {
        for (String bad : new String[]{"a b", "x".repeat(65), "id;drop", "ü"}) {
            String[] ids = run(MockServerHttpRequest.get("/api/hotels").header("X-Request-Id", bad).build());
            assertThat(ids[0]).isNotEqualTo(bad).matches("[0-9a-f-]{36}").isEqualTo(ids[1]);
        }
    }
}
