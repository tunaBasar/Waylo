package com.waylo.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> SPOOFED_HEADERS = List.of(
            "X-User-Id", "X-User-Email", "X-User-Role", "X-Internal-Token"
    );

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String rawPath = request.getURI().getRawPath();
        String decodedPath = request.getURI().getPath();
        String method = request.getMethod().name();

        // Strip spoofed identity headers from all requests
        ServerHttpRequest mutatedRequest = request.mutate().headers(headers -> {
            SPOOFED_HEADERS.forEach(headers::remove);
        }).build();

        // Reject path traversal attacks
        if (hasPathTraversal(rawPath, decodedPath)) {
            return badRequestResponse(exchange);
        }

        // Public paths: no token required
        if (isPublicRequest(method, decodedPath)) {
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // Require Authorization header
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorizedResponse(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // BUG 1 FIX: Verify synchronously, no Mono.fromCallable, no onErrorResume
        Optional<Claims> claims = jwtVerifier.verifyAccessToken(token);
        if (claims.isEmpty()) {
            return unauthorizedResponse(exchange);
        }

        Claims c = claims.get();
        // Add verified identity headers
        ServerHttpRequest verifiedRequest = mutatedRequest.mutate()
                .header("X-User-Id", c.getSubject())
                .header("X-User-Email", c.get("email", String.class))
                .header("X-User-Role", c.get("role", String.class))
                .build();
        return chain.filter(exchange.mutate().request(verifiedRequest).build());
    }

    private boolean hasPathTraversal(String rawPath, String decodedPath) {
        String checkLower = null;
        if (rawPath != null) {
            checkLower = rawPath.toLowerCase();
        }
        String decodedLower = null;
        if (decodedPath != null) {
            decodedLower = decodedPath.toLowerCase();
        }

        String[] traversalPatterns = {"..", "//", ";", "%2e", "%2f", "%5c", "\\"};
        for (String pattern : traversalPatterns) {
            if (checkLower != null && checkLower.contains(pattern)) {
                return true;
            }
            if (decodedLower != null && decodedLower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicRequest(String method, String path) {
        // OPTIONS requests are always public
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // Auth endpoints
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        // Actuator health
        if ("/actuator/health".equals(path)) {
            return true;
        }
        // Public hotel catalog browse (GET only, exact segment match)
        if ("GET".equalsIgnoreCase(method) &&
                (path.equals("/api/hotels") || path.startsWith("/api/hotels/"))) {
            return true;
        }
        return false;
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        org.springframework.http.server.reactive.ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of(
                    "status", 401,
                    "error", "UNAUTHORIZED",
                    "message", "Missing or invalid access token"
            ));
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(bytes))
            );
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    private Mono<Void> badRequestResponse(ServerWebExchange exchange) {
        org.springframework.http.server.reactive.ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of(
                    "status", 400,
                    "error", "BAD_REQUEST",
                    "message", "Invalid request path"
            ));
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(bytes))
            );
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
