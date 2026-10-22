package com.cnhsk.shared.security;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves a request identity from a verified cookie or mobile bearer token. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String ACCESS_COOKIE = "access_token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN_MESSAGE = "The access token is invalid.";
    private static final String REVOKED_TOKEN_MESSAGE = "The session has been revoked.";
    private static final String BANNED_ACCOUNT_MESSAGE = "Tài khoản đã bị cấm.";

    private final JwtService jwtService;
    private final SessionInvalidationService invalidationService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public JwtAuthenticationFilter(JwtService jwtService,
            SessionInvalidationService invalidationService,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.invalidationService = invalidationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "/api/auth/login".equals(path) || "/api/auth/refresh".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = jwtService.verifyAccessToken(token);
        } catch (ApiException exception) {
            rejectAuthentication(request, response, exception.getCode(), exception.getMessage(), exception.getExtra());
            return;
        }

        long userId = Long.parseLong(claims.getSubject());
        SessionInvalidationMarker marker = findMarkerOrContinue(userId);
        long issuedAtMs = ((Number) claims.get("iatMs")).longValue();
        if (marker != null && issuedAtMs < marker.invalidBeforeMs()) {
            if (marker.reason() == SessionInvalidationMarker.Reason.BANNED) {
                accessDeniedHandler.handle(request, response,
                        new AccessDeniedApiException(ErrorCode.ACCOUNT_BANNED, BANNED_ACCOUNT_MESSAGE,
                                Map.of("banReason", marker.banReason())));
            } else {
                rejectAuthentication(request, response, ErrorCode.SESSION_REVOKED,
                        REVOKED_TOKEN_MESSAGE, Map.of());
            }
            return;
        }

        setAuthentication(claims, userId);
        chain.doFilter(request, response);
    }

    private SessionInvalidationMarker findMarkerOrContinue(long userId) {
        try {
            return invalidationService.findMarker(userId).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Session invalidation lookup failed for userId={}; allowing request per NFR-A02",
                    userId, exception);
            return null;
        }
    }

    private void rejectAuthentication(HttpServletRequest request, HttpServletResponse response,
            ErrorCode errorCode, String message, Map<String, Object> extra) throws IOException, ServletException {
        String safeMessage = message == null ? INVALID_TOKEN_MESSAGE : message;
        authenticationEntryPoint.commence(request, response,
                new AuthenticationApiException(errorCode, safeMessage, extra));
    }

    private static void setAuthentication(Claims claims, long userId) {
        Collection<?> roleClaims = (Collection<?>) claims.get("roles");
        Set<String> roles = roleClaims.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
        AuthenticatedUser principal = new AuthenticatedUser(userId, roles,
                Boolean.TRUE.equals(claims.get("ev")), claims.getId(),
                UUID.fromString(claims.get("sid", String.class)), claims.getIssuedAt().toInstant());
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
