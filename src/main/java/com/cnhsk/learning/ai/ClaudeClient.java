package com.cnhsk.learning.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public interface ClaudeClient {
    Completion complete(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages,
            int maxTokens, Duration timeout);

    enum Role { USER, ASSISTANT }

    record SystemBlock(String text, boolean cache) {
        public SystemBlock {
            Objects.requireNonNull(text, "System block text is required");
        }
    }

    record ChatMessage(Role role, String text) {
        public ChatMessage {
            Objects.requireNonNull(role, "Message role is required");
            Objects.requireNonNull(text, "Message text is required");
        }
    }

    record Completion(String text, long inputTokens, long outputTokens) {
        public Completion {
            Objects.requireNonNull(text, "Completion text is required");
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("Token counts must be non-negative");
            }
        }
    }
}
