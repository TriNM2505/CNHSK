package com.cnhsk.shared.config;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class CnhskPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SharedConfiguration.class);

    @Test
    void refusesToStartWhenJwtSecretIsMissing() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("test-environment", Map.of("JWT_SECRET", ""))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("JWT_SECRET is required");
                });
    }

    @Test
    void refusesToStartWhenJwtSecretIsShorterThan32Bytes() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("test-environment", Map.of("JWT_SECRET", "short"))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("at least 32 bytes");
                });
    }

    @Test
    void bindsApprovedSettingsAndReadsDocumentedEnvironmentVariableNames() {
        String generatedSecret = UUID.randomUUID().toString() + UUID.randomUUID();
        contextRunner
                .withPropertyValues(
                        "cnhsk.cors.allowed-origins=https://cnhsk.com,https://game.cnhsk.com",
                        "cnhsk.cors.allow-credentials=true",
                        "cnhsk.cookie.same-site=Lax",
                        "cnhsk.jwt.access-token-ttl=15m",
                        "cnhsk.jwt.refresh-token-ttl=30d")
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("test-environment", Map.of(
                                "JWT_SECRET", generatedSecret,
                                "COOKIE_DOMAIN", "cnhsk.com",
                                "COOKIE_SECURE", "true",
                                "APP_WEB_BASE_URL", "https://cnhsk.com"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CnhskProperties properties = context.getBean(CnhskProperties.class);
                    assertThat(properties.getJwt().getSecret()).isEqualTo(generatedSecret);
                    assertThat(properties.getJwt().getAccessTokenTtl()).hasMinutes(15);
                    assertThat(properties.getJwt().getRefreshTokenTtl()).hasDays(30);
                    assertThat(properties.getCookie().getDomain()).isEqualTo("cnhsk.com");
                    assertThat(properties.getCookie().isSecure()).isTrue();
                    assertThat(properties.getCookie().getSameSite()).isEqualTo("Lax");
                    assertThat(properties.getCookie().getAccessTokenMaxAge()).hasMinutes(15);
                    assertThat(properties.getCookie().getRefreshTokenMaxAge()).hasDays(30);
                    assertThat(properties.getCors().getAllowedOrigins())
                            .containsExactly("https://cnhsk.com", "https://game.cnhsk.com");
                    assertThat(properties.getCors().isAllowCredentials()).isTrue();
                    assertThat(properties.getApp().getWebBaseUrl().toString()).isEqualTo("https://cnhsk.com");
                });
    }

    @Test
    void rejectsWildcardCorsOrigin() {
        contextRunner
                .withPropertyValues("cnhsk.jwt.secret=" + UUID.randomUUID() + UUID.randomUUID(),
                        "cnhsk.cors.allowed-origins=*")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context.getStartupFailure()))
                            .contains("Wildcard CORS origins are not allowed");
                });
    }

    private String rootCauseMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
