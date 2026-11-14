package com.waylo.apigateway;

import com.waylo.observability.RequestId;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Edge of the correlation id: keeps a safe client X-Request-Id (max 64 chars [A-Za-z0-9-]) or replaces it with a new
 * UUID, forwards it downstream and returns it in the response. A WebFilter (not a GlobalFilter) so unrouted requests,
 * 401/429 rejections and actuator calls carry it as well. The id lives in an exchange attribute; log statements put it
 * into the MDC only around the call (reactive threads are shared, a request-scoped MDC would leak between requests).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = RequestId.sanitizeOrGenerate(exchange.getRequest().getHeaders().getFirst(RequestId.HEADER));
        exchange.getAttributes().put(ATTRIBUTE, id);
        exchange.getResponse().getHeaders().set(RequestId.HEADER, id);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(RequestId.HEADER, id))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** Runs a log statement with the request id of the exchange in the MDC. */
    public static void withMdc(ServerWebExchange exchange, Runnable logStatement) {
        Object id = exchange.getAttribute(ATTRIBUTE);
        if (id == null) {
            logStatement.run();
            return;
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(RequestId.MDC_KEY, id.toString())) {
            logStatement.run();
        }
    }
}
