package com.waylo.hotelservice.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Starts every HTTP request in the least privileged DB mode it can have before any access check:
 * /internal (authenticated by the internal-auth filter) is SERVICE, gateway ADMIN role is ADMIN,
 * everything else PUBLIC. HotelAccessGuard upgrades PUBLIC to PARTNER only after a successful check.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.equals("/internal") || uri.startsWith("/internal/")) {
            TenantContext.set(TenantContext.Mode.SERVICE);
        } else if ("ADMIN".equals(request.getHeader("X-User-Role"))) {
            TenantContext.set(TenantContext.Mode.ADMIN);
        } else {
            TenantContext.set(TenantContext.Mode.PUBLIC);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
