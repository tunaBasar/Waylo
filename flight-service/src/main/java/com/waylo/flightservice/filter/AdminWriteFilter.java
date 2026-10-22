package com.waylo.flightservice.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Blocks non-admin write requests to /api/flights and /api/airports paths.
 * GET/HEAD/OPTIONS are always allowed. Writes require X-User-Id present
 * and X-User-Role exactly "ADMIN".
 */
@Component
public class AdminWriteFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String[] WRITE_PATH_PREFIXES = {"/api/flights", "/api/airports"};
    private static final String[] SAFE_METHODS = {"GET", "HEAD", "OPTIONS"};

    private final ObjectMapper objectMapper;

    public AdminWriteFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Allow safe methods unconditionally
        for (String safeMethod : SAFE_METHODS) {
            if (safeMethod.equalsIgnoreCase(method)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Check if this path matches any protected write prefix
        boolean isProtectedWritePath = false;
        for (String prefix : WRITE_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                isProtectedWritePath = true;
                break;
            }
        }

        if (!isProtectedWritePath) {
            filterChain.doFilter(request, response);
            return;
        }

        // Write path — require identity
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            sendErrorResponse(response, 401, "UNAUTHORIZED",
                    "Missing or empty X-User-Id header", path);
            return;
        }

        // Require admin role
        String userRole = request.getHeader(HEADER_USER_ROLE);
        if (!"ADMIN".equals(userRole)) {
            sendErrorResponse(response, 403, "FORBIDDEN",
                    "Admin role required for write operations", path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response,
                                   int status,
                                   String error,
                                   String message,
                                   String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "status", status,
                "error", error,
                "message", message,
                "path", path
        );

        byte[] bytes = objectMapper.writeValueAsBytes(body);
        response.getOutputStream().write(bytes);
    }
}
