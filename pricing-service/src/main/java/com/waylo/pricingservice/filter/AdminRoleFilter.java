package com.waylo.pricingservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Protects /api/admin/* writes: GET/HEAD/OPTIONS always allowed,
 * POST/PUT/DELETE require X-User-Role header = "ADMIN".
 */
@Component
public class AdminRoleFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String[] WRITE_METHODS = {"POST", "PUT", "DELETE", "PATCH"};
    private static final String[] SAFE_METHODS = {"GET", "HEAD", "OPTIONS"};

    private final ObjectMapper objectMapper;

    public AdminRoleFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only protect /api/admin paths
        if (!path.startsWith("/api/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Allow safe methods unconditionally
        for (String safe : SAFE_METHODS) {
            if (safe.equalsIgnoreCase(method)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Check admin role
        String role = request.getHeader(HEADER_USER_ROLE);
        if (!"ADMIN".equals(role)) {
            sendErrorResponse(response, 403, "FORBIDDEN",
                    "Admin role required", path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status,
                                   String error, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "status", status,
                "error", error,
                "message", message,
                "path", path));
        response.getOutputStream().write(body);
    }
}
