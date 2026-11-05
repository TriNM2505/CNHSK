package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ClientOptions;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicClaudeClientTest {
    @Mock private AnthropicClient sdk;
    @Mock private MessageService messages;
    private AnthropicClaudeClient client;

    @BeforeEach
    void setup() {
        client = new AnthropicClaudeClient(sdk);
        lenient().when(sdk.withOptions(any())).thenReturn(sdk);
        lenient().when(sdk.messages()).thenReturn(messages);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 15})
    void FR006_AT06_mapsRequestWithoutTemperatureAndIncludesCachedUsage(int seconds) {
        Message response = mock(Message.class);
        var usage = usage(7, 3, 11L, 13L);
        when(response.usage()).thenReturn(usage);
        when(response.content()).thenReturn(List.of(
                ContentBlock.ofText(TextBlock.builder().citations(List.of()).text("Hướng dẫn tự soạn.").build()),
                ContentBlock.ofText(TextBlock.builder().citations(List.of()).text("Mở danh sách bài học.").build())));
        when(messages.create(any(MessageCreateParams.class), any(RequestOptions.class))).thenReturn(response);

        var result = client.complete("claude-haiku-5-5",
                List.of(new ClaudeClient.SystemBlock("Hướng dẫn hệ thống", true),
                        new ClaudeClient.SystemBlock("Quy tắc", false)),
                List.of(new ClaudeClient.ChatMessage(ClaudeClient.Role.USER, "Mở bài học ở đâu?"),
                        new ClaudeClient.ChatMessage(ClaudeClient.Role.ASSISTANT, "Chọn Học tập.")),
                800, Duration.ofSeconds(seconds));

        assertThat(result).isEqualTo(new ClaudeClient.Completion(
                "Hướng dẫn tự soạn.\nMở danh sách bài học.", 31, 3));
        var params = ArgumentCaptor.forClass(MessageCreateParams.class);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(messages).create(params.capture(), options.capture());
        assertThat(params.getValue().model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(params.getValue().maxTokens()).isEqualTo(800);
        assertThat(params.getValue().messages()).containsExactly(
                MessageParam.ofUser("Mở bài học ở đâu?"), MessageParam.ofAssistant("Chọn Học tập."));
        var system = params.getValue().system().orElseThrow().asTextBlockParams();
        assertThat(system).hasSize(2);
        assertThat(system.get(0).text()).isEqualTo("Hướng dẫn hệ thống");
        assertThat(system.get(0).cacheControl()).isPresent();
        assertThat(system.get(1).cacheControl()).isEmpty();
        // API 2026-10-21 rejects temperature, including an explicit zero.
        assertThat(params.getValue()._additionalBodyProperties()).doesNotContainKey("temperature");
        assertThat(options.getValue().timeout().orElseThrow().request()).isEqualTo(Duration.ofSeconds(seconds));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ClientOptions.Builder>> configure = ArgumentCaptor.forClass(Consumer.class);
        verify(sdk).withOptions(configure.capture());
        var builder = mock(ClientOptions.Builder.class);
        configure.getValue().accept(builder);
        verify(builder).maxRetries(0);
        verify(builder).logLevel(com.anthropic.core.LogLevel.OFF);
    }

    @Test
    void FR006_missingUsageFieldsCountAsZeroAndEmptyTextIsAllowed() {
        var response = mock(Message.class);
        var usage = mock(Usage.class);
        when(usage._inputTokens()).thenReturn(com.anthropic.core.JsonField.ofNullable(null));
        when(usage._outputTokens()).thenReturn(com.anthropic.core.JsonField.ofNullable(null));
        when(usage.cacheCreationInputTokens()).thenReturn(Optional.empty());
        when(usage.cacheReadInputTokens()).thenReturn(Optional.empty());
        when(response.usage()).thenReturn(usage);
        when(response.content()).thenReturn(List.of());
        when(messages.create(any(MessageCreateParams.class), any(RequestOptions.class))).thenReturn(response);
        assertThat(call()).isEqualTo(new ClaudeClient.Completion("", 0, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 408, 409, 422, 429, 500, 503})
    void FR006_AT06_upstreamStatusIsSanitized(int status) {
        var error = new AnthropicServiceException("private-upstream-body-" + status, null) {
            @Override public int statusCode() { return status; }
            @Override public com.anthropic.core.http.Headers headers() {
                return com.anthropic.core.http.Headers.builder().build();
            }
            @Override public com.anthropic.core.JsonValue body() {
                return com.anthropic.core.JsonValue.from("private-upstream-body");
            }
        };
        assertSanitized(error, AiUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @Test
    void FR006_AT06_networkErrorIsSanitized() {
        assertSanitized(mock(AnthropicIoException.class), AiUnavailableException.Reason.UPSTREAM_ERROR);
    }

    @Test
    void FR006_AT06_timeoutIsSanitized() {
        var error = mock(AnthropicIoException.class);
        when(error.getCause()).thenReturn(new SocketTimeoutException("private-timeout-message"));
        assertSanitized(error, AiUnavailableException.Reason.TIMEOUT);
    }

    @Test
    void FR006_invalidTokenUsageCannotWrapAround() {
        var response = mock(Message.class);
        var usage = usage(Long.MAX_VALUE, 0, 1L, null);
        when(response.usage()).thenReturn(usage);
        when(messages.create(any(MessageCreateParams.class), any(RequestOptions.class))).thenReturn(response);
        assertThatThrownBy(this::call).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.UPSTREAM_ERROR));
    }

    @Test
    void FR006_negativeTokenUsageIsRejected() {
        var response = mock(Message.class);
        var usage = usage(-1, 0, null, null);
        when(response.usage()).thenReturn(usage);
        when(messages.create(any(MessageCreateParams.class), any(RequestOptions.class))).thenReturn(response);
        assertThatThrownBy(this::call).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void FR006_closesOnlyTheOwnedSdkClient() {
        client.close();
        verify(sdk).close();
    }

    private ClaudeClient.Completion call() {
        return client.complete("claude-haiku-5-5", List.of(),
                List.of(new ClaudeClient.ChatMessage(ClaudeClient.Role.USER, "Hướng dẫn mẫu")),
                800, Duration.ofSeconds(15));
    }

    private Usage usage(long input, long output, Long created, Long read) {
        var usage = mock(Usage.class);
        when(usage._inputTokens()).thenReturn(com.anthropic.core.JsonField.of(input));
        lenient().when(usage._outputTokens()).thenReturn(com.anthropic.core.JsonField.of(output));
        when(usage.cacheCreationInputTokens()).thenReturn(Optional.ofNullable(created));
        when(usage.cacheReadInputTokens()).thenReturn(Optional.ofNullable(read));
        return usage;
    }

    private void assertSanitized(AnthropicException failure, AiUnavailableException.Reason reason) {
        when(messages.create(any(MessageCreateParams.class), any(RequestOptions.class))).thenThrow(failure);
        assertThatThrownBy(this::call).isInstanceOfSatisfying(AiUnavailableException.class, error -> {
            assertThat(error.getReason()).isEqualTo(reason);
            assertThat(error.getMessage()).isEqualTo("AI service is temporarily unavailable");
            assertThat(error.getCause()).isNull();
        });
        verify(messages, times(1)).create(any(MessageCreateParams.class), any(RequestOptions.class));
    }
}
