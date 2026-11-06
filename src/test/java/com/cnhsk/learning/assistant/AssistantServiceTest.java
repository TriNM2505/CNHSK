package com.cnhsk.learning.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.ai.AiConfig;
import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.TokenBudget;
import com.cnhsk.learning.assistant.AssistantDailyLimit.Reservation;
import com.cnhsk.learning.assistant.AssistantPromptBuilder.Prompt;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {
    @Mock private TestInProgressGuard guard;
    @Mock private AssistantDailyLimit limit;
    @Mock private AssistantPromptBuilder promptBuilder;
    @Mock private TokenBudget budget;
    @Mock private ClaudeClient client;
    @Mock private Reservation reservation;
    private final AiConfig config = new AiConfig();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T04:00:00Z"), ZoneOffset.UTC);
    private AssistantService service;
    private final AssistantService.AskCommand input = new AssistantService.AskCommand("question", List.of(), null);
    private final Prompt prompt = new Prompt(List.of(new ClaudeClient.SystemBlock("rules", false)),
            List.of(new ClaudeClient.ChatMessage(ClaudeClient.Role.USER, "question")));

    @BeforeEach
    void setup() {
        config.setAssistantModel("claude-haiku-5-5");
        config.setTimeout(Duration.ofSeconds(15));
        service = new AssistantService(guard, limit, promptBuilder, budget, client, config, mapper, clock);
        when(limit.reserve(9L)).thenReturn(reservation);
    }

    @Test
    void FR008_returnsParsedAnswerAndOutOfScope() {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.of(prompt));
        when(budget.execute(any())).thenAnswer(call ->
                ((java.util.function.Supplier<ClaudeClient.Completion>) call.getArgument(0))
                        .get());
        when(client.complete(any(), any(), any(), anyInt(), any()))
                .thenReturn(new ClaudeClient.Completion("{\"in_scope\":false,\"answer\":\"help\"}", 12, 3));

        assertThat(service.ask(9L, input)).isEqualTo(new AssistantService.AskResult("help", true));
        verify(reservation).commit();
        verify(guard).check(eq(9L), any(Instant.class));
    }

    @Test
    void FR008_nonJsonIsReturnedVerbatim() {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.of(prompt));
        when(budget.execute(any())).thenAnswer(call ->
                ((java.util.function.Supplier<ClaudeClient.Completion>) call.getArgument(0)).get());
        when(client.complete(any(), any(), any(), anyInt(), any()))
                .thenReturn(new ClaudeClient.Completion("plain response", 1, 1));
        assertThat(service.ask(9L, input)).isEqualTo(new AssistantService.AskResult("plain response", false));
    }

    @Test
    void FR010_emptyLibraryDoesNotCallClaudeAndReservationIsReleased() {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ask(9L, input))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
        verifyNoInteractions(client, budget);
        verify(reservation).close();
        verify(reservation, never()).commit();
    }

    @Test
    void FR009_tokenBudgetRejectsBeforeClientAndReservationIsReleased() {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.of(prompt));
        when(budget.execute(any())).thenThrow(new AiUnavailableException(AiUnavailableException.Reason.BUDGET_EXCEEDED));
        assertThatThrownBy(() -> service.ask(9L, input))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
        verifyNoInteractions(client);
        verify(reservation).close();
        verify(reservation, never()).commit();
    }

    @Test
    void FR006_clientFailureKeepsReservation() {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.of(prompt));
        when(budget.execute(any())).thenAnswer(call ->
                ((java.util.function.Supplier<ClaudeClient.Completion>) call.getArgument(0)).get());
        when(client.complete(any(), any(), any(), anyInt(), any()))
                .thenThrow(new AiUnavailableException(AiUnavailableException.Reason.TIMEOUT));
        assertThatThrownBy(() -> service.ask(9L, input))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
        verify(reservation).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "null", "[]", "{\"in_scope\":true}", "{\"in_scope\":\"true\",\"answer\":\"x\"}",
            "{\"in_scope\":true,\"answer\":\"  \"}"})
    void FR008_rejectsBlankAnswersAndJsonWithWrongSchema(String reply) {
        when(promptBuilder.build("question", List.of(), null)).thenReturn(Optional.of(prompt));
        when(budget.execute(any())).thenAnswer(call ->
                call.<java.util.function.Supplier<ClaudeClient.Completion>>getArgument(0).get());
        when(client.complete(any(), any(), any(), anyInt(), any()))
                .thenReturn(new ClaudeClient.Completion(reply, 1, 1));

        assertThatThrownBy(() -> service.ask(9L, input))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
        verify(reservation).commit();
    }
}
