package com.cnhsk.learning.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;

class ReviewSessionServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    @Autowired ReviewSessionService reviews;
    @Autowired PracticeService practice;
    @Autowired AttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlCapture sqlCapture;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void AT21_summaryCountsOnlyDueSchedulesWithExistingLexemesAndReturnsNextFutureDate() {
        long user = user();
        long firstFuture = lexeme("future-a");
        long secondFuture = lexeme("future-b");
        seed(user, lexeme("due-a"), NOW.minusSeconds(86_400));
        seed(user, lexeme("due-b"), NOW.minusSeconds(172_800));
        seed(user, lexeme("due-c"), NOW);
        seed(user, firstFuture, NOW.plusSeconds(86_400));
        seed(user, secondFuture, NOW.plusSeconds(172_800));
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,?,?,?,0,?,?)",
                user, Long.MAX_VALUE - user, 1, 5, 0, java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW.minusSeconds(1)));

        var summary = reviews.summary(user);

        assertThat(summary.dueCount()).isEqualTo(3);
        assertThat(summary.nextDueAt()).isEqualTo(NOW.plusSeconds(86_400));
    }

    @Test
    void AT22_ordersOldestDueThenTargetIdAndReusesActiveSessionRegardlessOfAhead() {
        long user = user();
        long equalDueA = lexeme("equal-a");
        long equalDueB = lexeme("equal-b");
        long oldest = lexeme("oldest");
        long future = lexeme("future");
        Instant equalDue = NOW.minusSeconds(86_400);
        seed(user, equalDueA, equalDue);
        seed(user, equalDueB, equalDue);
        seed(user, oldest, NOW.minusSeconds(172_800));
        seed(user, future, NOW.plusSeconds(86_400));

        var created = reviews.start(user, true);
        assertThat(created.created()).isTrue();
        assertThat(created.session().items()).extracting(item -> item.lexemeId())
                .containsExactly(oldest, equalDueA, equalDueB, future);
        assertThat(created.session().items()).allMatch(item -> item.type().name().equals("FLASHCARD_RATING"));

        var resumed = reviews.start(user, false);
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.session().sessionId()).isEqualTo(created.session().sessionId());
    }

    @Test
    void AT22_limitsAheadCandidatesAndLexemeLoadToConfiguredFiftyItems() {
        long user = user();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            long id = lexeme("limit-" + index);
            ids.add(id);
            seed(user, id, NOW.minusSeconds(60L * (60 - index)));
        }

        sqlCapture.start();
        var result = reviews.start(user, true);
        List<String> statements = sqlCapture.stop();

        assertThat(result.created()).isTrue();
        assertThat(result.session().items()).hasSize(50);
        assertThat(result.session().items().get(0).lexemeId()).isEqualTo(ids.get(0));
        assertThat(result.session().items().get(49).lexemeId()).isEqualTo(ids.get(49));
        List<String> candidateQueries = statements.stream()
                .filter(sql -> sql.startsWith("select p.target_id from learning.user_progress p join learning.lexemes l"))
                .toList();
        assertThat(candidateQueries).hasSize(1);
        assertThat(candidateQueries.get(0)).contains("limit ?");
    }

    @Test
    void orphanedScheduleIsSkippedAndDoesNotPreventAValidItemFromBeingServed() {
        long user = user();
        long valid = lexeme("valid");
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,?,?,?,0,?,?)",
                user, Long.MAX_VALUE - user, 1, 5, 0, java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW.minusSeconds(7_200)));
        seed(user, valid, NOW.minusSeconds(3_600));

        var result = reviews.start(user, false);

        assertThat(result.session().items()).extracting(item -> item.lexemeId()).containsExactly(valid);
    }

    @Test
    void concurrentStartRequestsCreateOneAttemptAndShareItsSessionId() throws Exception {
        long user = user();
        seed(user, lexeme("concurrent"), NOW.minusSeconds(3_600));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> startTogether(user, ready, start));
            var second = pool.submit(() -> startTogether(user, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.session().sessionId(), b.session().sessionId()).stream().distinct()).hasSize(1);
            assertThat(List.of(a.created(), b.created()).stream().filter(Boolean::booleanValue)).hasSize(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=? "
                + "AND ref_type='DUE_REVIEW'", Long.class, user)).isEqualTo(1);
    }

    @Test
    void NFRP04_measuresFiftyChoiceItemsP95AndSelectCount() {
        List<Long> lexemeIds = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            lexemeIds.add(word("perf-" + index));
        }
        List<Long> durationsMillis = new ArrayList<>();
        List<Integer> selectCounts = new ArrayList<>();

        for (int sample = -1; sample < 10; sample++) {
            long user = user();
            for (long lexemeId : lexemeIds.subList(0, 50)) {
                seed(user, lexemeId, NOW.minusSeconds(3_600));
            }
            sqlCapture.start();
            long startedAt = System.nanoTime();
            var result = reviews.start(user, false);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            List<String> statements = sqlCapture.stop();
            assertThat(result.session().items()).hasSize(50);
            int selects = (int) statements.stream().filter(SqlCapture::isSelect).count();
            if (sample >= 0) {
                durationsMillis.add(elapsedMillis);
                selectCounts.add(selects);
            }
        }

        durationsMillis.sort(Long::compareTo);
        long p95 = durationsMillis.get((int) Math.ceil(durationsMillis.size() * 0.95) - 1);
        System.out.printf(Locale.ROOT, "NFR-P04 50 WORD items: p95=%d ms, SELECT/sample=%s%n", p95, selectCounts);
        assertThat(p95).isLessThan(500);
    }

    private ReviewSessionService.StartResult startTogether(long user, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent start test was not released");
        }
        return reviews.start(user, true);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'T14 fixture') RETURNING id",
                Long.class, "t14-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
    }

    private long lexeme(String label) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('GRAMMAR',?,?) RETURNING id",
                Long.class, "fixture-" + label + "-" + UUID.randomUUID(), "meaning-" + label);
    }

    private long word(String label) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD',?,?) RETURNING id",
                Long.class, "fixture-word-" + label + "-" + UUID.randomUUID(), "unique meaning " + label);
    }

    private void seed(long user, long lexeme, Instant dueAt) {
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,1,5,0,0,?,?)",
                user, lexeme, java.sql.Timestamp.from(NOW.minusSeconds(86_400)), java.sql.Timestamp.from(dueAt));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCaptureConfiguration {
        @Bean static SqlCapture reviewSessionSqlCapture() { return new SqlCapture(); }

        @Bean static BeanPostProcessor reviewSessionDataSourceCapture(SqlCapture capture) {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource dataSource)) return bean;
                    return new DelegatingDataSource(dataSource) {
                        @Override public Connection getConnection() throws SQLException {
                            return capture.wrap(super.getConnection());
                        }
                        @Override public Connection getConnection(String username, String password) throws SQLException {
                            return capture.wrap(super.getConnection(username, password));
                        }
                    };
                }
            };
        }
    }

    static final class SqlCapture {
        private final ThreadLocal<List<String>> statements = new ThreadLocal<>();
        void start() { statements.set(new ArrayList<>()); }
        List<String> stop() {
            List<String> result = statements.get();
            statements.remove();
            return result == null ? List.of() : List.copyOf(result);
        }
        Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (statements.get() != null && method.getName().startsWith("prepare")
                                && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                            statements.get().add(sql.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
                        }
                        try { return method.invoke(connection, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        }
        static boolean isSelect(String sql) {
            String normalized = sql.stripLeading();
            return normalized.startsWith("select") || normalized.startsWith("with");
        }
    }
}
