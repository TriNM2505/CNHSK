package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.cnhsk.learning.mastery.MasteryListenerIntegrationTest.Fixtures;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MasteryRebuilderIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-09-01T08:00:00Z");
    private static final List<PracticeSource> MIXED_SOURCES = List.of(
            PracticeSource.TOPIC_TEST, PracticeSource.MOCK_EXAM, PracticeSource.PLACEMENT);

    @Autowired PracticeService practice;
    @Autowired MasteryRebuilder rebuilder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoBean(name = "topicTestHandler") PracticeSourceHandler topicTestHandler;
    @MockitoBean(name = "mockExamHandler") PracticeSourceHandler mockExamHandler;
    @MockitoBean(name = "placementHandler") PracticeSourceHandler placementHandler;

    private final AtomicReference<Instant> now = new AtomicReference<>(BASE);
    private Fixtures fixtures;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenAnswer(ignored -> now.get());
        when(topicTestHandler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(topicTestHandler.onFinish(any())).thenReturn(Map.of());
        when(mockExamHandler.source()).thenReturn(PracticeSource.MOCK_EXAM);
        when(mockExamHandler.onFinish(any())).thenReturn(Map.of());
        when(placementHandler.source()).thenReturn(PracticeSource.PLACEMENT);
        when(placementHandler.onFinish(any())).thenReturn(Map.of());
        fixtures = new Fixtures(jdbc, practice);
    }

    @Test
    void AT13_replaysFortyMixedSubmittedSessionsToTheSameFiveProgressFields() {
        long user = fixtures.user();
        long firstKid = fixtures.node();
        long secondKid = fixtures.node();
        long[] questions = IntStream.range(0, 8)
                .mapToLong(index -> fixtures.question(index % 2 == 0 ? firstKid : secondKid)).toArray();

        for (int index = 0; index < 40; index++) {
            Instant submittedAt = BASE.plusSeconds(index * 86_400L);
            now.set(submittedAt);
            long session = start(user, MIXED_SOURCES.get(index % MIXED_SOURCES.size()), questions[index % questions.length]);
            boolean correct = index % 4 != 3;
            insertGradedItem(session, questions[index % questions.length], correct, submittedAt);
            assertThat(practice.finish(user, session, List.of()).status()).isEqualTo("SUBMITTED");
        }

        Map<Long, Progress> incremental = progress(user);
        assertThat(incremental).hasSize(2);

        rebuilder.rebuild(user);
        Map<Long, Progress> rebuilt = progress(user);
        assertProgressEquals(incremental, rebuilt);

        rebuilder.rebuild(user);
        assertProgressEquals(rebuilt, progress(user));
    }

    @Test
    void E9_rebuildUsesTheQuestionLabelCurrentAtReplayTime() {
        long user = fixtures.user();
        long oldKid = fixtures.node();
        long newKid = fixtures.node();
        long question = fixtures.question(oldKid);
        Instant submittedAt = BASE.plusSeconds(1_000);
        now.set(submittedAt);
        long session = start(user, PracticeSource.TOPIC_TEST, question);
        insertGradedItem(session, question, true, submittedAt);
        practice.finish(user, session, List.of());
        Long itemId = jdbc.queryForObject("SELECT id FROM learning.attempt_items WHERE attempt_id=?", Long.class, session);

        jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=? AND knowledge_point_id=?",
                question, oldKid);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id, knowledge_point_id) VALUES (?, ?)",
                question, newKid);

        rebuilder.rebuild(user);

        assertThat(progress(user)).containsKey(newKid).doesNotContainKey(oldKid);
        assertThat(progress(user).get(newKid).evidenceItemIds()).containsExactly(itemId);
    }

    @Test
    void rebuildDeletesOnlyTheOwnersKnowledgePointRows() {
        long owner = fixtures.user();
        long otherUser = fixtures.user();
        long kid = fixtures.node();
        long otherKid = fixtures.node();
        insertProgress(owner, "KNOWLEDGE_POINT", kid);
        insertProgress(owner, "FLASHCARD", 900_001L);
        insertProgress(otherUser, "KNOWLEDGE_POINT", otherKid);
        insertProgress(otherUser, "FLASHCARD", 900_002L);

        rebuilder.rebuild(owner);

        assertThat(countProgress(owner, "KNOWLEDGE_POINT")).isZero();
        assertThat(countProgress(owner, "FLASHCARD")).isEqualTo(1);
        assertThat(countProgress(otherUser, "KNOWLEDGE_POINT")).isEqualTo(1);
        assertThat(countProgress(otherUser, "FLASHCARD")).isEqualTo(1);
    }

    @Test
    void rebuildRollsBackItsDeleteWhenReplayFails() {
        long user = fixtures.user();
        long kid = fixtures.node();
        long question = fixtures.question(kid);
        Instant submittedAt = BASE.plusSeconds(2_000);
        now.set(submittedAt);
        long session = start(user, PracticeSource.TOPIC_TEST, question);
        insertGradedItem(session, question, true, submittedAt);
        practice.finish(user, session, List.of());
        Map<Long, Progress> before = progress(user);

        jdbc.update("UPDATE learning.attempts SET served_items=?::jsonb WHERE id=?", "{\"items\":[]}", session);

        assertThatThrownBy(() -> rebuilder.rebuild(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server snapshot");
        assertProgressEquals(before, progress(user));
    }

    @Test
    void concurrentFinishAndRebuildSerializeAndASecondRebuildIsStable() throws Exception {
        long user = fixtures.user();
        long kid = fixtures.node();
        long firstQuestion = fixtures.question(kid);
        Instant firstAt = BASE.plusSeconds(3_000);
        now.set(firstAt);
        long firstSession = start(user, PracticeSource.TOPIC_TEST, firstQuestion);
        insertGradedItem(firstSession, firstQuestion, true, firstAt);
        practice.finish(user, firstSession, List.of());

        long nextQuestion = fixtures.question(kid);
        Instant nextAt = firstAt.plusSeconds(60);
        now.set(nextAt);
        long nextSession = start(user, PracticeSource.MOCK_EXAM, nextQuestion);
        insertGradedItem(nextSession, nextQuestion, false, nextAt);

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var rebuild = executor.submit(() -> {
                start.await();
                rebuilder.rebuild(user);
                return null;
            });
            var finish = executor.submit(() -> {
                start.await();
                return practice.finish(user, nextSession, List.of()).status();
            });
            start.countDown();
            rebuild.get(30, TimeUnit.SECONDS);
            assertThat(finish.get(30, TimeUnit.SECONDS)).isEqualTo("SUBMITTED");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Map<Long, Progress> afterConcurrentOperations = progress(user);
        rebuilder.rebuild(user);
        assertProgressEquals(afterConcurrentOperations, progress(user));
    }

    private long start(long user, PracticeSource source, long questionId) {
        ServedItem item = new ServedItem(1, ItemType.QUESTION_CHOICE, Skill.READING, null,
                new ServedItem.Prompt("Fixture question", null, null, null, null, null, null, null, null),
                List.of(new ServedItem.Option("A", "Fixture answer"), new ServedItem.Option("B", "Other answer")),
                "A", null, null, new ServedItem.Rules(0, 0, false), questionId);
        return practice.start(new StartPracticeCommand(user, source, null, null, List.of(item), List.of(), null,
                Map.of())).sessionId();
    }

    private void insertGradedItem(long session, long questionId, boolean correct, Instant answeredAt) {
        jdbc.update("INSERT INTO learning.attempt_items "
                        + "(attempt_id, question_id, answer, is_correct, duration_ms, order_index, answered_at, item_type, skill, meta) "
                        + "VALUES (?, ?, ?, ?, 2_000, 1, ?, 'QUESTION_CHOICE', 'READING', '{}'::jsonb)",
                session, questionId, correct ? "A" : "B", correct, Timestamp.from(answeredAt));
    }

    private Map<Long, Progress> progress(long user) {
        return jdbc.query("SELECT target_id, mastery, confidence, mp_state, evidence_item_ids, next_review_at "
                        + "FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' ORDER BY target_id",
                result -> {
                    Map<Long, Progress> rows = new java.util.LinkedHashMap<>();
                    while (result.next()) {
                        try {
                            rows.put(result.getLong("target_id"), new Progress(result.getBigDecimal("mastery"),
                                    result.getBigDecimal("confidence"), result.getString("mp_state"),
                                    Arrays.asList((Long[]) result.getArray("evidence_item_ids").getArray()),
                                    result.getTimestamp("next_review_at") == null ? null
                                            : result.getTimestamp("next_review_at").toInstant()));
                        } catch (java.sql.SQLException exception) {
                            throw new IllegalStateException("Cannot read mastery progress snapshot", exception);
                        }
                    }
                    return rows;
                }, user);
    }

    private void assertProgressEquals(Map<Long, Progress> expected, Map<Long, Progress> actual) {
        assertThat(actual.keySet()).containsExactlyElementsOf(expected.keySet());
        expected.forEach((kid, before) -> {
            Progress after = actual.get(kid);
            assertThat(after.mastery()).isEqualByComparingTo(before.mastery());
            assertThat(after.confidence()).isEqualByComparingTo(before.confidence());
            assertThat(after.state()).isEqualTo(before.state());
            assertThat(after.evidenceItemIds()).containsExactlyElementsOf(before.evidenceItemIds());
            assertThat(after.nextReviewAt()).isEqualTo(before.nextReviewAt());
        });
    }

    private void insertProgress(long user, String type, long targetId) {
        jdbc.update("INSERT INTO learning.user_progress(user_id, target_type, target_id, mp_state) "
                + "VALUES (?, ?, ?, 'notyet')", user, type, targetId);
    }

    private long countProgress(long user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type=?",
                Long.class, user, type);
    }

    private record Progress(BigDecimal mastery, BigDecimal confidence, String state,
            List<Long> evidenceItemIds, Instant nextReviewAt) { }
}
