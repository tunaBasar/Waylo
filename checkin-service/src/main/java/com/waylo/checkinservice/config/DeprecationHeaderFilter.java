package com.waylo.checkinservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Legacy PackageBooking based check-in endpoints answer with Deprecation: true. */
@Component
public class DeprecationHeaderFilter extends OncePerRequestFilter {

    static boolean isLegacy(String uri) {
        if (!uri.startsWith("/api/checkins")) return false;
        return !(uri.equals("/api/checkins/me") || uri.startsWith("/api/checkins/order-items/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isLegacy(request.getRequestURI())) response.setHeader("Deprecation", "true");
        chain.doFilter(request, response);
    }
}
