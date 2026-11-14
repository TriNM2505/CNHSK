package com.cnhsk.shared.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

class SettingKeyTest {
    @ParameterizedTest
    @CsvSource({
        "billing.free-monthly-limit,Thanh toán,0,1000,10",
        "assistant.daily-limit,Trợ lý & AI,0,10000,200",
        "ai.monthly-token-budget,Trợ lý & AI,0,100000000,2000000",
        "library.guest-lookups-per-hour,Thư viện,1,10000,60",
        "library.authenticated-lookups-per-hour,Thư viện,1,10000,600",
        "library.max-notes,Thư viện,1,10000,1000",
        "library.max-decks,Thư viện,1,500,50",
        "library.max-cards,Thư viện,1,5000,500",
        "library.flashcard-reviews-per-hour,Thư viện,1,10000,500",
        "quiz.question-count,Quiz,5,30,10",
        "quiz.min-seconds-per-question,Quiz,0,60,2",
        "grading.claim-hours,Chấm bài,1,168,48",
        "grading.grading-hours,Chấm bài,1,168,24",
        "grading.teacher-payout-credits,Chấm bài,0,10,0",
        "grading.min-hanzi,Chấm bài,1,1000,50",
        "grading.max-hanzi,Chấm bài,10,2000,500",
        "grading.max-active-claims,Chấm bài,1,20,3",
        "contest.submit-grace-seconds,Cuộc thi,0,60,5"
    })
    void catalogMatchesApprovedBusinessSettings(String name, String group, int min, int max, int defaultValue) {
        SettingKey key = Arrays.stream(SettingKey.values()).filter(k -> k.key().equals(name)).findFirst().orElseThrow();
        assertThat(key.group()).isEqualTo(group);
        assertThat(key.description()).isNotBlank().containsPattern("[^\\x00-\\x7F]");
        assertThat(key.type()).isEqualTo("INT");
        assertThat(key.min()).isEqualTo(min);
        assertThat(key.max()).isEqualTo(max);
        assertThat(key.defaultProperty()).isEqualTo("cnhsk." + name);
        assertThat(yamlDefaults().getProperty(key.defaultProperty(), Integer.class)).isEqualTo(defaultValue);
        assertThat(key.accepts(min)).isTrue();
        assertThat(key.accepts(max)).isTrue();
        assertThat(key.accepts(min - 1)).isFalse();
        assertThat(key.accepts(max + 1)).isFalse();
    }

    @Test
    void catalogIsFixedUniqueAndContainsNoSensitiveKeys() {
        assertThat(SettingKey.values()).hasSize(18).extracting(SettingKey::key).doesNotHaveDuplicates();
        for (SettingKey key : SettingKey.values()) {
            String name = key.key().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("key", "secret", "password", "pepper");
            if (name.contains("token")) {
                assertThat(name).isEqualTo("ai.monthly-token-budget");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("keys")
    void missingDefaultPreventsStartup(SettingKey missing) {
        MockEnvironment environment = new MockEnvironment();
        for (SettingKey key : SettingKey.values()) {
            if (key != missing) {
                environment.setProperty(key.defaultProperty(), yamlDefaults().getProperty(key.defaultProperty()));
            }
        }
        assertThatThrownBy(() -> service(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid or missing default for setting " + missing.key());
    }

    @ParameterizedTest
    @CsvSource({"not-an-integer", "2147483648", "-1", "1001"})
    void invalidDefaultPreventsStartupWithoutLeakingValue(String raw) {
        ConfigurableEnvironment environment = yamlDefaults();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "override", java.util.Map.of("cnhsk.billing.free-monthly-limit", raw)));
        assertThatThrownBy(() -> service(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid or missing default for setting billing.free-monthly-limit").hasNoCause();
    }

    static ConfigurableEnvironment yamlDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
            new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yaml"))
                    .forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return environment;
    }

    private static SettingsService service(org.springframework.core.env.Environment environment) {
        return new SettingsService(mock(JdbcTemplate.class), environment, Clock.systemUTC(),
                mock(PlatformTransactionManager.class));
    }

    static Stream<SettingKey> keys() { return Arrays.stream(SettingKey.values()); }
}
