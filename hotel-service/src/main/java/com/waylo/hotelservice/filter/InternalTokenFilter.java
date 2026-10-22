package com.waylo.hotelservice.filter;

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

/**
 * Validates the X-Internal-Token header for all /internal/* routes.
 * Fails closed when the configured token is missing.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final String configuredToken;

    public InternalTokenFilter(
            @Value("${waylo.internal.token}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedToken = request.getHeader("X-Internal-Token");

        if (providedToken == null || !providedToken.equals(configuredToken)) {
            log.warn("Missing or invalid internal token for {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"data\":null,\"message\":\"Unauthorized internal request\",\"timestamp\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
