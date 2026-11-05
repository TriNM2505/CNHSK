package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class StubClaudeClientTest {
    @Test
    void FR006_AT06_returnsScriptedResultsAndTimeoutWithoutNetwork() {
        var client = new StubClaudeClient();
        var expected = new ClaudeClient.Completion("Câu trả lời tự soạn", 15, 4);
        client.enqueue(expected);
        client.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        assertThat(call(client)).isSameAs(expected);
        assertThatThrownBy(() -> call(client)).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.TIMEOUT));
        assertThatThrownBy(() -> call(client)).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.NOT_CONFIGURED));
        assertThat(client.callCount()).isEqualTo(3);
    }

    @Test
    void FR006_publicValuesRejectNullAndNegativeCounts() {
        assertThatThrownBy(() -> new ClaudeClient.SystemBlock(null, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaudeClient.ChatMessage(null, "message")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaudeClient.ChatMessage(ClaudeClient.Role.USER, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaudeClient.Completion(null, 1, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaudeClient.Completion("text", -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaudeClient.Completion("text", 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiUnavailableException(null)).isInstanceOf(NullPointerException.class);
        var client = new StubClaudeClient();
        assertThatThrownBy(() -> client.enqueue(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.enqueueFailure(null)).isInstanceOf(NullPointerException.class);
    }

    private ClaudeClient.Completion call(ClaudeClient client) {
        return client.complete("model-test", List.of(new ClaudeClient.SystemBlock("Hướng dẫn mẫu", true)),
                List.of(new ClaudeClient.ChatMessage(ClaudeClient.Role.USER, "Câu hỏi mẫu")),
                800, Duration.ofSeconds(15));
    }
}
