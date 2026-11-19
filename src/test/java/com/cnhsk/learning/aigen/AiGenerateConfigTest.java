package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.ToIntFunction;

import com.cnhsk.learning.ai.AnthropicClaudeClient;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import com.cnhsk.shared.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

class AiGenerateConfigTest {
    private static final List<BusinessValue> BUSINESS_VALUES = List.of(
            new BusinessValue(SettingKey.AI_GENERATE_SYSTEM_USER_ID, 0, AiGenerateConfig::systemUserId),
            new BusinessValue(SettingKey.AI_GENERATE_WINDOW_DAYS, 14, AiGenerateConfig::windowDays),
            new BusinessValue(SettingKey.AI_GENERATE_MIN_WRONG, 20, AiGenerateConfig::minWrong),
            new BusinessValue(SettingKey.AI_GENERATE_MIN_BANK, 10, AiGenerateConfig::minBank),
            new BusinessValue(SettingKey.AI_GENERATE_BATCH_SIZE, 5, AiGenerateConfig::batchSize),
            new BusinessValue(SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN, 3, AiGenerateConfig::maxBatchesPerRun));
    private static final List<String> TECHNICAL_PROPERTIES = List.of(
            "cnhsk.ai.generate-model=claude-sonnet-5-5",
            "cnhsk.ai-generate.timeout=60s",
            "cnhsk.ai-generate.cron=0 0 2 * * *");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    PropertyPlaceholderAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(AiGenerateConfig.class, StubClaudeClient.class)
            .withBean(SettingsService.class, AiGenerateConfigTest::settingsDouble)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"));

