package com.waylo.apigateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Instant start = Instant.now();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        RequestIdFilter.withMdc(exchange, () -> log.info(">>> {} {}", method, path));

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = java.time.Duration.between(start, Instant.now()).toMillis();
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            RequestIdFilter.withMdc(exchange, () -> log.info("<<< {} {} status={} duration={}ms",
                    method, path, status, duration));
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
