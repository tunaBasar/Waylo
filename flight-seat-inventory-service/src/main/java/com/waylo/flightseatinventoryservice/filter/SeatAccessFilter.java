package com.waylo.flightseatinventoryservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Guards /api/seat-inventory/**. Reads are open. POST/DELETE on .../reserve need a valid
 * X-User-Id UUID. All other writes need X-User-Id plus X-User-Role == ADMIN.
 */
@Component
public class SeatAccessFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String BASE = "/api/seat-inventory";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase();

        boolean guarded = path.equals(BASE) || path.startsWith(BASE + "/");
        if (!guarded || SAFE_METHODS.contains(method)) {
            chain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank() || !isUuid(userId.trim())) {
            send(response, 401, "UNAUTHORIZED", "Missing or invalid X-User-Id header", path);
            return;
        }

        boolean reserve = path.endsWith("/reserve") && ("POST".equals(method) || "DELETE".equals(method));
        if (!reserve && !"ADMIN".equals(request.getHeader(HEADER_USER_ROLE))) {
            send(response, 403, "FORBIDDEN", "Admin role required for this operation", path);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void send(HttpServletResponse response, int status, String error, String message,
                      String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = "{\"status\":" + status
                + ",\"error\":\"" + error
                + "\",\"message\":\"" + message
                + "\",\"path\":\"" + path.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
