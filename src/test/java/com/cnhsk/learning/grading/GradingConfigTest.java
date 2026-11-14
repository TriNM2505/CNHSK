package com.cnhsk.learning.grading;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GradingConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(GradingConfig.class);

    @Test
    void section01_applicationYamlRegistersAllEightApprovedValues() {
        runner.withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("grading-test", new ClassPathResource("application.yaml"))
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load configuration."); }
        }).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(GradingConfig.class);
            GradingConfig config = context.getBean(GradingConfig.class);
            assertThat(config.getClaimHours()).isEqualTo(48);
            assertThat(config.getGradingHours()).isEqualTo(24);
            assertThat(config.getTeacherPayoutCredits()).isZero();
            assertThat(config.getMinHanzi()).isEqualTo(50);
            assertThat(config.getMaxHanzi()).isEqualTo(500);
            assertThat(config.getMaxChars()).isEqualTo(3000);
            assertThat(config.getMaxActiveClaims()).isEqualTo(3);
            assertThat(config.getExpiryJobCron()).isEqualTo("0 */5 * * * *");
        });
    }

    @ParameterizedTest
    @MethodSource("keys")
    void section01_everyKeyIsMandatoryIncludingZeroPayout(String missing) {
        Map<String, String> properties = values(); properties.remove(missing);
        configured(properties).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void section01_emptyConfigurationDoesNotStart() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @MethodSource("invalid")
    void section01_invalidValueOrLengthRangeDoesNotStart(String assignment) {
        String[] parts = assignment.split("=", 2);
        Map<String, String> properties = values(); properties.put(parts[0], parts[1]);
        configured(properties).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void section01_nonzeroPayoutAndEqualLengthBoundsAreValid() {
        Map<String, String> properties = values();
        properties.put("teacher-payout-credits", "2");
        properties.put("min-hanzi", "50"); properties.put("max-hanzi", "50"); properties.put("max-chars", "50");
        configured(properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GradingConfig.class).getTeacherPayoutCredits()).isEqualTo(2);
        });
    }

    private ApplicationContextRunner configured(Map<String, String> properties) {
        return runner.withPropertyValues(properties.entrySet().stream()
                .map(entry -> "cnhsk.grading." + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private static Map<String, String> values() {
        return new LinkedHashMap<>(Map.of("claim-hours", "48", "grading-hours", "24", "teacher-payout-credits", "0",
                "min-hanzi", "50", "max-hanzi", "500", "max-chars", "3000", "max-active-claims", "3",
                "expiry-job-cron", "0 */5 * * * *"));
    }

    private static Stream<String> keys() { return values().keySet().stream(); }

    private static Stream<String> invalid() {
        return Stream.of("claim-hours=0", "claim-hours=-1", "grading-hours=0", "teacher-payout-credits=-1",
                "min-hanzi=0", "min-hanzi=501", "max-hanzi=49", "max-hanzi=3001", "max-chars=499", "max-chars=3001",
                "max-active-claims=0", "expiry-job-cron=", "expiry-job-cron=not-a-cron", "claim-hours=not-a-number");
    }
}
