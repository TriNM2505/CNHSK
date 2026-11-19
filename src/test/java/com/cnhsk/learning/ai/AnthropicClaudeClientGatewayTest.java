package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.ai.ClaudeClient.ChatMessage;
import com.cnhsk.learning.ai.ClaudeClient.Role;
import com.cnhsk.learning.ai.ClaudeClient.SystemBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** An Anthropic-compatible gateway (for example a reseller serving DeepSeek models) is reachable by base URL. */
class AnthropicClaudeClientGatewayTest {
    private static final String RESPONSE = """
            {"id":"msg_1","type":"message","role":"assistant","model":"deepseek-v4.1-flash",
             "content":[{"type":"text","text":"Xin chào"}],"stop_reason":"end_turn","stop_sequence":null,
             "usage":{"input_tokens":12,"output_tokens":3}}""";

    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;
    private AnthropicClaudeClient client;

    @AfterEach
    void stop() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @ParameterizedTest
    @CsvSource({"''", "/", "/v1", "/v1/"})
    void callsTheMessagesEndpointOfTheConfiguredGateway(String suffix) throws Exception {
        String base = start();
        client = new AnthropicClaudeClient(" gateway-key ", base + suffix);
        var result = client.complete("deepseek-v4.1-flash", List.of(new SystemBlock("Rules", true)),
                List.of(new ChatMessage(Role.USER, "Xin chao")), 30, Duration.ofSeconds(10));

        assertThat(result).isEqualTo(new ClaudeClient.Completion("Xin chào", 12, 3));
        assertThat(path.get()).isEqualTo("/v1/messages");
        assertThat(apiKey.get()).isEqualTo("gateway-key");
        // Exactly one credential form reaches the gateway; a stray bearer must not carry another value.
        assertThat(authorization.get()).isIn(null, "Bearer gateway-key");
        var sent = new ObjectMapper().readTree(body.get());
        assertThat(sent.path("model").asText()).isEqualTo("deepseek-v4.1-flash");
        assertThat(sent.path("system").get(0).path("cache_control").path("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    void blankBaseUrlKeepsTheSdkDefault() {
        assertThat(AnthropicClaudeClient.normalizeBaseUrl(null)).isEmpty();
        assertThat(AnthropicClaudeClient.normalizeBaseUrl("  ")).isEmpty();
        assertThat(AnthropicClaudeClient.normalizeBaseUrl("https://gw.example/v1/")).isEqualTo("https://gw.example");
        assertThat(AnthropicClaudeClient.normalizeBaseUrl("https://gw.example")).isEqualTo("https://gw.example");
    }

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
