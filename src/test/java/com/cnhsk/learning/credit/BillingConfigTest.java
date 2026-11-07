package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;
import org.springframework.validation.FieldError;

@ExtendWith(OutputCaptureExtension.class)
class BillingConfigTest {

    private static final String TEST_PEPPER = "test-only-billing-pepper-32-bytes-minimum";
    private static final String VALIDATION_MESSAGE = "cnhsk.billing.card-code-pepper >= 32 byte";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(BillingConfig.class, CardCodes.class)
            .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-11-06T17:30:00Z"), ZoneOffset.UTC))
            .withInitializer(context -> {
                var environment = context.getEnvironment();
                environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                environment.setActiveProfiles("test");
            });

    @Test
    void rejectsMissingEmptyAndShortUtf8Pepper() {
        runner.run(context -> assertThat(context.getStartupFailure() != null).isTrue());
        for (String invalid : List.of("", "x".repeat(31), "\u00e1".repeat(15) + "x")) {
            runner.withPropertyValues("cnhsk.billing.card-code-pepper=" + invalid)
                    .run(context -> assertThat(context.getStartupFailure() != null).isTrue());
        }
    }

    @Test
    void acceptsThirtyTwoUtf8BytesIncludingVietnameseCharacters() {
        for (String valid : List.of("x".repeat(32), "\u00e1".repeat(16))) {
            assertThat(valid.getBytes(StandardCharsets.UTF_8).length).isEqualTo(32);
            runner.withPropertyValues("cnhsk.billing.card-code-pepper=" + valid).run(context -> {
                assertThat(context.getStartupFailure() == null).isTrue();
                assertThat(context.getBeansOfType(BillingConfig.class).size()).isEqualTo(1);
                assertThat(context.getBeansOfType(CardCodes.class).size()).isEqualTo(1);
                BillingConfig config = context.getBean(BillingConfig.class);
                assertThat(config.isCardCodePepperValid()).isTrue();
                assertThat(config.getCardCodePepper().equals(valid)).isTrue();
                assertThat(config.getFreeMonthlyLimit()).isEqualTo(10);
            });
        }
    }

    @Test
    void bindsFreeMonthlyLimitOverrideAndJavaBeanSetters() {
        runner.withPropertyValues("cnhsk.billing.card-code-pepper=" + TEST_PEPPER,
                "cnhsk.billing.free-monthly-limit=23").run(context -> {
                    assertThat(context.getStartupFailure() == null).isTrue();
                    BillingConfig config = context.getBean(BillingConfig.class);
                    assertThat(config.getFreeMonthlyLimit()).isEqualTo(23);
                    config.setFreeMonthlyLimit(17);
                    assertThat(config.getFreeMonthlyLimit()).isEqualTo(17);
                });
        BillingConfig config = new BillingConfig();
        assertThat(config.isCardCodePepperValid()).isFalse();
        assertThat(config.getFreeMonthlyLimit()).isEqualTo(10);
        config.setCardCodePepper(TEST_PEPPER);
        assertThat(config.getCardCodePepper().equals(TEST_PEPPER)).isTrue();
    }

    @Test
    void productionRequiresPepperAndTestProfileOverridesIt() {
        withYaml("application.yaml").run(context -> assertThat(context.getStartupFailure() != null).isTrue());
        withYaml("application-test.yaml", "application.yaml").run(context -> {
            assertThat(context.getStartupFailure() == null).isTrue();
            assertThat(context.getBean(BillingConfig.class).isCardCodePepperValid()).isTrue();
            assertThat(context.getBean(BillingConfig.class).getFreeMonthlyLimit()).isEqualTo(10);
            assertThat(context.getBeansOfType(CardCodes.class).size()).isEqualTo(1);
        });
    }

    @Test
    void startupDiagnosticsAndToStringDoNotExposePepper(CapturedOutput output) {
        String invalidTestPepper = "test-only-invalid-pepper";
        runner.withPropertyValues("cnhsk.billing.card-code-pepper=" + invalidTestPepper).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure != null).isTrue();
            BindValidationException validation = null;
            for (Throwable current = failure; current != null; current = current.getCause()) {
                assertThat(String.valueOf(current.getMessage()).contains(invalidTestPepper)).isFalse();
                if (current instanceof BindValidationException bindValidation) {
                    validation = bindValidation;
                }
            }
            StringWriter stackTrace = new StringWriter();
            failure.printStackTrace(new PrintWriter(stackTrace));
            assertThat(stackTrace.toString().contains(invalidTestPepper)).isFalse();
            Class<?> analyzerClass = ClassUtils.resolveClassName(
                    "org.springframework.boot.diagnostics.analyzer.BindValidationFailureAnalyzer",
                    BillingConfigTest.class.getClassLoader());
            FailureAnalyzer analyzer = (FailureAnalyzer) BeanUtils.instantiateClass(analyzerClass);
            var analysis = analyzer.analyze(failure);
            assertThat(analysis != null).isTrue();
            assertThat(analysis.getDescription().contains(invalidTestPepper)).isFalse();
            assertThat(String.valueOf(analysis.getAction()).contains(invalidTestPepper)).isFalse();
            assertThat(validation != null).isTrue();
            List<FieldError> errors = validation.getValidationErrors().getAllErrors().stream()
                    .filter(FieldError.class::isInstance).map(FieldError.class::cast).toList();
            assertThat(errors.size()).isEqualTo(1);
            assertThat(errors.get(0).getField()).isEqualTo("cardCodePepperValid");
            assertThat(Boolean.FALSE.equals(errors.get(0).getRejectedValue())).isTrue();
            assertThat(errors.get(0).getDefaultMessage()).isEqualTo(VALIDATION_MESSAGE);
        });
        assertThat(output.getAll().contains(invalidTestPepper)).isFalse();
        BillingConfig config = new BillingConfig();
        String redacted = config.toString();
        for (String pepper : List.of(invalidTestPepper, TEST_PEPPER)) {
            config.setCardCodePepper(pepper);
            assertThat(config.toString().contains(pepper)).isFalse();
            assertThat(config.toString().equals(redacted)).isTrue();
        }
    }

    private ApplicationContextRunner withYaml(String... resources) {
        return runner.withInitializer(context -> {
            try {
                for (String resource : resources) {
                    new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                }
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot load application configuration");
            }
        });
    }
}
