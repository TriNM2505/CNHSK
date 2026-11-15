package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class TokenBudgetUsageIntegrationTest extends AbstractIntegrationTest {
    private static final String JANUARY = "ai:tokens:219801";
    private static final String FEBRUARY = "ai:tokens:219802";
    @Autowired private StringRedisTemplate redis;
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private TokenBudget budget;
    private SettingsService settings;

    @BeforeEach
    void setup() {
        redis.delete(List.of(JANUARY, FEBRUARY));
        now.set(Instant.parse("2198-01-15T00:00:00Z"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        settings = mock(SettingsService.class);
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(1000);
        budget = new TokenBudget(redis, settings, clock);
    }

    @AfterEach
    void cleanup() {
        redis.delete(List.of(JANUARY, FEBRUARY));
    }

    @Test
    void absentKeyStaysAbsentIncludingWhenTheBudgetIsZero() {
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(0);
        for (int i = 0; i < 3; i++)
            assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-01", 0L, 0, true));
        assertThat(redis.hasKey(JANUARY)).isFalse();
        assertThat(redis.getExpire(JANUARY)).isEqualTo(-2L);
    }

    @Test
    void readingDoesNotChangeTheCounterOrRenewItsExactExpiry() {
        redis.opsForValue().set(JANUARY, "1234", Duration.ofMinutes(12));
        // Redis TIME + PTTL permits bracketing the absolute deadline without PEXPIRETIME (Redis 7+).
        long earliestExpiry = serverMillis() + redis.getExpire(JANUARY, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 5; i++)
            assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-01", 1234L, 1000, true));
        long remaining = redis.getExpire(JANUARY, TimeUnit.MILLISECONDS);
        long latestExpiry = serverMillis() + remaining;
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("1234");
        assertThat(remaining).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(12).toMillis());
        assertThat(latestExpiry).isCloseTo(earliestExpiry, org.assertj.core.data.Offset.offset(1000L));
    }

    @Test
    void readingAPersistentCounterDoesNotAddAnExpiry() {
        redis.opsForValue().set(JANUARY, "42");
        assertThat(budget.currentUsage().used()).isEqualTo(42L);
        assertThat(redis.getExpire(JANUARY)).isEqualTo(-1L);
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("42");
    }

    @Test
    void malformedAndNegativeCountersAreNotRepairedOrGivenAnotherTtl() {
        for (String raw : List.of("self-authored-invalid", "-7", "9223372036854775808")) {
            redis.opsForValue().set(JANUARY, raw);
            assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-01", null, 1000, false));
            assertThat(redis.opsForValue().get(JANUARY)).isEqualTo(raw);
            assertThat(redis.getExpire(JANUARY)).isEqualTo(-1L);
        }
    }

    @Test
    void vietnamRolloverIgnoresPreviousMonthAndExecuteUsesTheSameKey() {
        redis.opsForValue().set(JANUARY, "31");
        now.set(Instant.parse("2198-01-31T16:59:59.999Z"));
        assertThat(budget.currentUsage().used()).isEqualTo(31L);
        now.set(Instant.parse("2198-01-31T17:00:00Z"));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-02", 0L, 1000, true));
        assertThat(redis.hasKey(FEBRUARY)).isFalse();
        budget.execute(() -> new ClaudeClient.Completion("Mẫu tự soạn", 7, 3));
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-02", 10L, 1000, true));
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("31");
        assertThat(redis.getExpire(JANUARY)).isEqualTo(-1L);
        assertThat(redis.opsForValue().get(FEBRUARY)).isEqualTo("10");
        when(settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET)).thenReturn(0);
        assertThat(budget.currentUsage()).isEqualTo(new TokenBudget.Usage("2198-02", 10L, 0, true));
    }

    private long serverMillis() {
        return redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> connection.serverCommands().time());
    }
}
