package com.cnhsk.shared.security;

import java.io.IOException;
import java.util.Map;

import com.cnhsk.shared.exception.ErrorCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Applies an existing authentication filter and a role gate before CSRF on one route group. */
public final class RouteRoleGuardFilter implements Filter {

    private final RequestMatcher routes;
    private final Filter authenticationFilter;
    private final String authority;
    private final AuthenticationEntryPoint entryPoint;
    private final AccessDeniedHandler deniedHandler;

    public RouteRoleGuardFilter(RequestMatcher routes, Filter authenticationFilter, String role,
            AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler) {
        this.routes = routes;
        this.authenticationFilter = authenticationFilter;
        this.authority = "ROLE_" + role;
        this.entryPoint = entryPoint;
        this.deniedHandler = deniedHandler;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!routes.matches(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        authenticationFilter.doFilter(request, response, (authenticatedRequest, authenticatedResponse) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                entryPoint.commence(httpRequest, (HttpServletResponse) response,
                        new AuthenticationApiException(ErrorCode.UNAUTHENTICATED, "Bạn cần đăng nhập.", Map.of()));
                return;
            }
            if (authentication.getAuthorities().stream().noneMatch(granted -> granted.getAuthority().equals(authority))) {
                deniedHandler.handle(httpRequest, (HttpServletResponse) response,
                        new AccessDeniedApiException(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.", Map.of()));
                return;
            }
            chain.doFilter(authenticatedRequest, authenticatedResponse);
        });
    }
}
