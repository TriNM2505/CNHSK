package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Real Flyway migrations, an isolated database, and rollback for every fixture. */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Import(LeaderboardRepository.class)
class LeaderboardRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("leaderboard_test").withUsername("leaderboard_test")
            .withPassword(UUID.randomUUID().toString());

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final Interval ALL = new Interval("all", null, null);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private LeaderboardRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void FR008_game_AT02_AT03_ordersByScoreDurationSubmissionAndUserNotAttemptId() {
        long lowerUser = user(), higherUser = user(), faster = user(), earlier = user(), highScore = user(), noDuration = user();
        game(higherUser, 80L, 100, NOW);
        game(lowerUser, 80L, 100, NOW); // Reverse attempt IDs prove the final global key is user_id.
        game(faster, 80L, 90, NOW);
        game(earlier, 80L, 100, NOW.minusSeconds(1));
        game(highScore, 100L, 500, NOW);
        game(noDuration, 80L, null, NOW.minusSeconds(100));
        var result = games(null, Set.of(), ALL, 20);
        assertThat(result.entries()).extracting(Row::userId)
                .containsExactly(highScore, faster, earlier, lowerUser, higherUser, noDuration);
        assertThat(result.entries()).extracting(Row::rank).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(result.total()).isEqualTo(6);
        assertThat(result.myEntry()).isEmpty();
    }

    @Test
    void FR008_game_AT06_selectsOneBestAttemptIncludingAllTieBreaks() {
        long player = user();
        game(player, 50L, 1, NOW);
        game(player, 80L, 200, NOW);
        game(player, 70L, 1, NOW);
        game(player, 80L, 100, NOW);
        game(player, 80L, 100, NOW.minusSeconds(1));
        game(player, 80L, 100, NOW.minusSeconds(1));
        var result = games(player, Set.of(), ALL, 20);
        assertThat(result.entries()).containsExactly(new Row(1, player, 80, 100, NOW.minusSeconds(1)));
        assertThat(result.myEntry()).contains(result.entries().get(0));
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void FR008_game_AT04_AT05_filtersBeforeRankingAndHandlesEmptyBannedArray() {
        long banned = user(), suspicious = user(), regular = user();
        game(banned, 100L, 100, NOW);
        attempt(suspicious, "GAME", null, null, "CHAR_RAIN", "SUBMITTED", 200L, 1, NOW, true);
        game(regular, 80L, 100, NOW);
        assertThat(games(null, Set.of(), ALL, 20).entries()).extracting(Row::userId).containsExactly(banned, regular);
        var result = games(banned, Set.of(banned, suspicious), ALL, 20);
        assertThat(result.entries()).containsExactly(new Row(1, regular, 80, 100, NOW));
        assertThat(result.myEntry()).isEmpty();
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void FR008_game_excludesOtherGamesKindsStatusesAndNullScoreButKeepsZero() {
        long player = user();
        game(player, 0L, null, NOW);
        attempt(user(), "GAME", null, null, "CHAR_CATCH", "SUBMITTED", 999L, 1, NOW, false);
        attempt(user(), "PRACTICE", null, null, "CHAR_RAIN", "SUBMITTED", 999L, 1, NOW, false);
        for (String status : List.of("IN_PROGRESS", "ABANDONED", "EXPIRED")) {
            attempt(user(), "GAME", null, null, "CHAR_RAIN", status, 999L, 1, NOW, false);
        }
        game(user(), null, 1, NOW);
        assertThat(games(null, Set.of(), ALL, 20).entries()).containsExactly(new Row(1, player, 0, null, NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"week", "month"})
    void FR008_game_AT07_AT08_usesStartInclusiveEndExclusiveBeforeChoosingBest(String period) {
        var interval = new LeaderboardPeriods(Clock.fixed(NOW, ZoneOffset.UTC)).resolve(period);
        long player = user(), endPlayer = user(), lastPlayer = user();
        game(player, 999L, 1, interval.start().toInstant().minusSeconds(1));
        game(player, 80L, 100, interval.start().toInstant());
        game(endPlayer, 999L, 1, interval.end().toInstant());
        game(lastPlayer, 70L, 100, interval.end().toInstant().minusNanos(1000));
        var result = games(null, Set.of(), interval, 20);
        assertThat(result.entries()).extracting(Row::userId).containsExactly(player, lastPlayer);
        assertThat(result.entries()).extracting(Row::score).containsExactly(80L, 70L);
        assertThat(games(null, Set.of(), ALL, 20).total()).isEqualTo(3);
    }

    @Test
    void FR008_game_AT09_AT10_returnsRank128WithoutLimitingRankingToTop20() {
        long last = 0;
        for (int rank = 1; rank <= 128; rank++) {
            last = user();
            game(last, 1000L - rank, 100, NOW);
        }
        var result = games(last, Set.of(), ALL, 20);
        assertThat(result.entries()).hasSize(20);
        assertThat(result.entries()).extracting(Row::rank).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(result.total()).isEqualTo(128);
        assertThat(result.myEntry()).contains(new Row(128, last, 872, 100, NOW));
        assertThat(games(user(), Set.of(), ALL, 1).myEntry()).isEmpty();
        assertThat(games(null, Set.of(), ALL, 1).entries()).hasSize(1);
    }

    @Test
    void FR008_game_emptyPeriodAndViewerInTopHaveConsistentSnapshots() {
        long player = user();
        assertThat(games(player, Set.of(), ALL, 20).total()).isZero();
        assertThat(games(player, Set.of(), ALL, 20).entries()).isEmpty();
        assertThat(games(player, Set.of(), ALL, 20).myEntry()).isEmpty();
        game(player, 20L, 100, NOW);
        assertThat(games(player, Set.of(), ALL, 20).entries()).hasSize(1);
        assertThat(games(player, Set.of(), ALL, 20).myEntry()).contains(new Row(1, player, 20, 100, NOW));
    }

    @Test
    void FR008_game_invalidInputIsRejectedBeforeAnyQuery() {
        var client = mock(NamedParameterJdbcTemplate.class);
        var isolated = new LeaderboardRepository(client);
        for (int size : new int[] {0, 21}) {
            assertThatIllegalArgumentException().isThrownBy(() -> isolated.gameRanking(GameCode.CHAR_RAIN, ALL, Set.of(), null, size));
        }
        for (Set<Long> banned : Arrays.asList(null, Set.of(0L), Set.of(-1L), new HashSet<>(Arrays.asList(1L, null)))) {
            assertThatIllegalArgumentException().isThrownBy(() -> isolated.gameRanking(GameCode.CHAR_RAIN, ALL, banned, null, 20));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> isolated.gameRanking(null, ALL, Set.of(), null, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> isolated.gameRanking(GameCode.CHAR_RAIN, null, Set.of(), null, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> isolated.gameRanking(GameCode.CHAR_RAIN, ALL, Set.of(), 0L, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> isolated.topicRanking(0, ALL, Set.of(), null, 20));
        verifyNoInteractions(client);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR008_game_AT16_explainUsesGameIndexAndRestoresLocalSetting() {
        String before = jdbc.queryForObject("SHOW enable_seqscan", String.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            seedExplainAttempts();
            jdbc.execute("SET LOCAL enable_seqscan=off");
            var parameters = LeaderboardRepository.parameters(ALL, Set.of(), null, 20).addValue("gameCode", "CHAR_RAIN");
            String explain = plan(LeaderboardRepository.gameSql(ALL), parameters, false);
            System.out.println("AT16 game gate: " + explain);
            assertThat(indexNames(explain)).contains("ix_att_rank");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SHOW enable_seqscan", String.class)).isEqualTo(before);
    }

    @Test
    void FR007_topic_firstQuizOnlyAndAllEligibilityFilters() {
        long topic = topic(), player = user(), banned = user();
        quiz(player, topic, 80L, 100, NOW, false);
        quiz(banned, topic, 100L, 100, NOW, false);
        quiz(user(), topic, 999L, 100, NOW, true);
        quiz(user(), topic, null, 100, NOW, false);
        quiz(user(), topic(), 999L, 100, NOW, false);
        attempt(player, "PRACTICE", "QUIZ_RETRY", topic, null, "SUBMITTED", 999L, 1, NOW, false);
        attempt(user(), "QUIZ", "OTHER", topic, null, "SUBMITTED", 999L, 1, NOW, false);
        attempt(user(), "QUIZ", "QUIZ", topic, null, "IN_PROGRESS", 999L, 1, NOW, false);
        var result = repository.topicRanking(topic, ALL, Set.of(banned), player, 20);
        assertThat(result.entries()).containsExactly(new Row(1, player, 80, 100, NOW));
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.myEntry()).contains(result.entries().get(0));
    }

    @Test
    void FR007_topic_AT02_AT03_tiesUseUserIdAndNullDurationLast() {
        long topic = topic(), lower = user(), higher = user(), fast = user(), earlier = user(), nullDuration = user();
        quiz(higher, topic, 80L, 100, NOW, false);
        quiz(lower, topic, 80L, 100, NOW, false);
        quiz(fast, topic, 80L, 90, NOW, false);
        quiz(earlier, topic, 80L, 100, NOW.minusSeconds(1), false);
        quiz(nullDuration, topic, 80L, null, NOW.minusSeconds(2), false);
        assertThat(repository.topicRanking(topic, ALL, Set.of(), null, 20).entries())
                .extracting(Row::userId).containsExactly(fast, earlier, lower, higher, nullDuration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"week", "month"})
    void FR007_topic_AT07_AT08_filtersVietnamPeriodBoundaries(String period) {
        long topic = topic(), atStart = user(), beforeEnd = user();
        var interval = new LeaderboardPeriods(Clock.fixed(NOW, ZoneOffset.UTC)).resolve(period);
        quiz(user(), topic, 100L, 1, interval.start().toInstant().minusSeconds(1), false);
        quiz(atStart, topic, 80L, 1, interval.start().toInstant(), false);
        quiz(beforeEnd, topic, 70L, 1, interval.end().toInstant().minusNanos(1000), false);
        quiz(user(), topic, 100L, 1, interval.end().toInstant(), false);
        assertThat(repository.topicRanking(topic, interval, Set.of(), null, 20).entries())
                .extracting(Row::userId).containsExactly(atStart, beforeEnd);
    }

    @Test
    void FR007_topic_AT09_AT10_rankOutsideTopAndMissingPlayer() {
        long topic = topic(), last = 0;
        for (int rank = 1; rank <= 128; rank++) {
            last = user();
            quiz(last, topic, 1000L - rank, 100, NOW, false);
        }
        var result = repository.topicRanking(topic, ALL, Set.of(), last, 20);
        assertThat(result.entries()).hasSize(20);
        assertThat(result.myEntry()).contains(new Row(128, last, 872, 100, NOW));
        assertThat(result.total()).isEqualTo(128);
        assertThat(repository.topicRanking(topic, ALL, Set.of(), user(), 20).myEntry()).isEmpty();
        assertThat(repository.topicRanking(topic(), ALL, Set.of(), null, 20).total()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR007_topic_AT16_explainUsesV8IndexAndRestoresLocalSetting() {
        String before = jdbc.queryForObject("SHOW enable_seqscan", String.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            long topic = seedExplainAttempts();
            var interval = new LeaderboardPeriods(Clock.fixed(NOW, ZoneOffset.UTC)).resolve("week");
            jdbc.execute("SET LOCAL enable_seqscan=off");
            var parameters = LeaderboardRepository.parameters(interval, Set.of(), null, 20).addValue("topicId", topic);
            String explain = plan(LeaderboardRepository.topicSql(interval), parameters, false);
            System.out.println("AT16 topic gate: " + explain);
            assertThat(indexNames(explain)).contains("ix_att_ref_rank");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SHOW enable_seqscan", String.class)).isEqualTo(before);
    }

    @Test
    void AT16_tenThousandAttemptsDiagnosticRollsBackAndDoesNotAssertTiming() {
        long topic = seedExplainAttempts();
        var interval = new LeaderboardPeriods(Clock.fixed(NOW, ZoneOffset.UTC)).resolve("week");
        long started = System.nanoTime();
        System.out.println("AT16 topic diagnostic: " + plan(LeaderboardRepository.topicSql(interval),
                LeaderboardRepository.parameters(interval, Set.of(), null, 20).addValue("topicId", topic), true));
        System.out.println("AT16 game diagnostic: " + plan(LeaderboardRepository.gameSql(interval),
                LeaderboardRepository.parameters(interval, Set.of(), null, 20).addValue("gameCode", "CHAR_RAIN"), true));
        System.out.println("AT16 diagnostic elapsed ms=" + (System.nanoTime() - started) / 1_000_000);
    }

    private long seedExplainAttempts() {
        Long[] users = new Long[200];
        Long[] topics = new Long[20];
        for (int i = 0; i < users.length; i++) users[i] = user();
        for (int i = 0; i < topics.length; i++) topics[i] = topic();
        named.update("""
                INSERT INTO learning.attempts(user_id, kind, ref_type, ref_id, game_code,
                    status, score, duration_ms, submitted_at, suspicious)
                SELECT (CAST(:users AS bigint[]))[1 + (n - 1) %% 200],
                       CASE WHEN n <= 4000 THEN 'QUIZ' ELSE 'GAME' END,
                       CASE WHEN n <= 4000 THEN 'QUIZ' END,
                       CASE WHEN n <= 4000 THEN (CAST(:topics AS bigint[]))[1 + (n - 1) / 200] END,
                       CASE WHEN n > 4000 THEN (CAST(:games AS varchar[]))[1 + (n - 1) %% 6] END,
                       'SUBMITTED', n %% 100, n %% 1000, :at, false
                FROM generate_series(1, 10000) AS n
                """.replace("%%", "%"), new MapSqlParameterSource()
                .addValue("users", new SqlArrayValue("bigint", (Object[]) users))
                .addValue("topics", new SqlArrayValue("bigint", (Object[]) topics))
                .addValue("games", new SqlArrayValue("varchar", (Object[]) Arrays.stream(GameCode.values()).map(Enum::name).toArray(String[]::new)))
                .addValue("at", Timestamp.from(NOW)));
        jdbc.execute("ANALYZE learning.attempts");
        return topics[0];
    }

    private LeaderboardRepository.Snapshot games(Long viewer, Set<Long> banned, Interval interval, int size) {
        return repository.gameRanking(GameCode.CHAR_RAIN, interval, banned, viewer, size);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Leaderboard fixture");
    }

    private long topic() {
        return jdbc.queryForObject("INSERT INTO learning.topics(code, name, hsk_level) VALUES (?, ?, 1) RETURNING id",
                Long.class, "LB_" + UUID.randomUUID().toString().replace("-", ""), "Leaderboard fixture");
    }

    private void game(long user, Long score, Integer duration, Instant submitted) {
        attempt(user, "GAME", null, null, "CHAR_RAIN", "SUBMITTED", score, duration, submitted, false);
    }

    private void quiz(long user, long topic, Long score, Integer duration, Instant submitted, boolean suspicious) {
        attempt(user, "QUIZ", "QUIZ", topic, null, "SUBMITTED", score, duration, submitted, suspicious);
    }

    private void attempt(long user, String kind, String refType, Long refId, String game, String status,
            Long score, Integer duration, Instant submitted, boolean suspicious) {
        jdbc.update("""
                INSERT INTO learning.attempts(user_id, kind, ref_type, ref_id, game_code, status,
                    score, duration_ms, submitted_at, suspicious) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, user, kind, refType, refId, game, status, score, duration, Timestamp.from(submitted), suspicious);
    }

    private String plan(String sql, MapSqlParameterSource parameters, boolean analyze) {
        return named.queryForObject((analyze ? "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " : "EXPLAIN (FORMAT JSON) ") + sql,
                parameters, String.class);
    }

    private static Set<String> indexNames(String json) {
        try {
            var names = new HashSet<String>();
            collectIndexes(new ObjectMapper().readTree(json), names);
            return names;
        } catch (java.io.IOException exception) {
            throw new AssertionError("Invalid EXPLAIN JSON", exception);
        }
    }

    private static void collectIndexes(JsonNode node, Set<String> names) {
        if (node.has("Index Name")) names.add(node.get("Index Name").asText());
        node.forEach(child -> collectIndexes(child, names));
    }
}
