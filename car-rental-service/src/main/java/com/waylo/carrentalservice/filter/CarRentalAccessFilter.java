package com.waylo.carrentalservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.waylo.serviceauth.ServiceAuthFilter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Edge rules: internal paths need a verified X-Service-Token (service-auth-common), partner and customer paths need a valid X-User-Id,
 * admin paths need X-User-Role ADMIN. Catalogue paths (/api/car-branches, /api/car-offers) are public.
 */
@Component
public class CarRentalAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    public CarRentalAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (under(path, "/internal")) {
            // Verified (signature, audience, caller allowlist) by service-auth-common's ServiceAuthFilter; defence in depth.
            if (!ServiceAuthFilter.isServiceCall(request)) {
                send(response, 401, "UNAUTHORIZED", "Unauthorized internal request", path);
                return;
            }
        } else if (under(path, "/api/partner") || under(path, "/api/admin") || under(path, "/api/rentals")) {
            if (!validUser(request.getHeader("X-User-Id"))) {
                send(response, 401, "UNAUTHORIZED", "Missing or invalid X-User-Id header", path);
                return;
            }
            if (under(path, "/api/admin") && !"ADMIN".equals(request.getHeader("X-User-Role"))) {
                send(response, 403, "FORBIDDEN", "Admin role required", path);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean under(String path, String root) { return path.equals(root) || path.startsWith(root + "/"); }

    private static boolean validUser(String value) {
        try {
            UUID.fromString(value == null ? "" : value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void send(HttpServletResponse response, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "timestamp", Instant.now().toString(), "status", status, "error", error, "message", message, "path", path)));
    }
}
