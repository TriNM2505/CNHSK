package com.cnhsk.learning.ai;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.LogLevel;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Anthropic Messages API client, selected only when {@code cnhsk.ai.provider=anthropic}. With
 * {@code cnhsk.ai.anthropic-base-url} it can call any Anthropic-compatible gateway instead of Anthropic.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "cnhsk.ai.provider", havingValue = "anthropic")
public class AnthropicClaudeClient implements ClaudeClient {
    private final String apiKey;
    /** Empty means the SDK default host; otherwise an Anthropic-compatible gateway root. */
    private final String baseUrl;
    private AnthropicClient sdk;

    @Autowired
    public AnthropicClaudeClient(Environment environment) {
        this(environment.getProperty("ANTHROPIC_API_KEY", ""),
                environment.getProperty("cnhsk.ai.anthropic-base-url", ""));
    }

    AnthropicClaudeClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    AnthropicClaudeClient(AnthropicClient sdk) {
        this.apiKey = "";
        this.baseUrl = "";
        this.sdk = Objects.requireNonNull(sdk);
    }

    /** The SDK appends {@code /v1/messages}, so a configured root ending in {@code /v1} is trimmed. */
    static String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (result.endsWith("/v1")) result = result.substring(0, result.length() - 3);
        return result;
    }

    private synchronized AnthropicClient client() {
        if (sdk == null) {
            if (apiKey.isEmpty()) {
                throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED);
            }
            // The SDK sends the key as a bearer token; gateways that follow the Messages API often read only
            // x-api-key, so send both.
            var builder = AnthropicOkHttpClient.builder().apiKey(apiKey).putHeader("x-api-key", apiKey);
            if (!baseUrl.isEmpty()) builder.baseUrl(baseUrl);
            sdk = builder.build();
        }
        return sdk;
    }

    @Override
    public Completion complete(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages,
            int maxTokens, Duration timeout) {
        if (model == null || model.isBlank() || maxTokens <= 0 || timeout == null
                || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Model, token limit and timeout must be valid");
        }
        try {
            var params = MessageCreateParams.builder().model(model).maxTokens(maxTokens)
                    .systemOfTextBlockParams(systemBlocks.stream().map(block -> {
                        var text = TextBlockParam.builder().text(block.text());
                        if (block.cache()) text.cacheControl(CacheControlEphemeral.builder().build());
                        return text.build();
                    }).toList());
            for (ChatMessage message : messages) {
                switch (message.role()) {
                    case USER -> params.addUserMessage(message.text());
                    case ASSISTANT -> params.addAssistantMessage(message.text());
                }
            }
            var response = client().withOptions(options -> {
                options.maxRetries(0);
                options.logLevel(LogLevel.OFF);
            }).messages()
                    .create(params.build(), RequestOptions.builder().timeout(timeout).build());
            var usage = response.usage();
            long input = Math.addExact(usage._inputTokens().asKnown().orElse(0L),
                    Math.addExact(usage.cacheCreationInputTokens().orElse(0L),
                            usage.cacheReadInputTokens().orElse(0L)));
            long output = usage._outputTokens().asKnown().orElse(0L);
            if (input < 0 || output < 0) {
                throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
            }
            String text = response.content().stream().filter(block -> block.isText())
                    .map(block -> block.asText().text()).collect(Collectors.joining("\n"));
            return new Completion(text, input, output);
        } catch (AnthropicException exception) {
            throw new AiUnavailableException(isTimeout(exception)
                    ? AiUnavailableException.Reason.TIMEOUT : AiUnavailableException.Reason.UPSTREAM_ERROR);
        } catch (ArithmeticException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        }
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    void close() {
        if (sdk != null) sdk.close();
    }
}
