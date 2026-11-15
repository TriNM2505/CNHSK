package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class TokenBudgetTest {
    private static final String KEY = "ai:tokens:202611";
    private static final ClaudeClient.Completion RESULT = new ClaudeClient.Completion("Mẫu", 7, 3);
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private Supplier<ClaudeClient.Completion> operation;
    private TokenBudget budget;
    private AiConfig config;

    @BeforeEach
    void setup() {
        config = new AiConfig();
        config.setMonthlyTokenBudget(1000L);
        budget = new TokenBudget(redis, settings(config), Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC));
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void FR009_AT09_exactlyAtBudgetNeverCallsSupplier() {
        when(values.get(KEY)).thenReturn("1000");
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.BUDGET_EXCEEDED));
        verifyNoInteractions(operation);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void FR009_AT09_addsInputAndOutputAfterSuccessfulResponse() {
        when(values.get(KEY)).thenReturn("999");
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq("10"), anyString());
    }

    @Test
    void FR009_doesNotChargeFailedCompletion() {
        when(operation.get()).thenThrow(new AiUnavailableException(AiUnavailableException.Reason.TIMEOUT));
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOf(AiUnavailableException.class);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void FR009_redisFailuresAllowCallAndWarnWithoutSensitiveData() {
        var logger = (Logger) LoggerFactory.getLogger(TokenBudget.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("private-connection-marker"));
            when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenThrow(new RedisConnectionFailureException("private-connection-marker"));
            when(operation.get()).thenReturn(RESULT);
            assertThat(budget.execute(operation)).isSameAs(RESULT);
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("private-connection-marker", RESULT.text());
                assertThat(event.getThrowableProxy()).isNull();
            });
            verify(operation).get();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void FR009_unreadableCounterAllowsCall() {
        when(values.get(KEY)).thenReturn("invalid-counter");
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
    }

    @Test
    void FR009_overflowCannotWrapTokenCountBelowBudget() {
        when(operation.get()).thenReturn(new ClaudeClient.Completion("Mẫu", Long.MAX_VALUE, 1));
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.UPSTREAM_ERROR));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void zeroBudgetRejectsBeforeSupplierEvenWhenRedisKeyDoesNotExist() {
        config.setMonthlyTokenBudget(0L);
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOfSatisfying(AiUnavailableException.class,
                error -> assertThat(error.getReason()).isEqualTo(AiUnavailableException.Reason.BUDGET_EXCEEDED));
        verify(values).get(KEY);
        verifyNoInteractions(operation);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void positiveBudgetAllowsFirstCallWithoutAnExistingRedisKey() {
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
        verify(values).get(KEY);
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq("10"), anyString());
    }

    @Test
    void budgetIsReadAgainForEveryCallOnTheSameInstanceWithoutResettingUsage() {
        config.setMonthlyTokenBudget(10L);
        when(values.get(KEY)).thenReturn("10");
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOf(AiUnavailableException.class);
        config.setMonthlyTokenBudget(11L);
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
        config.setMonthlyTokenBudget(0L);
        assertThatThrownBy(() -> budget.execute(operation)).isInstanceOf(AiUnavailableException.class);
        verify(operation).get();
        verify(values, times(3)).get(KEY);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void zeroBudgetStillFailsOpenWhenRedisCannotReadTheCounter() {
        config.setMonthlyTokenBudget(0L);
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("fixture"));
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
    }

    @Test
    void zeroBudgetStillFailsOpenForAnUnreadableCounter() {
        config.setMonthlyTokenBudget(0L);
        when(values.get(KEY)).thenReturn("invalid-counter");
        when(operation.get()).thenReturn(RESULT);
        assertThat(budget.execute(operation)).isSameAs(RESULT);
    }

    private static com.cnhsk.shared.settings.SettingsService settings(AiConfig config) {
        var settings = org.mockito.Mockito.mock(com.cnhsk.shared.settings.SettingsService.class);
        org.mockito.Mockito.when(settings.getInt(com.cnhsk.shared.settings.SettingKey.AI_MONTHLY_TOKEN_BUDGET))
                .thenAnswer(invocation -> Math.toIntExact(config.getMonthlyTokenBudget()));
        return settings;
    }
}
