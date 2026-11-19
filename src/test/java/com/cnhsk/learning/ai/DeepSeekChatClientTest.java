package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.ai.ClaudeClient.ChatMessage;
import com.cnhsk.learning.ai.ClaudeClient.Role;
import com.cnhsk.learning.ai.ClaudeClient.SystemBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeepSeekChatClientTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String OK = """
            {"choices":[{"message":{"role":"assistant","content":"Xin chào"}}],
             "usage":{"prompt_tokens":120,"completion_tokens":30}}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsAnOpenAiCompatibleRequestAndReadsTextAndUsage() throws Exception {
        URI base = serve(200, OK, 0);
        var result = new DeepSeekChatClient(" test-key ", base).complete("deepseek-chat",
                List.of(new SystemBlock("Rules", false), new SystemBlock("Data", true)),
                List.of(new ChatMessage(Role.USER, "Câu hỏi"), new ChatMessage(Role.ASSISTANT, "Trả lời"),
                        new ChatMessage(Role.USER, "Hỏi tiếp")), 800, TIMEOUT);

        assertThat(result).isEqualTo(new ClaudeClient.Completion("Xin chào", 120, 30));
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(800);
        assertThat(sent.path("stream").asBoolean(true)).isFalse();
        assertThat(sent.has("temperature")).isFalse();
        JsonNode messages = sent.path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("Rules\n\nData");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("Hỏi tiếp");
    }

    @Test
    void omitsTheSystemMessageWhenThereAreNoSystemBlocksAndTreatsMissingUsageAsZero() throws Exception {
        URI base = serve(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", 0);
        var result = new DeepSeekChatClient("key", base).complete("deepseek-chat", List.of(),
                List.of(new ChatMessage(Role.USER, "hi")), 50, TIMEOUT);
        assertThat(result).isEqualTo(new ClaudeClient.Completion("ok", 0, 0));
        JsonNode messages = mapper.readTree(body.get()).path("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
    }

    @Test
    void missingKeyFailsAsNotConfiguredWithoutAnyRequest() throws Exception {
        URI base = serve(200, OK, 0);
        assertThatThrownBy(() -> new DeepSeekChatClient("  ", base).complete("deepseek-chat", List.of(), List.of(),
                800, TIMEOUT)).isInstanceOfSatisfying(AiUnavailableException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.NOT_CONFIGURED);
                    assertThat(error.getCause()).isNull();
                });
        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 429, 500, 503})
    void nonSuccessStatusIsAnUpstreamErrorWithoutLeakingTheBody(int status) throws Exception {
        URI base = serve(status, "{\"error\":{\"message\":\"private upstream detail\"}}", 0);
        assertUnavailable(base, AiUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":null}}]}",
            "{\"choices\":[{\"message\":{\"content\":42}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"x\"}}],\"usage\":{\"prompt_tokens\":-1}}"})
    void malformedResponsesAreUpstreamErrors(String response) throws Exception {
        assertUnavailable(serve(200, response, 0), AiUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @Test
    void slowResponseIsATimeout() throws Exception {
        URI base = serve(200, OK, 2_000);
        assertThatThrownBy(() -> new DeepSeekChatClient("key", base).complete("deepseek-chat", List.of(),
                List.of(new ChatMessage(Role.USER, "hi")), 50, Duration.ofMillis(200)))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.TIMEOUT));
    }

    @Test
    void unreachableHostIsAnUpstreamError() throws Exception {
        URI base = serve(200, OK, 0);
        server.stop(0);
        server = null;
        assertUnavailable(base, AiUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @Test
    void rejectsInvalidArgumentsBeforeAnyRequest() throws Exception {
        URI base = serve(200, OK, 0);
        DeepSeekChatClient client = new DeepSeekChatClient("key", base);
        List<ChatMessage> messages = List.of(new ChatMessage(Role.USER, "hi"));
        assertThatThrownBy(() -> client.complete(" ", List.of(), messages, 50, TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.complete("deepseek-chat", List.of(), messages, 0, TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.complete("deepseek-chat", List.of(), messages, 50, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }

    private void assertUnavailable(URI base, AiUnavailableException.Reason reason) {
        assertThatThrownBy(() -> new DeepSeekChatClient("key", base).complete("deepseek-chat", List.of(),
                List.of(new ChatMessage(Role.USER, "hi")), 50, TIMEOUT))
                .isInstanceOfSatisfying(AiUnavailableException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(reason);
                    assertThat(error.getMessage()).doesNotContain("private upstream detail");
                });
    }

    private URI serve(int status, String response, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client may already have given up (timeout case).
            } finally {
                exchange.close();
            }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
