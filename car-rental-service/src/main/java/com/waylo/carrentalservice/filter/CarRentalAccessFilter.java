package com.waylo.carrentalservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class CarRentalAccessFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    public CarRentalAccessFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean vehicles = matches(path, "/api/vehicles");
        boolean rentals = matches(path, "/api/rentals");
        if (!vehicles && !rentals) { chain.doFilter(request, response); return; }
        if (vehicles && (request.getMethod().equals("GET") || request.getMethod().equals("HEAD")
                || request.getMethod().equals("OPTIONS"))) { chain.doFilter(request, response); return; }

        String userId = request.getHeader("X-User-Id");
        try { UUID.fromString(userId == null ? "" : userId); }
        catch (IllegalArgumentException ex) {
            send(response, 401, "UNAUTHORIZED", "Missing or invalid X-User-Id header", path); return;
        }
        if (vehicles && !"ADMIN".equals(request.getHeader("X-User-Role"))) {
            send(response, 403, "FORBIDDEN", "Admin role required for vehicle write operations", path); return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String path, String root) { return path.equals(root) || path.startsWith(root + "/"); }

    private void send(HttpServletResponse response, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "timestamp", Instant.now().toString(), "status", status, "error", error, "message", message, "path", path)));
    }
}
