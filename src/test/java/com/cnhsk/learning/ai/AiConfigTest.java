package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class AiConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(AiConfig.class, DeepSeekChatClient.class, AnthropicClaudeClient.class,
                    StubClaudeClient.class)
            .withPropertyValues("DEEPSEEK_API_KEY=", "ANTHROPIC_API_KEY=")
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Cannot load application configuration");
                }
            });

    @Test
    void FR006_AT06_missingKeyAllowsStartupAndFailsOnlyOnCompletion() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ClaudeClient.class);
            AiConfig config = context.getBean(AiConfig.class);
            assertThat(config.getProvider()).isEqualTo("deepseek");
            assertThat(config.getAssistantModel()).isEqualTo("deepseek-chat");
            assertThat(config.getTranslateModel()).isEqualTo("deepseek-chat");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(config.getMonthlyTokenBudget()).isEqualTo(2_000_000L);
            ClaudeClient client = context.getBean(ClaudeClient.class);
            assertThat(client).isInstanceOf(DeepSeekChatClient.class);
            assertNotConfigured(client, config);
        });
    }

    @Test
    void FR006_AT06_anthropicProviderStillSelectsTheAnthropicClient() {
        runner.withPropertyValues("cnhsk.ai.provider=anthropic").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ClaudeClient.class);
            assertThat(context.getBean(ClaudeClient.class)).isInstanceOf(AnthropicClaudeClient.class);
            assertNotConfigured(context.getBean(ClaudeClient.class), context.getBean(AiConfig.class));
        });
    }

    @Test
    void FR006_AT06_testProfileSelectsOnlyOfflineStub() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test")).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ClaudeClient.class);
            assertThat(context.getBean(ClaudeClient.class)).isInstanceOf(StubClaudeClient.class);
            assertThat(context).doesNotHaveBean(AnthropicClaudeClient.class).doesNotHaveBean(DeepSeekChatClient.class);
        });
    }

    private static void assertNotConfigured(ClaudeClient client, AiConfig config) {
        assertThatThrownBy(() -> client.complete(config.getAssistantModel(), List.of(), List.of(),
                800, config.getTimeout())).isInstanceOfSatisfying(AiUnavailableException.class,
                        error -> {
                            assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.NOT_CONFIGURED);
                            assertThat(error.getCause()).isNull();
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"assistant-model=", "translate-model=", "timeout=", "timeout=0s",
            "timeout=-1s", "monthly-token-budget=0", "monthly-token-budget=-1", "provider=", "provider=openai"})
    void FR006_invalidConfigurationPreventsStartup(String property) {
        runner.withPropertyValues("cnhsk.ai." + property).run(context -> assertThat(context).hasFailed());
    }
}
