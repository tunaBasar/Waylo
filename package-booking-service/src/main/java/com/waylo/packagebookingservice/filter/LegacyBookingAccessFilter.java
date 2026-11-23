package com.waylo.packagebookingservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.waylo.serviceauth.ServiceAuthFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

/**
 * Locks the legacy /api/bookings API (it trusts client-supplied data and predates Order/OrderItem).
 * Allowed: service-to-service calls with a verified X-Service-Token (saga; see waylo.service-auth.routes),
 * and read-only (GET/HEAD) calls from a gateway-verified ADMIN for support. Everyone else is rejected.
 * The gateway strips X-Service-Token and X-Internal-Token from external requests.
 */
@Component
public class LegacyBookingAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LegacyBookingAccessFilter.class);
    private static final String BASE = "/api/bookings";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(BASE) || path.startsWith(BASE + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("Deprecation", "true"); // legacy API, use /api/orders (PACKAGE-ORDER-CONTRACT.md)
        // Caller verified (signature, audience, allowlist) by service-auth-common's ServiceAuthFilter.
        if (ServiceAuthFilter.isServiceCall(request)) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod().toUpperCase();
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            send(response, 401, "UNAUTHORIZED", "Authentication required", request.getRequestURI());
            return;
        }
        if ("ADMIN".equals(request.getHeader("X-User-Role")) && READ_METHODS.contains(method)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected legacy booking call {} {}", method, request.getRequestURI());
        send(response, 403, "FORBIDDEN", "The legacy booking API is internal only", request.getRequestURI());
    }

    private void send(HttpServletResponse response, int status, String error, String message,
                      String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String body = "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message
                + "\",\"path\":\"" + path.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
