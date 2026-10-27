package com.cnhsk.auth.service;

import java.time.Duration;

import com.cnhsk.shared.config.CnhskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookieFactoryTest {

    private CnhskProperties properties;
    private CookieFactory cookieFactory;

    @BeforeEach
    void setUp() {
        properties = new CnhskProperties();
        properties.getCookie().setDomain("cnhsk.com");
        properties.getCookie().setSecure(true);
        cookieFactory = new CookieFactory(properties);
    }

    @Test
    void FR014_accessCookieUsesRequiredAttributes() {
        ResponseCookie cookie = cookieFactory.createAccessTokenCookie("access.jwt.token");

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isEqualTo("access.jwt.token");
        assertThat(cookie.getDomain()).isEqualTo("cnhsk.com");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void FR014b_refreshCookieUsesRestrictedPathAndThirtyDayLifetime() {
        ResponseCookie cookie = cookieFactory.createRefreshTokenCookie("refresh_token_value");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("refresh_token_value");
        assertThat(cookie.getDomain()).isEqualTo("cnhsk.com");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    void csrfCookieIsReadableAndUsesSharedDomainAndRootPath() {
        ResponseCookie cookie = cookieFactory.createXsrfTokenCookie("csrf-token");

        assertThat(cookie.getName()).isEqualTo("XSRF-TOKEN");
        assertThat(cookie.getValue()).isEqualTo("csrf-token");
        assertThat(cookie.getDomain()).isEqualTo("cnhsk.com");
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void FR034_clearCookiesExpireOnTheSameDomainAndPath() {
        assertExpiredCookie(cookieFactory.clearAccessTokenCookie(), "access_token", "/");
        assertExpiredCookie(cookieFactory.clearRefreshTokenCookie(), "refresh_token", "/api/auth");
        assertExpiredCookie(cookieFactory.clearXsrfTokenCookie(), "XSRF-TOKEN", "/");
    }

    @Test
    void rejectsUnsafeOrMissingCookieValues() {
        assertThatThrownBy(() -> cookieFactory.createAccessTokenCookie(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cookieFactory.createRefreshTokenCookie("token\r\nSet-Cookie: forged=true"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cookieFactory.createXsrfTokenCookie("token; injected=true"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCookieDomain() {
        properties.getCookie().setDomain(" ");

        assertThatThrownBy(() -> new CookieFactory(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cookie domain must not be blank.");
    }

    private void assertExpiredCookie(ResponseCookie cookie, String name, String path) {
        assertThat(cookie.getName()).isEqualTo(name);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getDomain()).isEqualTo("cnhsk.com");
        assertThat(cookie.getPath()).isEqualTo(path);
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isEqualTo(!"XSRF-TOKEN".equals(name));
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
}
