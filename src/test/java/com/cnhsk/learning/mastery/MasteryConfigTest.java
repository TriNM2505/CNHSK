package com.cnhsk.learning.mastery;

import java.math.BigDecimal;

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

    private String validProperties() {
        return "cnhsk.mastery.window=8,cnhsk.mastery.max-practice-in-window=3,"
                + "cnhsk.mastery.min-evidence=3,cnhsk.mastery.min-distinct-days=2,"
                + "cnhsk.mastery.min-confidence=1.0,cnhsk.mastery.half-life-days=30,"
                + "cnhsk.mastery.review-below=60,cnhsk.mastery.extend-from=80,"
                + "cnhsk.mastery.hysteresis=3,cnhsk.mastery.due-days.review=3,"
                + "cnhsk.mastery.due-days.consolidate=7,cnhsk.mastery.due-days.extend=14";
    }
}
