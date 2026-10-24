package com.waylo.apigateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window (60s) per-IP rate limit for sensitive auth endpoints, backed by Redis.
 * Fails open if Redis is unavailable so authentication keeps working.
 */
@Component
@org.springframework.boot.context.properties.EnableConfigurationProperties(RateLimitProperties.class)
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final int WINDOW_SECONDS = 60;
    private static final long WARN_INTERVAL_MS = 60_000;

    /** Returns 0 when allowed, otherwise the seconds until the window resets. */
    static final RedisScript<Long> SCRIPT = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end "
                    + "if c > tonumber(ARGV[1]) then "
                    + "local t = redis.call('TTL', KEYS[1]) "
                    + "if t < 1 then t = tonumber(ARGV[2]) end "
                    + "return t end "
                    + "return 0",
            Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties props;
    private final AtomicLong lastWarn = new AtomicLong(0);

    public AuthRateLimitFilter(ReactiveStringRedisTemplate redis, ObjectMapper objectMapper,
                               RateLimitProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        int limit = limitFor(path);
        if (limit <= 0) {
            return chain.filter(exchange);
        }
        String key = "waylo:rl:" + path + ":" + clientIp(exchange);
        return redis.execute(SCRIPT, List.of(key), List.of(String.valueOf(limit), String.valueOf(WINDOW_SECONDS)))
                .next()
                .defaultIfEmpty(0L)
                .onErrorResume(e -> {
                    warnOncePerMinute(e);
                    return Mono.just(0L);
                })
                .flatMap(retryAfter -> retryAfter > 0
                        ? tooManyRequests(exchange, retryAfter)
                        : chain.filter(exchange));
    }

    private int limitFor(String path) {
        return switch (path) {
            case "/api/auth/login" -> props.getLoginPerMinute();
            case "/api/auth/register" -> props.getRegisterPerMinute();
            case "/api/auth/forgot-password" -> props.getForgotPasswordPerMinute();
            case "/api/auth/resend-verification" -> props.getResendVerificationPerMinute();
            case "/api/auth/refresh" -> props.getRefreshPerMinute();
            default -> 0;
        };
    }

    private String clientIp(ServerWebExchange exchange) {
        if (props.isTrustForwardedFor()) {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }

    private void warnOncePerMinute(Throwable e) {
        long now = System.currentTimeMillis();
        long prev = lastWarn.get();
        if (now - prev >= WARN_INTERVAL_MS && lastWarn.compareAndSet(prev, now)) {
            log.warn("Rate limiting unavailable (Redis error), failing open: {}", e.toString());
        }
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, long retryAfter) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(retryAfter));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of(
                    "status", 429,
                    "error", "TOO_MANY_REQUESTS",
                    "message", "Çok fazla istek. Lütfen " + retryAfter + " saniye sonra tekrar deneyin.",
                    "path", exchange.getRequest().getURI().getPath()));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