    @Test
    void productionYamlBindsTechnicalDefaultsAndDelegatesAllBusinessDefaults() {
        withYaml("application.yaml").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SettingsApi.class).hasSingleBean(ClaudeClient.class);
            assertThat(context.getBean(SettingsApi.class)).isSameAs(context.getBean(SettingsService.class));
            AiGenerateConfig config = context.getBean(AiGenerateConfig.class);
            assertThat(config.getGenerateModel()).isEqualTo("deepseek-chat");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(config.getCron()).isEqualTo("0 0 2 * * *");
            for (BusinessValue value : BUSINESS_VALUES) {
                assertThat(context.getEnvironment().getProperty(value.key().defaultProperty(), Integer.class))
                        .isEqualTo(value.defaultValue());
                assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
                verify(context.getBean(SettingsService.class)).getInt(value.key());
            }
            assertThat(context.getEnvironment().getProperty("cnhsk.ai.assistant-model")).isEqualTo("deepseek-chat");
            assertThat(context.getEnvironment().getProperty("cnhsk.ai.translate-model")).isEqualTo("deepseek-chat");
            assertThat(context.getEnvironment().getProperty("cnhsk.ai.timeout")).isEqualTo("15s");
            assertThat(context.getBean(ClaudeClient.class)).isInstanceOf(StubClaudeClient.class);
            assertThat(context).doesNotHaveBean(AnthropicClaudeClient.class);
            assertThat(context.getBean(StubClaudeClient.class).callCount()).isZero();
        });
    }

    @Test
    void testYamlDisablesCronWithoutReplacingOtherDefaults() {
        withYaml("application-test.yaml", "application.yaml").run(context -> {
            assertThat(context).hasNotFailed();
            AiGenerateConfig config = context.getBean(AiGenerateConfig.class);
            assertThat(config.getCron()).isEqualTo("-");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(config.getGenerateModel()).isEqualTo("deepseek-chat");
            assertThat(context.getBean(StubClaudeClient.class).callCount()).isZero();
        });
    }

    @Test
    void businessAccessorsReadChangedSettingsIncludingBothAllowedZeros() {
        validRunner().run(context -> {
            assertThat(context).hasNotFailed();
            AiGenerateConfig config = context.getBean(AiGenerateConfig.class);
            SettingsService settings = context.getBean(SettingsService.class);
            for (BusinessValue value : BUSINESS_VALUES) {
                assertThat(value.read().applyAsInt(config)).isEqualTo(value.defaultValue());
                when(settings.getInt(value.key())).thenReturn(value.key().max());
                assertThat(value.read().applyAsInt(config)).isEqualTo(value.key().max());
                when(settings.getInt(value.key())).thenReturn(value.key().min());
                assertThat(value.read().applyAsInt(config)).isEqualTo(value.key().min());
            }
            assertThat(config.systemUserId()).isZero();
            assertThat(config.maxBatchesPerRun()).isZero();
            assertThat(context.getBean(StubClaudeClient.class).callCount()).isZero();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cnhsk.ai.generate-model=", "cnhsk.ai.generate-model=   ",
            "cnhsk.ai-generate.timeout=", "cnhsk.ai-generate.timeout=invalid",
            "cnhsk.ai-generate.timeout=0s", "cnhsk.ai-generate.timeout=-1s",
            "cnhsk.ai-generate.cron=", "cnhsk.ai-generate.cron=   ",
            "cnhsk.ai-generate.cron=0 2 * * *", "cnhsk.ai-generate.cron=0 0 25 * * *",
            "cnhsk.ai-generate.cron=invalid", "cnhsk.ai-generate.cron=@daily"})
    void invalidTechnicalValuesPreventStartup(String property) {
        validRunner().withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cnhsk.ai.generate-model", "cnhsk.ai-generate.timeout", "cnhsk.ai-generate.cron"})
    void missingTechnicalValuesPreventStartupWithoutYamlFallback(String missing) {
        runner.withPropertyValues(TECHNICAL_PROPERTIES.stream()
                        .filter(property -> !property.startsWith(missing + "=")).toArray(String[]::new))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customTechnicalValuesRequireNoProviderProbeOrModelAllowlist() {
        validRunner().withPropertyValues("cnhsk.ai.generate-model=custom-generation-model",
                "cnhsk.ai-generate.timeout=2h", "cnhsk.ai-generate.cron=0 30 3 * * MON-FRI").run(context -> {
                    assertThat(context).hasNotFailed();
                    AiGenerateConfig config = context.getBean(AiGenerateConfig.class);
                    assertThat(config.getGenerateModel()).isEqualTo("custom-generation-model");
                    assertThat(config.getTimeout()).isEqualTo(Duration.ofHours(2));
                    assertThat(config.getCron()).isEqualTo("0 30 3 * * MON-FRI");
                    assertThat(context.getBean(StubClaudeClient.class).callCount()).isZero();
                });
    }

    @Test
    void productionCronNextFireIsTwoAmInVietnam() {
        withYaml("application.yaml").run(context -> {
            assertThat(context).hasNotFailed();
            ZonedDateTime before = ZonedDateTime.of(2026, 11, 15, 1, 59, 59, 0, ZoneId.of("Asia/Ho_Chi_Minh"));
            assertThat(CronExpression.parse(context.getBean(AiGenerateConfig.class).getCron()).next(before))
                    .isEqualTo(before.plusSeconds(1));
        });
    }

    private ApplicationContextRunner validRunner() {
        return runner.withPropertyValues(TECHNICAL_PROPERTIES.toArray(String[]::new));
    }

    private ApplicationContextRunner withYaml(String... resources) {
        return runner.withInitializer(context -> {
            try {
                for (String resource : resources) {
                    new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot load application configuration");
            }
        });
    }

    private static SettingsService settingsDouble() {
        SettingsService settings = mock(SettingsService.class);
        BUSINESS_VALUES.forEach(value -> when(settings.getInt(value.key())).thenReturn(value.defaultValue()));
        return settings;
    }

    private record BusinessValue(SettingKey key, int defaultValue, ToIntFunction<AiGenerateConfig> read) { }
}
