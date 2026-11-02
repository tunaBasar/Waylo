package com.waylo.pricingservice.filter;

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

/** Guards every /internal/* route with X-Internal-Token. Fails closed when no token configured. */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final byte[] configuredToken;

    public InternalTokenFilter(@Value("${waylo.internal.token}") String configuredToken) {
        this.configuredToken = configuredToken == null ? new byte[0]
                : configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader("X-Internal-Token");
        if (provided == null || configuredToken.length == 0
                || !MessageDigest.isEqual(configuredToken,
                        provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Missing or invalid internal token for {} {}",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"data\":null,\"message\":\"Unauthorized internal request\",\"timestamp\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
