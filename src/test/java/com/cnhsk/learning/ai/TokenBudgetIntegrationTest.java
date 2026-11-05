package com.cnhsk.learning.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class TokenBudgetIntegrationTest extends AbstractIntegrationTest {
    private static final String JANUARY = "ai:tokens:209901";
    private static final String FEBRUARY = "ai:tokens:209902";
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        redis.delete(List.of(JANUARY, FEBRUARY));
    }

    @Test
    void FR009_AT09_exactLimitDoesNotCallStubAndSuccessChargesBothTokenCounts() {
        var budget = budget(new AtomicReference<>(Instant.parse("2099-01-15T00:00:00Z")));
        var stub = new StubClaudeClient();
        redis.opsForValue().set(JANUARY, "1000");
        assertThatThrownBy(() -> budget.execute(() -> call(stub))).isInstanceOf(AiUnavailableException.class);
        assertThat(stub.callCount()).isZero();

        redis.opsForValue().set(JANUARY, "999");
        stub.enqueue(new ClaudeClient.Completion("Mẫu tự soạn", 7, 3));
        budget.execute(() -> call(stub));
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("1009");
        assertThatThrownBy(() -> budget.execute(() -> call(stub))).isInstanceOf(AiUnavailableException.class);
        assertThat(stub.callCount()).isEqualTo(1);
    }

    @Test
    void FR009_parallelAdmittedRequestsChargeAtomicallyAndMayExceedSoftLimit() throws Exception {
        var budget = budget(new AtomicReference<>(Instant.parse("2099-01-15T00:00:00Z")));
        redis.opsForValue().set(JANUARY, "990");
        var admitted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<ClaudeClient.Completion> request = () -> budget.execute(() -> {
                calls.incrementAndGet();
                admitted.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Admission timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted test request", exception);
                }
                return new ClaudeClient.Completion("Mẫu", 8, 2);
            });
            var first = pool.submit(request);
            var second = pool.submit(request);
            assertThat(admitted.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).inputTokens()).isEqualTo(8);
            assertThat(second.get(10, TimeUnit.SECONDS).inputTokens()).isEqualTo(8);
            assertThat(calls).hasValue(2);
            assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("1010");
            assertThatThrownBy(() -> budget.execute(() -> {
                calls.incrementAndGet();
                return new ClaudeClient.Completion("Không được gọi", 1, 1);
            })).isInstanceOf(AiUnavailableException.class);
            assertThat(calls).hasValue(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void FR009_vietnamMonthIsCapturedBeforeCallAndCounterRetainsThroughNextDay() {
        var now = new AtomicReference<>(Instant.parse("2099-01-31T16:59:59Z"));
        var budget = budget(now);
        budget.execute(() -> {
            now.set(Instant.parse("2099-01-31T17:00:01Z"));
            return new ClaudeClient.Completion("Mẫu", 7, 3);
        });
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("10");
        assertThat(redis.hasKey(FEBRUARY)).isFalse();
        assertThat(redis.getExpire(JANUARY, TimeUnit.SECONDS)).isBetween(86_390L, 86_400L);

        budget.execute(() -> new ClaudeClient.Completion("Tháng mới", 11, 2));
        assertThat(redis.opsForValue().get(FEBRUARY)).isEqualTo("13");
        assertThat(redis.opsForValue().get(JANUARY)).isEqualTo("10");
        // February 2099 has 28 days; retain its counter until March 2, Vietnamese time.
        assertThat(redis.getExpire(FEBRUARY, TimeUnit.SECONDS)).isBetween(29 * 86_400L - 10, 29 * 86_400L);
    }

    private TokenBudget budget(AtomicReference<Instant> now) {
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        var config = new AiConfig();
        config.setMonthlyTokenBudget(1000L);
        return new TokenBudget(redis, config, clock);
    }

    private ClaudeClient.Completion call(StubClaudeClient stub) {
        return stub.complete("claude-haiku-5-5", List.of(), List.of(), 800, java.time.Duration.ofSeconds(15));
    }
}
