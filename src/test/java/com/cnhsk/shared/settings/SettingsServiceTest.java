package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SettingsServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AdjustableClock clock = new AdjustableClock();
    private SettingsService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            return new SimpleTransactionStatus();
        });
        service = new SettingsService(jdbc, SettingKeyTest.yamlDefaults(), clock, transactions);
    }

    @Test
    void noRowUsesYamlAndCachesUntilExactExpiry() {
        var environment = SettingKeyTest.yamlDefaults();
        environment.getPropertySources().addFirst(new MapPropertySource("override",
                java.util.Map.of("cnhsk.billing.free-monthly-limit", "27")));
        service = new SettingsService(jdbc, environment, clock, transactions);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(27);
        clearInvocations(jdbc, transactions);
        clock.advance(29_999);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(27);
        verifyNoInteractions(jdbc, transactions);
        override(BILLING_FREE_MONTHLY_LIMIT, "42");
        clock.advance(1);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(42);
        verify(transactions).getTransaction(any());
    }

    @ParameterizedTest
    @CsvSource({"BILLING_FREE_MONTHLY_LIMIT,0", "BILLING_FREE_MONTHLY_LIMIT,1000",
            "QUIZ_QUESTION_COUNT,5", "QUIZ_QUESTION_COUNT,30"})
    void validOverridesIncludeZeroAndBothBounds(SettingKey key, String raw) {
        override(key, raw);
        assertThat(service.getInt(key)).isEqualTo(Integer.parseInt(raw));
    }

    @Test
    void cacheIsIndependentPerKeyAndInvalidationReadsNewValueImmediately() {
        override(BILLING_FREE_MONTHLY_LIMIT, "20");
        override(QUIZ_QUESTION_COUNT, "15");
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(20);
        assertThat(service.getInt(QUIZ_QUESTION_COUNT)).isEqualTo(15);
        override(BILLING_FREE_MONTHLY_LIMIT, "21");
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(20);
        service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(21);
        clearInvocations(jdbc, transactions);
        assertThat(service.getInt(QUIZ_QUESTION_COUNT)).isEqualTo(15);
        verifyNoInteractions(jdbc, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "2147483648", "-1", "1001"})
    void corruptOverridesFallBackToYaml(String raw) {
        override(BILLING_FREE_MONTHLY_LIMIT, raw);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
    }

    @Test
    void databaseFailureIsCachedAndRecoversAtExpiryOrInvalidation() {
        doThrow(new DataAccessResourceFailureException("private-sentinel"))
                .when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        clearInvocations(jdbc, transactions);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(10);
        verifyNoInteractions(jdbc, transactions);
        override(BILLING_FREE_MONTHLY_LIMIT, "25");
        clock.advance(30_000);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(25);
        override(BILLING_FREE_MONTHLY_LIMIT, "26");
        service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
        assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(26);
    }

    @Test
    void warningsAreGlobalRateLimitedAndNeverContainExceptionOrDatabaseValue() {
        Logger logger = (Logger) LoggerFactory.getLogger(SettingsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new DataAccessResourceFailureException("private-sentinel"))
                    .when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
            service.getInt(BILLING_FREE_MONTHLY_LIMIT);
            service.getInt(QUIZ_QUESTION_COUNT);
            clock.advance(59_999);
            service.invalidate(BILLING_FREE_MONTHLY_LIMIT);
            service.getInt(BILLING_FREE_MONTHLY_LIMIT);
            assertThat(appender.list).hasSize(1);
            clock.advance(1);
            override(QUIZ_QUESTION_COUNT, "database-sentinel");
            service.invalidate(QUIZ_QUESTION_COUNT);
            service.getInt(QUIZ_QUESTION_COUNT);
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).doesNotContain("sentinel");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).isNullOrEmpty();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void programmingErrorsAreNotHiddenByFallback() {
        doThrow(new IllegalStateException("programming error"))
                .when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        assertThatThrownBy(() -> service.getInt(BILLING_FREE_MONTHLY_LIMIT))
                .isInstanceOf(IllegalStateException.class).hasMessage("programming error");
    }

    @Test
    void simultaneousMissesLoadOnlyOnce() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            override(BILLING_FREE_MONTHLY_LIMIT, "44");
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return service.getInt(BILLING_FREE_MONTHLY_LIMIT);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(44);
            }
            verify(jdbc).query(anyString(), any(ResultSetExtractor.class), eq(BILLING_FREE_MONTHLY_LIMIT.key()));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidationWaitsForAnInFlightLoadBeforeClearingItsResult() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            doAnswer(call -> {
                loading.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return "40";
            }).when(jdbc).query(anyString(), any(ResultSetExtractor.class), eq(BILLING_FREE_MONTHLY_LIMIT.key()));
            Future<Integer> first = executor.submit(() -> service.getInt(BILLING_FREE_MONTHLY_LIMIT));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> invalidated = executor.submit(() -> service.invalidate(BILLING_FREE_MONTHLY_LIMIT));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(40);
            invalidated.get(5, TimeUnit.SECONDS);
            override(BILLING_FREE_MONTHLY_LIMIT, "41");
            assertThat(service.getInt(BILLING_FREE_MONTHLY_LIMIT)).isEqualTo(41);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void warmedCacheUsesNoDatabaseOrTransactionAndReportsLatency() {
        service = new SettingsService(jdbc, SettingKeyTest.yamlDefaults(), Clock.systemUTC(), transactions);
        service.getInt(BILLING_FREE_MONTHLY_LIMIT);
        for (int i = 0; i < 100_000; i++) { service.getInt(BILLING_FREE_MONTHLY_LIMIT); }
        clearInvocations(jdbc, transactions);
        long start = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) { sum += service.getInt(BILLING_FREE_MONTHLY_LIMIT); }
        long elapsed = System.nanoTime() - start;
        assertThat(sum).isEqualTo(10_000_000);
        verifyNoInteractions(jdbc, transactions);
        System.out.printf("Settings warmed cache: %.2f ns/call (%d calls)%n", elapsed / 1_000_000.0, 1_000_000);
    }

    private void override(SettingKey key, String value) {
        doReturn(value).when(jdbc).query(anyString(), any(ResultSetExtractor.class), eq(key.key()));
    }

    static final class AdjustableClock extends Clock {
        private long millis = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        void advance(long amount) { millis += amount; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
