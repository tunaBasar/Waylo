package com.waylo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Puts the incoming X-Request-Id (or a new one for direct calls) into the MDC for the whole request and echoes it. */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestId.sanitizeOrGenerate(request.getHeader(RequestId.HEADER));
        MDC.put(RequestId.MDC_KEY, id);
        response.setHeader(RequestId.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
