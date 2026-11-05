package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MasteryConfig.class);

    @Test
    void defaultsBindAndConvertToPlainSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MasteryConfig.class);
            MasteryConfig.Settings settings = context.getBean(MasteryConfig.class).settings();

            assertThat(settings).isEqualTo(new MasteryConfig.Settings(8, 3, 3, 2, new BigDecimal("1.0"), 30,
                    new BigDecimal("60"), new BigDecimal("80"), new BigDecimal("3"), 3, 7, 14));
            assertThat(context.getBean(MasteryConfig.class).evidenceSettings()).isEqualTo(
                    new MasteryConfig.EvidenceSettings(7,
                            Map.of("EXAM", new BigDecimal("1.0"), "QUIZ", new BigDecimal("0.8"),
                                    "GRADED", new BigDecimal("0.6"), "PRACTICE", new BigDecimal("0.5")),
                            Map.of("OPEN", new BigDecimal("1.0"), "MCQ4", new BigDecimal("0.75"),
                                    "MCQ3", new BigDecimal("0.67"), "BINARY", new BigDecimal("0.5")),
                            Map.of("PRIMARY", new BigDecimal("1.0"), "SECONDARY", new BigDecimal("0.5"),
                                    "ERROR", new BigDecimal("1.0"))));
        });
    }

    @Test
    void missingRequiredKeyPreventsStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(MasteryConfig.class)
                .withPropertyValues(validProperties().replace("cnhsk.mastery.window=8,", "").split(","))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidRangeAndCrossFieldRelationshipsPreventStartup() {
        contextRunner.withPropertyValues("cnhsk.mastery.max-practice-in-window=9")
                .run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues("cnhsk.mastery.hysteresis=60")
                .run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues("cnhsk.mastery.due-days.review=0")
                .run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues("cnhsk.mastery.review-below=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void evidenceKeysAreRequiredAndExactAtStartup() {
        for (String key : new String[] {"seen-item-days", "context-weights.EXAM", "form-weights.MCQ4",
                "role-weights.ERROR"}) {
            String[] properties = Arrays.stream(validProperties().split(","))
                    .filter(value -> !value.startsWith("cnhsk.mastery." + key + "="))
                    .toArray(String[]::new);
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                            ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
                    .withUserConfiguration(MasteryConfig.class).withPropertyValues(properties)
                    .run(context -> assertThat(context).hasFailed());
        }
        contextRunner.withPropertyValues("cnhsk.mastery.context-weights.OTHER=0.5")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("cnhsk.mastery.form-weights.OTHER=0.5")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("cnhsk.mastery.role-weights.OTHER=0.5")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void evidenceWeightsAndWindowRejectInvalidValuesAtStartup() {
        for (String property : new String[] {"seen-item-days=0", "context-weights.EXAM=0",
                "context-weights.EXAM=1.1", "form-weights.MCQ4=0", "form-weights.MCQ4=1.1",
                "role-weights.PRIMARY=0", "role-weights.PRIMARY=1.1"}) {
            contextRunner.withPropertyValues("cnhsk.mastery." + property)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    private String validProperties() {
        return "cnhsk.mastery.window=8,cnhsk.mastery.max-practice-in-window=3,"
                + "cnhsk.mastery.min-evidence=3,cnhsk.mastery.min-distinct-days=2,"
                + "cnhsk.mastery.min-confidence=1.0,cnhsk.mastery.half-life-days=30,"
                + "cnhsk.mastery.review-below=60,cnhsk.mastery.extend-from=80,"
                + "cnhsk.mastery.hysteresis=3,cnhsk.mastery.due-days.review=3,"
                + "cnhsk.mastery.due-days.consolidate=7,cnhsk.mastery.due-days.extend=14,"
                + "cnhsk.mastery.seen-item-days=7,cnhsk.mastery.context-weights.EXAM=1.0,"
                + "cnhsk.mastery.context-weights.QUIZ=0.8,cnhsk.mastery.context-weights.GRADED=0.6,"
                + "cnhsk.mastery.context-weights.PRACTICE=0.5,cnhsk.mastery.form-weights.OPEN=1.0,"
                + "cnhsk.mastery.form-weights.MCQ4=0.75,cnhsk.mastery.form-weights.MCQ3=0.67,"
                + "cnhsk.mastery.form-weights.BINARY=0.5,cnhsk.mastery.role-weights.PRIMARY=1.0,"
                + "cnhsk.mastery.role-weights.SECONDARY=0.5,cnhsk.mastery.role-weights.ERROR=1.0";
    }
}
