package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBudgetUsageTest {
    private static final String NOVEMBER = "ai:tokens:202611";
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private SettingsService settings;
    @Mock private Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private TokenBudget budget;

    @BeforeEach
    void setup() {
        now.set(Instant.parse("2026-11-05T12:00:00Z"));
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(redis.opsForValue()).thenReturn(values);
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(1000);
        budget = new TokenBudget(redis, settings, clock);
    }

    @Test
    void absentCounterIsAnAvailableZeroAndOnlyReadsRedis() {
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", 0L, 1000, true));
        verify(redis).opsForValue();
        verify(values).get(NOVEMBER);
        verifyNoMoreInteractions(redis, values);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 42, 1001, Long.MAX_VALUE})
    void readsTheFullNonNegativeLongWithoutCappingItToTheBudget(long used) {
        when(values.get(NOVEMBER)).thenReturn(Long.toString(used));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", used, 1000, true));
        verify(redis).opsForValue();
        verify(values).get(NOVEMBER);
        verifyNoMoreInteractions(redis, values);
    }

    @Test
    void readsTheBudgetAgainOnEveryCallWithoutCachingOrResettingCounters() {
        when(values.get(NOVEMBER)).thenReturn("1234");
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(0, 1500, 800);
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", 1234L, 0, true));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", 1234L, 1500, true));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", 1234L, 800, true));
        verify(settings, times(3)).getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET);
        verify(redis, times(3)).opsForValue();
        verify(values, times(3)).get(NOVEMBER);
        verifyNoMoreInteractions(redis, values);
    }

    @Test
    void changesTheMonthAtMidnightInVietnamOnTheSameInstance() {
        now.set(Instant.parse("2026-11-30T16:59:59.999Z"));
        when(values.get(NOVEMBER)).thenReturn("91");
        when(values.get("ai:tokens:202612")).thenReturn("7");
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", 91L, 1000, true));
        now.set(Instant.parse("2026-11-30T17:00:00Z"));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-12", 7L, 1000, true));
        now.set(Instant.parse("2026-12-31T17:00:00Z"));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2027-01", 0L, 1000, true));
        verify(values).get(NOVEMBER);
        verify(values).get("ai:tokens:202612");
        verify(values).get("ai:tokens:202701");
        verify(redis, times(3)).opsForValue();
        verifyNoMoreInteractions(redis, values);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "private-counter-fixture", "1.5", " 42 ", "9223372036854775808", "-1", "-9223372036854775808"})
    void invalidOrNegativeCountersAreUnavailableWithoutLoggingTheirContents(String raw) {
        when(values.get(NOVEMBER)).thenReturn(raw);
        assertUnavailableAndSafeWarning(raw);
    }

    @Test
    void redisReadFailureIsUnavailableAndDoesNotLeakExceptionContents() {
        when(values.get(NOVEMBER)).thenThrow(new RedisConnectionFailureException("private-redis-fixture"));
        assertUnavailableAndSafeWarning("private-redis-fixture");
    }

    private void assertUnavailableAndSafeWarning(String raw) {
        var logger = (Logger) LoggerFactory.getLogger(TokenBudget.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2026-11", null, 1000, false));
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("AI token usage read unavailable");
            if (!raw.isEmpty()) assertThat(event.getFormattedMessage()).doesNotContain(raw);
            assertThat(event.getThrowableProxy()).isNull();
            verify(redis).opsForValue();
            verify(values).get(NOVEMBER);
            verifyNoMoreInteractions(redis, values);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
