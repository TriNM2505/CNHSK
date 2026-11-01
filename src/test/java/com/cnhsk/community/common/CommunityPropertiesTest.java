package com.cnhsk.community.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityPropertiesTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T05:00:00Z"), ZoneOffset.UTC);
    private final AuthApi authApi = mock(AuthApi.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(CommunityProperties.class, CommunityLimits.class, CommunityAuthors.class)
            .withBean(Clock.class, () -> clock)
            .withBean(AuthApi.class, () -> authApi);

    @Test
    void FR008_FR019_FR024_FR035_defaultsBindAndHelpersUseInjectedDependencies() {
        when(authApi.displayNames(Set.of(11L))).thenReturn(Map.of(11L, "Minh"));

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CommunityProperties.class)
                    .hasSingleBean(CommunityLimits.class).hasSingleBean(CommunityAuthors.class)
                    .hasSingleBean(Clock.class);
            CommunityProperties properties = context.getBean(CommunityProperties.class);
            assertThat(properties.getPostsPerDay()).isEqualTo(5);
            assertThat(properties.getCommentsPerHour()).isEqualTo(30);
            assertThat(properties.getReportsPerDay()).isEqualTo(20);
            assertThat(properties.getCommentEditWindow()).isEqualTo(Duration.ofMinutes(15));
            assertThat(context.getBean(CommunityLimits.class).snapshot().now()).isEqualTo(clock.instant());
            assertThat(context.getBean(CommunityAuthors.class).resolve(Set.of(11L)))
                    .containsEntry(11L, new UserRef(11L, "Minh"));
        });
    }

    @Test
    void FR008_FR019_FR024_FR035_positiveOverridesMayExceedDefaults() {
        contextRunner.withPropertyValues(
                "cnhsk.community.posts-per-day=8",
                "cnhsk.community.comments-per-hour=45",
                "cnhsk.community.reports-per-day=25",
                "cnhsk.community.comment-edit-window=20m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CommunityProperties properties = context.getBean(CommunityProperties.class);
                    assertThat(properties.getPostsPerDay()).isEqualTo(8);
                    assertThat(properties.getCommentsPerHour()).isEqualTo(45);
                    assertThat(properties.getReportsPerDay()).isEqualTo(25);
                    assertThat(properties.getCommentEditWindow()).isEqualTo(Duration.ofMinutes(20));
                    CommunityLimits limits = context.getBean(CommunityLimits.class);
                    CommunityLimits.Snapshot snapshot = limits.snapshot();
                    limits.assertCanCreatePost(7, snapshot);
                    limits.assertCanComment(44, snapshot.now().minusSeconds(10), snapshot);
                    limits.assertCanReport(24, snapshot);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "posts-per-day=0", "posts-per-day=-1",
            "comments-per-hour=0", "comments-per-hour=-1",
            "reports-per-day=0", "reports-per-day=-1",
            "comment-edit-window=0s", "comment-edit-window=-1s"
    })
    void FR008_FR019_FR024_FR035_invalidConfigurationPreventsStartup(String setting) {
        contextRunner.withPropertyValues("cnhsk.community." + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void FR024_nullZeroAndNegativeEditWindowsFailBeanValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            CommunityProperties properties = new CommunityProperties();
            assertThat(factory.getValidator().validate(properties)).isEmpty();
            for (Duration invalid : new Duration[]{null, Duration.ZERO, Duration.ofNanos(-1)}) {
                properties.setCommentEditWindow(invalid);
                assertThat(factory.getValidator().validate(properties)).isNotEmpty();
            }
            properties.setCommentEditWindow(Duration.ofNanos(1));
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }
    }
}
