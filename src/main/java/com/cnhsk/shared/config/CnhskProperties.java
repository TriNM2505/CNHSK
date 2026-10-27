package com.cnhsk.shared.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/** Typed application settings shared by the application modules. */
@ConfigurationProperties(prefix = "cnhsk")
public class CnhskProperties implements EnvironmentAware, InitializingBean {

    private final Jwt jwt = new Jwt();
    private final Cookie cookie = new Cookie();
    private final Cors cors = new Cors();
    private final App app = new App();
    private Environment environment;

    public Jwt getJwt() {
        return jwt;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Cors getCors() {
        return cors;
    }

    public App getApp() {
        return app;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        applyEnvironmentVariables();
        String secret = jwt.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required to start the application.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes for HS256.");
        }
        if (cors.getAllowedOrigins().isEmpty() || cors.getAllowedOrigins().stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException("cnhsk.cors.allowed-origins must contain explicit origins.");
        }
        if (cors.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed.");
        }
        if (app.getWebBaseUrl() == null || !app.getWebBaseUrl().isAbsolute()) {
            throw new IllegalStateException("APP_WEB_BASE_URL must be an absolute URL.");
        }
    }

    private void applyEnvironmentVariables() {
        if (environment == null) {
            return;
        }
        String secret = environment.getProperty("JWT_SECRET");
        if (secret != null) {
            jwt.setSecret(secret);
        }
        String cookieDomain = environment.getProperty("COOKIE_DOMAIN");
        if (cookieDomain != null) {
            cookie.setDomain(cookieDomain);
        }
        String cookieSecure = environment.getProperty("COOKIE_SECURE");
        if (cookieSecure != null) {
            cookie.setSecure(Boolean.parseBoolean(cookieSecure));
        }
        String webBaseUrl = environment.getProperty("APP_WEB_BASE_URL");
        if (webBaseUrl != null) {
            app.setWebBaseUrl(java.net.URI.create(webBaseUrl));
        }
    }

    public static class Jwt {
        private String secret;
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(30);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    public static class Cookie {
        private String domain = "localhost";
        private boolean secure;
        private String sameSite = "Lax";
        private Duration accessTokenMaxAge = Duration.ofMinutes(15);
        private Duration refreshTokenMaxAge = Duration.ofDays(30);

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public Duration getAccessTokenMaxAge() {
            return accessTokenMaxAge;
        }

        public void setAccessTokenMaxAge(Duration accessTokenMaxAge) {
            this.accessTokenMaxAge = accessTokenMaxAge;
        }

        public Duration getRefreshTokenMaxAge() {
            return refreshTokenMaxAge;
        }

        public void setRefreshTokenMaxAge(Duration refreshTokenMaxAge) {
            this.refreshTokenMaxAge = refreshTokenMaxAge;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:5174");
        private boolean allowCredentials = true;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class App {
        private java.net.URI webBaseUrl = java.net.URI.create("http://localhost:5173");

        public java.net.URI getWebBaseUrl() {
            return webBaseUrl;
        }

        public void setWebBaseUrl(java.net.URI webBaseUrl) {
            this.webBaseUrl = webBaseUrl;
        }
    }
}
