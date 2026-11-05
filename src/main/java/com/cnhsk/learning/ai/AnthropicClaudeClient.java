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
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class AnthropicClaudeClient implements ClaudeClient {
    private final String apiKey;
    private AnthropicClient sdk;

    @Autowired
    public AnthropicClaudeClient(Environment environment) {
        apiKey = environment.getProperty("ANTHROPIC_API_KEY", "").trim();
    }

    AnthropicClaudeClient(AnthropicClient sdk) {
        this.apiKey = "";
        this.sdk = Objects.requireNonNull(sdk);
    }

    private synchronized AnthropicClient client() {
        if (sdk == null) {
            if (apiKey.isEmpty()) {
                throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED);
            }
            sdk = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
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
