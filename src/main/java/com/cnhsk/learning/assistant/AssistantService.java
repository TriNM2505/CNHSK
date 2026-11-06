package com.cnhsk.learning.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.cnhsk.learning.ai.AiConfig;
import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
import com.cnhsk.learning.assistant.AssistantDailyLimit.RateLimitExceededException;
import com.cnhsk.learning.assistant.AssistantDailyLimit.Reservation;
import com.cnhsk.learning.assistant.AssistantPromptBuilder.Prompt;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String UNAVAILABLE_MESSAGE =
            "Trợ lý tạm thời chưa trả lời được, bạn thử lại sau";

    private final TestInProgressGuard testGuard;
    private final AssistantDailyLimit dailyLimit;
    private final AssistantPromptBuilder promptBuilder;
    private final TokenBudget tokenBudget;
    private final ClaudeClient client;
    private final AiConfig config;
    private final ObjectReader jsonReader;
    private final Clock clock;

    public AssistantService(TestInProgressGuard testGuard, AssistantDailyLimit dailyLimit,
            AssistantPromptBuilder promptBuilder, TokenBudget tokenBudget, ClaudeClient client,
            AiConfig config, ObjectMapper mapper, Clock clock) {
        this.testGuard = testGuard;
        this.dailyLimit = dailyLimit;
        this.promptBuilder = promptBuilder;
        this.tokenBudget = tokenBudget;
        this.client = client;
        this.config = config;
        this.jsonReader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
    }

    public AskResult ask(long userId, AskCommand command) {
        Instant started = clock.instant();
        testGuard.check(userId, started);

        Reservation reservation;
        try {
            reservation = dailyLimit.reserve(userId);
        } catch (RateLimitExceededException exception) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã hỏi đủ số lượt hôm nay.", Map.of("retryAfterSeconds", exception.retryAfterSeconds()));
        }

        try (reservation) {
            Optional<Prompt> prompt = promptBuilder.build(command.question(), command.history(), command.pageContext());
            if (prompt.isEmpty()) {
                throw unavailable();
            }
            Prompt built = prompt.get();
            ClaudeClient.Completion completion;
            try {
                completion = tokenBudget.execute(() -> {
                    reservation.commit();
                    return client.complete(config.getAssistantModel(), built.systemBlocks(), built.messages(),
                            800, config.getTimeout());
                });
            } catch (AiUnavailableException exception) {
                throw unavailable();
            }

            ParsedAnswer parsed = parse(completion.text());
            if (parsed.answer().isBlank()) {
                throw unavailable();
            }
            long elapsed = Math.max(0, Duration.between(started, clock.instant()).toMillis());
            log.info("Assistant response userId={} inputTokens={} outputTokens={} durationMs={} outOfScope={}",
                    userId, completion.inputTokens(), completion.outputTokens(), elapsed, parsed.outOfScope());
            return new AskResult(parsed.answer(), parsed.outOfScope());
        } catch (ApiException exception) {
            log.warn("Assistant request failed userId={} errorCode={}", userId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Assistant request failed userId={} errorCode={}", userId, ErrorCode.ASSISTANT_UNAVAILABLE);
            throw unavailable();
        }
    }

    private ParsedAnswer parse(String text) {
        if (text.isBlank()) {
            throw unavailable();
        }
        JsonNode root;
        try {
            root = jsonReader.readTree(text);
        } catch (JsonProcessingException exception) {
            return new ParsedAnswer(text, false);
        }
        if (root == null || !root.isObject() || !root.path("in_scope").isBoolean()
                || !root.path("answer").isTextual()) {
            throw unavailable();
        }
        return new ParsedAnswer(root.path("answer").textValue(), !root.path("in_scope").booleanValue());
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                UNAVAILABLE_MESSAGE, Map.of());
    }

    public record AskCommand(String question, List<ClaudeClient.ChatMessage> history, String pageContext) {
        public AskCommand {
            history = List.copyOf(history);
        }
    }

    public record AskResult(String answer, boolean outOfScope) {
    }

    private record ParsedAnswer(String answer, boolean outOfScope) {
    }
}
