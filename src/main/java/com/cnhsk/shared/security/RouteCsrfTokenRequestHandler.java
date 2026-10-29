package com.cnhsk.shared.security;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Prevents multipart form parsing while resolving CSRF on routes that require a header. */
public final class RouteCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final RequestMatcher headerOnlyRoutes;
    private final CsrfTokenRequestAttributeHandler delegate = new CsrfTokenRequestAttributeHandler();

    public RouteCsrfTokenRequestHandler(RequestMatcher headerOnlyRoutes) {
        this.headerOnlyRoutes = headerOnlyRoutes;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        delegate.handle(request, response, token);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        return headerOnlyRoutes.matches(request) ? request.getHeader(token.getHeaderName())
                : delegate.resolveCsrfTokenValue(request, token);
    }
}
