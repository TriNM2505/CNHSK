package com.cnhsk.learning.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Calls the DeepSeek chat completions API (OpenAI-compatible). Selected when {@code cnhsk.ai.provider}
 * is {@code deepseek} or absent. Sends no sampling parameters, never retries and never logs content.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "cnhsk.ai.provider", havingValue = "deepseek", matchIfMissing = true)
public class DeepSeekChatClient implements ClaudeClient {
    static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private final String apiKey;
    private final URI endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http;

    @Autowired
    public DeepSeekChatClient(Environment environment) {
        this(environment.getProperty("DEEPSEEK_API_KEY", ""),
                URI.create(environment.getProperty("cnhsk.ai.deepseek-base-url", DEFAULT_BASE_URL)));
    }

    DeepSeekChatClient(String apiKey, URI baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String base = Objects.requireNonNull(baseUrl, "Base URL is required").toString();
        this.endpoint = URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
    }

    private synchronized HttpClient client() {
        if (apiKey.isEmpty()) throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED);
        if (http == null) http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return http;
    }

    @Override
    public Completion complete(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages,
            int maxTokens, Duration timeout) {
        if (model == null || model.isBlank() || maxTokens <= 0 || timeout == null
                || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Model, token limit and timeout must be valid");
        }
        HttpClient client = client();
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(model, systemBlocks, messages, maxTokens),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.TIMEOUT);
        } catch (IOException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        }
        if (response.statusCode() / 100 != 2) {
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        }
        return parse(response.body());
    }

    /** All system blocks become one system message; DeepSeek caches repeated prefixes automatically. */
    private String body(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages, int maxTokens) {
        ObjectNode root = mapper.createObjectNode().put("model", model).put("max_tokens", maxTokens)
                .put("stream", false);
        ArrayNode list = root.putArray("messages");
        String system = systemBlocks.stream().map(SystemBlock::text).collect(Collectors.joining("\n\n"));
        if (!system.isEmpty()) list.addObject().put("role", "system").put("content", system);
        for (ChatMessage message : messages) {
            list.addObject().put("role", message.role() == Role.USER ? "user" : "assistant")
                    .put("content", message.text());
        }
        return root.toString();
    }

    private Completion parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode content = root == null ? null : root.path("choices").path(0).path("message").path("content");
            if (content == null || !content.isTextual()) {
                throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
            }
            long input = root.path("usage").path("prompt_tokens").asLong(0);
            long output = root.path("usage").path("completion_tokens").asLong(0);
            if (input < 0 || output < 0) throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
            return new Completion(content.textValue(), input, output);
        } catch (JsonProcessingException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        }
    }
}
