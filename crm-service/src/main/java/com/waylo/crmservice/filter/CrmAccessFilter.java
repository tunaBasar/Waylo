package com.waylo.crmservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Edge access rules for customer PII (the gateway verifies the JWT and sets X-User-Id / X-User-Role).
 * ADMIN: everything. Any other authenticated user: only GET /api/customers/me. Service-to-service
 * (notification-service e-mail lookup): GET with a valid X-Internal-Token. Everything else: 401/403.
 */
@Component
public class CrmAccessFilter extends OncePerRequestFilter {

    private final byte[] internalToken;

    public CrmAccessFilter(@Value("${waylo.internal.token:}") String internalToken) {
        this.internalToken = internalToken == null ? new byte[0] : internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !(p.startsWith("/api/customers") || p.startsWith("/api/loyalty"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");
        if (token != null && internalToken.length > 0 && "GET".equals(request.getMethod())
                && MessageDigest.isEqual(internalToken, token.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            deny(response, 401, "Authentication required");
            return;
        }
        if ("ADMIN".equals(request.getHeader("X-User-Role"))) {
            chain.doFilter(request, response);
            return;
        }
        if ("GET".equals(request.getMethod()) && "/api/customers/me".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        deny(response, 403, "Admin role required");
    }

    private void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + message + "\"}");
    }
}
