package com.cnhsk.learning.review;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.review.FsrsConfig.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsrsConfigTest {

    private static final List<Double> WEIGHTS = List.of(0.40255, 1.18385, 3.173, 15.69105, 7.1949,
            0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698,
            0.2315, 2.9898, 0.51655, 0.6621);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(FsrsConfig.class);

    @Test
    void AT19_lockedYamlDefaultsBindToValidatedSettings() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(FsrsConfig.class);
            FsrsConfig config = context.getBean(FsrsConfig.class);
            assertThat(config.getWeights()).containsExactlyElementsOf(WEIGHTS);
            assertThat(config.getDesiredRetention()).isEqualByComparingTo("0.9");
            assertThat(config.getMinIntervalDays()).isEqualTo(1);
            assertThat(config.getMaxIntervalDays()).isEqualTo(365);
            assertThat(config.getSessionMaxItems()).isEqualTo(50);
            assertThat(config.settings()).isEqualTo(new Settings(WEIGHTS, new BigDecimal("0.9"), 1, 365));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"weights", "desired-retention", "min-interval-days", "max-interval-days", "session-max-items"})
    void AT19_missingAnyRequiredSettingPreventsStartup(String missing) {
        String prefix = "cnhsk.fsrs." + missing;
        String[] properties = properties(WEIGHTS.size()).stream()
                .filter(property -> !property.startsWith(prefix + "=") && !property.startsWith(prefix + "["))
                .toArray(String[]::new);
        runner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 18, 20})
    void AT19_weightsMustHaveExactlyNineteenEntries(int count) {
        runner.withPropertyValues(properties(count).toArray(String[]::new))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e309", ""})
    void AT19_nonFiniteOrMissingWeightPreventsStartup(String invalid) {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs.weights[10]=" + invalid)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, -1", "2, 0", "3, -0.1"})
    void AT19_initialStabilityWeightsMustBePositive(int index, String invalid) {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs.weights[" + index + "]=" + invalid)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "-0.1", "1.1", "NaN", "Infinity", "abc"})
    void AT19_retentionMustBeStrictlyBetweenZeroAndOne(String invalid) {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs.desired-retention=" + invalid)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"min-interval-days=0", "max-interval-days=366", "max-interval-days=0",
            "min-interval-days=366", "session-max-items=0", "session-max-items=-1",
            "min-interval-days=abc", "session-max-items=2147483648"})
    void AT19_invalidIntervalsAndSessionLimitPreventStartup(String invalid) {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs." + invalid)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void AT19_reversedIntervalRangePreventsStartup() {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs.min-interval-days=10", "cnhsk.fsrs.max-interval-days=2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Test
    void AT19_validOverridesDoNotImposeAdditionalWeightOrSessionBounds() {
        runner.withPropertyValues(properties(19).toArray(String[]::new))
                .withPropertyValues("cnhsk.fsrs.weights[4]=-2", "cnhsk.fsrs.weights[17]=-1",
                        "cnhsk.fsrs.desired-retention=0.8", "cnhsk.fsrs.min-interval-days=2",
                        "cnhsk.fsrs.max-interval-days=10", "cnhsk.fsrs.session-max-items=100")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FsrsConfig config = context.getBean(FsrsConfig.class);
                    assertThat(config.getSessionMaxItems()).isEqualTo(100);
                    assertThat(config.settings()).isEqualTo(new Settings(config.getWeights(), new BigDecimal("0.8"), 2, 10));
                });
    }

    @Test
    void AT19_plainSettingsAreImmutableAndRejectInvalidNumericalParameters() {
        List<Double> mutable = new ArrayList<>(WEIGHTS);
        Settings settings = new Settings(mutable, new BigDecimal("0.9"), 1, 365);
        mutable.set(0, 20.0);
        assertThat(settings.weights()).containsExactlyElementsOf(WEIGHTS);
        assertThatThrownBy(() -> settings.weights().set(0, 20.0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(null, new BigDecimal("0.9"), 1, 365));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, null, 1, 365));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, BigDecimal.ZERO, 1, 365));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, BigDecimal.ONE, 1, 365));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, new BigDecimal("0.9"), 0, 365));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, new BigDecimal("0.9"), 1, 366));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(WEIGHTS, new BigDecimal("0.9"), 10, 2));
    }

    private List<String> properties(int weightCount) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < weightCount; index++) {
            values.add("cnhsk.fsrs.weights[" + index + "]=" + WEIGHTS.get(index % WEIGHTS.size()));
        }
        values.add("cnhsk.fsrs.desired-retention=0.9");
        values.add("cnhsk.fsrs.min-interval-days=1");
        values.add("cnhsk.fsrs.max-interval-days=365");
        values.add("cnhsk.fsrs.session-max-items=50");
        return values;
    }
}
