package com.waylo.apigateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    ) + Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)
    );

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            java.util.Base64.getDecoder().decode(SECRET)
    );

    private static final String ACCESS_TOKEN = buildToken("user-1", "user@example.com", "USER", "access");
    private static final String REFRESH_TOKEN = buildToken("user-1", "user@example.com", "USER", "refresh");

    private static String buildToken(String subject, String email, String role, String typ) {
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("role", role)
                .claim("typ", typ)
                .issuer("waylo-auth")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    @Test
    void quoteEndpointsRequireVerifiedIdentity() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtVerifier(SECRET), new ObjectMapper());
        for (String path : java.util.List.of("/api/quotes", "/api/quotes/" + UUID.randomUUID())) {
            AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
            GatewayFilterChain chain = exchange -> {
                forwarded.set(exchange.getRequest());
                return Mono.empty();
            };
            var anonymous = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                    .header("X-User-Id", "spoofed-user").build());
            filter.filter(anonymous, chain).block();
            assertThat(anonymous.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(forwarded.get()).isNull();

            var authenticated = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                    .header("X-User-Id", "spoofed-user")
                    .header("X-Internal-Token", "spoofed-internal").build());
            filter.filter(authenticated, chain).block();
            assertThat(forwarded.get()).isNotNull();
            assertThat(forwarded.get().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
            assertThat(forwarded.get().getHeaders().getFirst("X-Internal-Token")).isNull();
        }
    }

    @Test
    void noTokenOnBookings_returns401AndChainNotCalled() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validToken_chainCalledWithIdentityHeaders() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void spoofedHeaderWithValidToken_forwardedHeaderIsRealSubject() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .header("X-User-Id", "attacker")
                .header("X-User-Email", "attacker@evil.com")
                .header("X-User-Role", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Spoofed headers removed, verified headers added
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void spoofedHeaderOnPublicPath_headerRemoved() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login")
                .header("X-User-Id", "attacker")
                .header("X-User-Email", "attacker@evil.com")
                .header("X-User-Role", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isNull();
    }

    @Test
    void refreshToken_returns401() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + REFRESH_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void optionsRequest_passesWithoutToken() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.options("/api/bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNotNull();
    }

    @Test
    void getOrder_returnsMinus100() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    // --- BUG 1: downstream error propagation tests ---

    @Test
    void downstreamChainError_propagatesErrorNot401() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return Mono.error(new RuntimeException("service unavailable"));
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        RuntimeException thrown = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> filter.filter(exchange, chain).block(), RuntimeException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("service unavailable");

        // Chain was called (it threw the error)
        assertThat(capturedRequest.get()).isNotNull();
        // Response status should NOT be set to 401
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void downstreamChainError_headerForwardedBeforeError() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return Mono.error(new RuntimeException("service unavailable"));
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> filter.filter(exchange, chain).block(), RuntimeException.class);

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // X-User-* headers were still forwarded before downstream failed
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    // --- BUG 2: path traversal tests ---

    @Test
    void pathTraversal_dotdotSlash_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/../bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_percentEncodedDotDot_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/%2e%2e/bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_semColon_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/..;/bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_doubleSlash_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        // Use %2F%2F so decoded path becomes /api//bookings
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/%2F%2Fbookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_backslashEncoded_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/%5cbookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_rawBackslash_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels\\bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pathTraversal_percentEncodedSlash_returns400() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/%2fbookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void hotelXNotPublic_returns401() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotelsX").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void hotelExactPathPublic_passes() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNotNull();
    }

    @Test
    void hotelSubPathPublic_passes() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/search").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        assertThat(capturedRequest.get()).isNotNull();
    }

    // --- PART B: identity forwarding on public GETs ---

    @Test
    void publicHotelGet_withValidToken_forwardIdentityHeaders() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void publicHotelGet_withValidTokenMissingEmail_forwardIdentityHeaders() {
        // Token without email claim - should still forward subject and role, skip email
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        String tokenWithoutEmail = Jwts.builder()
                .subject("user-2")
                .claim("role", "USER")
                .claim("typ", "access")
                .issuer("waylo-auth")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutEmail)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-2");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void publicHotelSubPath_withValidToken_forwardIdentityHeaders() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels/12345678-1234-1234-1234-123456789abc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void publicHotelGet_noToken_proceedsAnonymous() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // No identity headers present (anonymous)
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isNull();
    }

    @Test
    void publicHotelGet_withInvalidToken_proceedsAnonymous() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Not 401 - public path continues anonymous even with invalid token
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void publicHotelGet_withExpiredToken_proceedsAnonymous() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        // Build an expired token
        String expiredToken = Jwts.builder()
                .subject("user-1")
                .claim("email", "user@example.com")
                .claim("role", "USER")
                .claim("typ", "access")
                .issuer("waylo-auth")
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000)) // expired 1 hour ago
                .signWith(KEY)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Public path continues anonymous with expired token
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void publicHotelGet_withSpoofedHeadersAndValidToken_usesRealSubject() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .header("X-User-Id", "attacker")
                .header("X-User-Email", "attacker@evil.com")
                .header("X-User-Role", "ADMIN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Spoofed headers removed, verified headers from token added
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void nonPublicHotelPost_withValidToken_forwardsIdentity() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Non-GET hotel paths require auth but are handled by the regular auth flow
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.getHeaders().getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void authEndpoint_withValidToken_proceedsWithoutIdentity() {
        AtomicReference<ServerHttpRequest> capturedRequest = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedRequest.set(exchange.getRequest());
            return exchange.getResponse().setComplete();
        };

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        JwtVerifier verifier = new JwtVerifier(SECRET);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, new ObjectMapper());

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = capturedRequest.get();
        assertThat(forwarded).isNotNull();
        // Auth endpoints are public and don't do identity forwarding
        assertThat(forwarded.getHeaders().getFirst("X-User-Id")).isNull();
    }
}
