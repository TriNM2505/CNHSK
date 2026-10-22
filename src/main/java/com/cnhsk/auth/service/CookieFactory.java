package com.cnhsk.auth.service;

import java.time.Duration;
import java.util.Objects;

import com.cnhsk.shared.config.CnhskProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds authentication and CSRF cookies with the attributes required by the auth contract. */
@Component
public class CookieFactory {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/auth";
    private static final String SAME_SITE = "Lax";

    private final CnhskProperties.Cookie properties;

    public CookieFactory(CnhskProperties configuration) {
        this.properties = Objects.requireNonNull(configuration, "configuration must not be null.").getCookie();
        if (properties.getDomain() == null || properties.getDomain().isBlank()) {
            throw new IllegalArgumentException("Cookie domain must not be blank.");
        }
    }

    public ResponseCookie createAccessTokenCookie(String token) {
        return authenticationCookie(ACCESS_COOKIE, requireCookieValue(token), ACCESS_PATH,
                properties.getAccessTokenMaxAge(), properties.isSecure());
    }

    public ResponseCookie createRefreshTokenCookie(String token) {
        return authenticationCookie(REFRESH_COOKIE, requireCookieValue(token), REFRESH_PATH,
                properties.getRefreshTokenMaxAge(), true);
    }

    public ResponseCookie createXsrfTokenCookie(String token) {
        return ResponseCookie.from(XSRF_COOKIE, requireCookieValue(token))
                .domain(properties.getDomain())
                .httpOnly(false)
                .secure(properties.isSecure())
                .sameSite(SAME_SITE)
                .path(ACCESS_PATH)
                .build();
    }

    public ResponseCookie clearAccessTokenCookie() {
        return authenticationCookie(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO, properties.isSecure());
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return authenticationCookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO, true);
    }

    public ResponseCookie clearXsrfTokenCookie() {
        return ResponseCookie.from(XSRF_COOKIE, "")
                .domain(properties.getDomain())
                .httpOnly(false)
                .secure(properties.isSecure())
                .sameSite(SAME_SITE)
                .path(ACCESS_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie authenticationCookie(String name, String token, String path,
            Duration maxAge, boolean secure) {
        return ResponseCookie.from(name, token)
                .domain(properties.getDomain())
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static String requireCookieValue(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Cookie value must be non-blank and safe for a Set-Cookie header.");
        }
        return value;
    }
}
