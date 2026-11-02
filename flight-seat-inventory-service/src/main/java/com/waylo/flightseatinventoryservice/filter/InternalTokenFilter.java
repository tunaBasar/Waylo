package com.waylo.flightseatinventoryservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** /internal/** requires X-Internal-Token. Fails closed when no token is configured. */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String internalToken;

    public InternalTokenFilter(@Value("${waylo.internal.token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            String provided = request.getHeader("X-Internal-Token");
            if (internalToken == null || internalToken.isBlank() || provided == null
                    || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                    internalToken.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                String body = "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Unauthorized internal request\","
                        + "\"timestamp\":\"" + Instant.now() + "\"}";
                response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
