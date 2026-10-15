package com.waylo.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);
    private static final String HEADER_NAME = "X-Internal-Token";
    private static final String ERROR_BODY = "{\"allowed\":false,\"error\":\"UNAUTHORIZED_SERVICE\"}";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final String expectedToken;

    public InternalTokenFilter(@Value("${waylo.internal.token}") String token) {
        if (token == null || token.isBlank() || token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("waylo.internal.token must be non-blank and at least 32 characters");
        }
        this.expectedToken = token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);

        if (provided == null || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Unauthorized /internal request from {} with missing or invalid token",
                    request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(ERROR_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
