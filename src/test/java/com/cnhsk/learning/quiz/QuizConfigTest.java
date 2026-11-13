package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QuizConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class)).withUserConfiguration(QuizConfig.class);

    @Test
    void defaultsBindWithoutExternalSettingsAndApplicationYamlAgrees() {
        runner.run(context -> assertDefaults(context.getBean(QuizConfig.class)));
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertDefaults(context.getBean(QuizConfig.class));
                });
    }

    @Test
    void overridesAndInclusiveBoundsBind() {
        runner.withPropertyValues("cnhsk.quiz.question-count=12", "cnhsk.quiz.min-seconds-per-question=0",
                "cnhsk.quiz.session-minutes=45").run(context -> {
                    assertThat(context).hasNotFailed();
                    QuizConfig config = context.getBean(QuizConfig.class);
                    assertThat(config.getQuestionCount()).isEqualTo(12);
                    assertThat(config.getMinSecondsPerQuestion()).isZero();
                    assertThat(config.getSessionMinutes()).isEqualTo(45);
                });
        for (int count : new int[] {5, 30}) {
            runner.withPropertyValues("cnhsk.quiz.question-count=" + count,
                    "cnhsk.quiz.min-seconds-per-question=10", "cnhsk.quiz.session-minutes=1")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @ParameterizedTest
    @CsvSource({"question-count,4", "question-count,31", "question-count,abc",
            "question-count,2147483648", "min-seconds-per-question,-1", "min-seconds-per-question,11",
            "session-minutes,0", "session-minutes,-1"})
    void invalidConfigurationPreventsStartup(String field, String value) {
        runner.withPropertyValues("cnhsk.quiz." + field + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    private static void assertDefaults(QuizConfig config) {
        assertThat(config.getQuestionCount()).isEqualTo(10);
        assertThat(config.getMinSecondsPerQuestion()).isEqualTo(2);
        assertThat(config.getSessionMinutes()).isEqualTo(30);
    }
}
