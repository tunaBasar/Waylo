package com.waylo.serviceauth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Verifies X-Service-Token on protected route groups and records
 * the caller in the {@link #CALLER_ATTRIBUTE} request attribute. Required groups (all of /internal) fail closed.
 */
public class ServiceAuthFilter implements Filter {

    /** Request attribute holding the authenticated caller service name. */
    public static final String CALLER_ATTRIBUTE = "waylo.serviceAuth.caller";

    private static final Logger log = LoggerFactory.getLogger(ServiceAuthFilter.class);

    private final ServiceAuthenticator authenticator;

    public ServiceAuthFilter(ServiceAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /** True when this request carried an accepted service credential for its route group. */
    public static boolean isServiceCall(HttpServletRequest request) {
        return request.getAttribute(CALLER_ATTRIBUTE) != null;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        request.removeAttribute(CALLER_ATTRIBUTE);
        ServiceAuthenticator.Route route = authenticator.routeFor(request.getRequestURI());
        if (route == null) {
            chain.doFilter(req, res);
            return;
        }
        String token = request.getHeader(ServiceTokens.HEADER);
        if (!route.required() && token == null) {
            chain.doFilter(req, res);
            return;
        }
        ServiceAuthenticator.Decision d = authenticator.decide(route, token);
        if (d.allowed()) {
            request.setAttribute(CALLER_ATTRIBUTE, d.caller());
            chain.doFilter(req, res);
            return;
        }
        log.warn("Rejected service call {} {}: {}", request.getMethod(), request.getRequestURI(), d.reason());
        if (!route.required()) {
            chain.doFilter(req, res); // not a service call after all; the service's own access rules apply
            return;
        }
        HttpServletResponse response = (HttpServletResponse) res;
        response.setStatus(d.status());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String error = d.status() == 403 ? "FORBIDDEN" : "UNAUTHORIZED";
        response.getOutputStream().write(("{\"success\":false,\"status\":" + d.status() + ",\"error\":\"" + error
                + "\",\"message\":\"Unauthorized internal request\"}").getBytes(StandardCharsets.UTF_8));
    }
}
