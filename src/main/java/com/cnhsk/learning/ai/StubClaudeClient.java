package com.cnhsk.learning.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubClaudeClient implements ClaudeClient {
    private final Queue<Supplier<Completion>> responses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger calls = new AtomicInteger();

    public void enqueue(Completion response) {
        Objects.requireNonNull(response);
        responses.add(() -> response);
    }

    public void enqueueFailure(AiUnavailableException.Reason reason) {
        Objects.requireNonNull(reason);
        responses.add(() -> { throw new AiUnavailableException(reason); });
    }

    public int callCount() {
        return calls.get();
    }

    @Override
    public Completion complete(String model, List<SystemBlock> systemBlocks, List<ChatMessage> messages,
            int maxTokens, Duration timeout) {
        calls.incrementAndGet();
        var response = responses.poll();
        if (response == null) {
            throw new AiUnavailableException(AiUnavailableException.Reason.NOT_CONFIGURED);
        }
        return response.get();
    }
}
