package com.cnhsk.shared.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "request_id";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentRequestId() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    static String resolveRequestId(String suppliedRequestId) {
        if (suppliedRequestId != null && VALID_REQUEST_ID.matcher(suppliedRequestId).matches()) {
            return suppliedRequestId;
        }
        return UUID.randomUUID().toString();
    }
}
