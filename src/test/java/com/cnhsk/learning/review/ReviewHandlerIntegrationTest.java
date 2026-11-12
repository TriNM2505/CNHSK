package com.cnhsk.learning.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.mastery.MasteryUpdater;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class ReviewHandlerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

    @Autowired PracticeService practice;
    @Autowired JdbcTemplate jdbc;
    @Autowired FsrsConfig config;
    @MockitoBean Clock clock;
    @MockitoSpyBean MasteryUpdater mastery;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void AT23_flashcardRatingUpdatesFsrsButDoesNotCreateMasteryEvidence() {
        long user = user();
        long lexeme = lexeme("WORD", null);
        seed(user, lexeme, 0);
        long session = flashcardSession(user, lexeme);

        practice.answer(user, session, answer(1, "EASY"));

        var row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).review(
                new FsrsScheduler.Memory(new BigDecimal("2.000"), new BigDecimal("5.00"),
                        NOW.minusSeconds(10L * 86_400), 0), FsrsScheduler.Rating.EASY, NOW);
        assertThat((BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, RoundingMode.HALF_UP));
        assertThat(((BigDecimal) row.get("stability")).scale()).isEqualTo(3);
        assertThat(((BigDecimal) row.get("difficulty")).scale()).isEqualTo(2);
        assertThat(row.get("review_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? "
                + "AND target_type='KNOWLEDGE_POINT'", Long.class, user)).isZero();
    }

    @Test
    void AT23_choiceAnswerUpdatesFsrsAndWritesEvidenceInTheSameAnswerTransaction() {
        long user = user();
        long kid = kid();
        long lexeme = lexeme("WORD", kid);
        seed(user, lexeme, 0);
        long session = choiceSession(user, lexeme);

        practice.answer(user, session, answer(1, "A"));

        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(1);
        Long[] evidence = jdbc.queryForObject("SELECT evidence_item_ids FROM learning.user_progress "
                + "WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=?",
                (row, index) -> (Long[]) row.getArray(1).getArray(), user, kid);
        assertThat(evidence).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE id=? AND is_correct=true",
                Long.class, evidence[0])).isEqualTo(1);
    }

    @Test
    void invalidFlashcardRatingIsRejectedBeforeAttemptItemIsPersisted() {
        long user = user();
        long lexeme = lexeme("GRAMMAR", null);
        seed(user, lexeme, 0);
        long session = flashcardSession(user, lexeme);

        assertThatThrownBy(() -> practice.answer(user, session, answer(1, "PERFECT")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus().value()).isEqualTo(422));
        assertThat(countItems(session)).isZero();
        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(0);
    }

    @Test
    void skippedChoiceAndFlashcardAnswersDoNotChangeFsrsOrMastery() {
        long user = user();
        long choiceLexeme = lexeme("WORD", null);
        long flashcardLexeme = lexeme("GRAMMAR", null);
        seed(user, choiceLexeme, 0);
        seed(user, flashcardLexeme, 0);
        long choiceSession = choiceSession(user, choiceLexeme);
        long cardSession = flashcardSession(user, flashcardLexeme);

        practice.answer(user, choiceSession, new Answer(1, null, 0, 0, true, false, Map.of()));
        practice.answer(user, cardSession, new Answer(1, null, 0, 0, true, false, Map.of()));

        assertThat(schedule(user, choiceLexeme).get("review_count")).isEqualTo(0);
        assertThat(schedule(user, flashcardLexeme).get("review_count")).isEqualTo(0);
        assertThat(countItems(choiceSession)).isEqualTo(1);
        assertThat(countItems(cardSession)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? "
                + "AND target_type='KNOWLEDGE_POINT'", Long.class, user)).isZero();
    }

    @Test
    void listenerFailureRollsBackTheAnswerItemAndScheduleUpdate() {
        long user = user();
        long kid = kid();
        long lexeme = lexeme("WORD", kid);
        seed(user, lexeme, 0);
        long session = choiceSession(user, lexeme);
        MasteryUpdater target = AopTestUtils.getUltimateTargetObject(mastery);
        doThrow(new IllegalStateException("fixture listener failure")).when(target).update(anyLong(), anyList());

        assertThatThrownBy(() -> practice.answer(user, session, answer(1, "A")))
                .isInstanceOf(IllegalStateException.class).hasMessage("fixture listener failure");

        assertThat(countItems(session)).isZero();
        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(0);
    }

    @Test
    void twoConcurrentAnswersForOneLexemeDoNotLoseScheduleUpdates() throws Exception {
        long user = user();
        long lexeme = lexeme("GRAMMAR", null);
        seed(user, lexeme, 0);
        long first = flashcardSession(user, lexeme);
        long second = flashcardSession(user, lexeme);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> answerTogether(user, first, ready, go));
            var b = pool.submit(() -> answerTogether(user, second, ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(2);
    }

    @Test
    void finishingAfterImmediateAnswersDoesNotApplyTheReviewAgain() {
        long user = user();
        long lexeme = lexeme("GRAMMAR", null);
        seed(user, lexeme, 0);
        long session = flashcardSession(user, lexeme);
        practice.answer(user, session, answer(1, "HARD"));
        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(1);

        practice.finish(user, session, List.of());

        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(1);
    }

    private void answerTogether(long user, long session, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent answer not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent answer interrupted", exception);
        }
        practice.answer(user, session, answer(1, "GOOD"));
    }

    private long flashcardSession(long user, long lexeme) {
        var item = new ServedItem(1, ItemType.FLASHCARD_RATING, Skill.GRAMMAR, lexeme,
                new ServedItem.Prompt("自作句型", null, null, "tự soạn nghĩa", null, null, null, null, "GRAMMAR"),
                null, null, null, null, new ServedItem.Rules(0, 0, false));
        return practice.start(new StartPracticeCommand(user, PracticeSource.DUE_REVIEW, null, null,
                List.of(item), List.of(), null, Map.of())).sessionId();
    }

    private long choiceSession(long user, long lexeme) {
        var item = new ServedItem(1, ItemType.MEANING_CHOICE, Skill.VOCAB, lexeme,
                new ServedItem.Prompt("自作単語", null, null, "tự soạn nghĩa", null, null, null, null, "WORD"),
                List.of(new ServedItem.Option("A", "đúng tự soạn"), new ServedItem.Option("B", "sai tự soạn")),
                "A", null, null, new ServedItem.Rules(0, 0, false));
        return practice.start(new StartPracticeCommand(user, PracticeSource.DUE_REVIEW, null, null,
                List.of(item), List.of(), null, Map.of())).sessionId();
    }

    private Answer answer(int no, String value) {
        return new Answer(no, value, 0, 0, false, false, Map.of());
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'T14 fixture') RETURNING id",
                Long.class, "t14-handler-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
    }

    private long lexeme(String kind, Long kid) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi,knowledge_point_id) "
                + "VALUES (?,?,?,?) RETURNING id", Long.class, kind, "self-made-" + UUID.randomUUID(),
                "nghĩa tự soạn", kid);
    }

    private long kid() {
        List<Integer> available = jdbc.queryForList("SELECT n FROM generate_series(0,999) n WHERE NOT EXISTS "
                + "(SELECT 1 FROM learning.knowledge_points WHERE code='NL6-' || lpad(n::text,3,'0')) "
                + "ORDER BY n LIMIT 1", Integer.class);
        if (available.isEmpty()) throw new IllegalStateException("No unused NL6 fixture code remains");
        String code = String.format(Locale.ROOT, "NL6-%03d", available.get(0));
        return jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                + "VALUES (?,'Self-authored fixture node','NL','自',6) RETURNING id", Long.class, code);
    }

    private void seed(long user, long lexeme, int reviewCount) {
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,2,5,?,0,?,?)",
                user, lexeme, reviewCount, java.sql.Timestamp.from(NOW.minusSeconds(10L * 86_400)),
                java.sql.Timestamp.from(NOW.minusSeconds(60)));
    }

    private Map<String, Object> schedule(long user, long lexeme) {
        return jdbc.queryForMap("SELECT stability,difficulty,review_count,next_review_at,last_seen_at FROM "
                + "learning.user_progress WHERE user_id=? AND target_type='LEXEME' AND target_id=?", user, lexeme);
    }

    private long countItems(long session) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, session);
    }
}
