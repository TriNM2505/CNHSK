package com.cnhsk.learning.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.ServedItem.Option;
import com.cnhsk.learning.practice.ServedItem.Prompt;
import com.cnhsk.learning.practice.ServedItem.Rules;
import com.cnhsk.support.AbstractIntegrationTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ReviewListenerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");

    @Autowired PracticeService practice;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlCapture sqlCapture;
    @Autowired FsrsConfig config;
    @MockitoSpyBean JdbcTemplate jdbcSpy;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);

    @BeforeEach
    void setup() {
        currentTime.set(NOW);
        when(clock.instant()).thenAnswer(invocation -> currentTime.get());
    }

    @Test
    void AT20_topicStudyLearnedCreatesGoodFirstScheduleAndDoesNotWriteMastery() {
        long user = user();
        long lexeme = lexeme();
        long session = studySession(user, lexeme);
        answerStudy(user, session, 1, "LEARNED", 1_200);

        practice.finish(user, session, List.of());

        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).first(FsrsScheduler.Rating.GOOD, NOW);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
        assertThat(row.get("next_review_at").toString()).contains("2026-11-10");
        assertThat(row.get("review_count")).isEqualTo(0);
        assertThat((java.math.BigDecimal) row.get("mastery")).isEqualByComparingTo("0.000");
        assertThat(row.get("mp_state")).isNull();
    }

    @Test
    void AT20_topicStudyKnownCreatesEasyFirstSchedule() {
        long user = user();
        long lexeme = lexeme();
        long session = studySession(user, lexeme);
        answerStudy(user, session, 1, "KNOWN", 1_200);

        practice.finish(user, session, List.of());

        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).first(FsrsScheduler.Rating.EASY, NOW);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
        assertThat(row.get("next_review_at").toString()).contains("2026-11-23");
    }

    @Test
    void learnedWinsWhenFirstTopicStudyAttemptHasLearnedAndKnownForSameLexeme() {
        long user = user();
        long lexeme = lexeme();
        long session = studySession(user, lexeme, lexeme);
        answerStudy(user, session, 1, "KNOWN", 1_200);
        answerStudy(user, session, 2, "LEARNED", 1_200);

        practice.finish(user, session, List.of());

        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).first(FsrsScheduler.Rating.GOOD, NOW);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
        assertThat(row.get("review_count")).isEqualTo(0);
    }

    @Test
    void gradedCorrectCreatesGoodAndGradedIncorrectCreatesAgain() {
        long user = user();
        long correctLexeme = lexeme();
        long incorrectLexeme = lexeme();
        long correctQuestion = question();
        long incorrectQuestion = question();
        long session = gradedSession(user,
                List.of(questionItem(1, correctLexeme, correctQuestion), questionItem(2, incorrectLexeme, incorrectQuestion)));
        answerQuestion(user, session, 1, "A", 1_500);
        answerQuestion(user, session, 2, "B", 1_500);

        practice.finish(user, session, List.of());

        assertFirstResult(user, correctLexeme, FsrsScheduler.Rating.GOOD);
        assertFirstResult(user, incorrectLexeme, FsrsScheduler.Rating.AGAIN);
    }

    @Test
    void validGradedWrongAnswerWinsForOneLexemeAndCreatesOnlyOneFirstReview() {
        long user = user();
        long lexeme = lexeme();
        long session = gradedSession(user, List.of(questionItem(1, lexeme, question()),
                questionItem(2, lexeme, question())));
        answerQuestion(user, session, 1, "A", 1_500);
        answerQuestion(user, session, 2, "B", 1_500);

        practice.finish(user, session, List.of());

        assertFirstResult(user, lexeme, FsrsScheduler.Rating.AGAIN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? "
                + "AND target_type='LEXEME' AND target_id=?", Long.class, user, lexeme)).isEqualTo(1);
    }

    @Test
    void existingScheduleUsesGoodForTopicStudyAndIncrementsReviewCountOnce() {
        long user = user();
        long lexeme = lexeme();
        seedSchedule(user, lexeme, 2.000, 5.00, 4, NOW.minusSeconds(172_800));
        long session = studySession(user, lexeme);
        answerStudy(user, session, 1, "KNOWN", 1_200);

        practice.finish(user, session, List.of());

        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).review(
                new FsrsScheduler.Memory(new java.math.BigDecimal("2.000"), new java.math.BigDecimal("5.00"),
                        NOW.minusSeconds(172_800), 0), FsrsScheduler.Rating.GOOD, NOW);
        assertThat(row.get("review_count")).isEqualTo(5);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
        assertThat(((java.sql.Timestamp) row.get("last_seen_at")).toInstant()).isEqualTo(NOW);
        assertThat(((java.sql.Timestamp) row.get("next_review_at")).toInstant()).isEqualTo(expected.nextReviewAt());
    }

    @Test
    void existingGradedScheduleUsesAgainWhenAnyValidAnswerIsWrong() {
        long user = user();
        long lexeme = lexeme();
        seedSchedule(user, lexeme, 2.000, 5.00, 4, NOW.minusSeconds(172_800));
        long session = gradedSession(user, List.of(questionItem(1, lexeme, question()),
                questionItem(2, lexeme, question())));
        answerQuestion(user, session, 1, "A", 1_500);
        answerQuestion(user, session, 2, "B", 1_500);

        practice.finish(user, session, List.of());

        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).review(
                new FsrsScheduler.Memory(new java.math.BigDecimal("2.000"), new java.math.BigDecimal("5.00"),
                        NOW.minusSeconds(172_800), 0), FsrsScheduler.Rating.AGAIN, NOW);
        assertThat(row.get("review_count")).isEqualTo(5);
        assertThat(row.get("lapse_count")).isEqualTo(1);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void topicStudyWithSuspiciousMetadataDoesNotCreateSchedule() {
        long user = user();
        long lexeme = lexeme();
        long session = studySession(user, lexeme);
        answerStudy(user, session, 1, "LEARNED", 500);

        practice.finish(user, session, List.of());

        assertThat(scheduleCount(user, lexeme)).isZero();
    }

    @Test
    void gradedArchivedQuestionIsExcludedByFR008() {
        long user = user();
        long lexeme = lexeme();
        long question = question();
        jdbc.update("UPDATE learning.questions SET status='ARCHIVED' WHERE id=?", question);
        long session = gradedSession(user, List.of(questionItem(1, lexeme, question)));
        answerQuestion(user, session, 1, "A", 1_500);

        practice.finish(user, session, List.of());

        assertThat(scheduleCount(user, lexeme)).isZero();
    }

    @Test
    void invalidItemMetadataDoesNotDiscardOtherValidEvidenceInSameSession() {
        long user = user();
        List<Long> lexemes = List.of(lexeme(), lexeme(), lexeme(), lexeme());
        List<ServedItem> served = new ArrayList<>();
        for (int index = 0; index < lexemes.size(); index++) {
            served.add(questionItem(index + 1, lexemes.get(index), question()));
        }
        long session = gradedSession(user, served);
        answerQuestion(user, session, 1, "A", 500);
        for (int index = 2; index <= lexemes.size(); index++) answerQuestion(user, session, index, "A", 1_500);

        practice.finish(user, session, List.of());

        assertThat(scheduleCount(user, lexemes.get(0))).isZero();
        assertThat(scheduleCount(user, lexemes.get(1))).isEqualTo(1);
        assertThat(scheduleCount(user, lexemes.get(2))).isEqualTo(1);
        assertThat(scheduleCount(user, lexemes.get(3))).isEqualTo(1);
    }

    @Test
    void priorSameDayTopicStudyDoesNotApplyTheMasterySevenDayDuplicateRule() {
        long user = user();
        long lexeme = lexeme();
        long first = studySession(user, lexeme);
        answerStudy(user, first, 1, "LEARNED", 1_200);
        practice.finish(user, first, List.of());
        long second = studySession(user, lexeme);
        answerStudy(user, second, 1, "KNOWN", 1_200);

        practice.finish(user, second, List.of());

        assertThat(schedule(user, lexeme).get("review_count")).isEqualTo(1);
    }

    @Test
    void listenerFailureRollsBackTheFinishTransaction() {
        long user = user();
        long lexeme = lexeme();
        long session = deferredGradedSession(user, List.of(questionItem(1, lexeme, question())));
        answerQuestion(user, session, 1, "A", 1_500);
        currentTime.set(NOW.plusSeconds(3));
        doThrow(new IllegalStateException("injected review persistence failure"))
                .when(jdbcSpy).batchUpdate(startsWith("UPDATE learning.user_progress SET stability"), anyList());

        assertThatThrownBy(() -> practice.finish(user, session, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected review persistence failure");

        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, session))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, session))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, user))
                .isZero();
        assertThat(scheduleCount(user, lexeme)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? "
                + "AND target_type='KNOWLEDGE_POINT'", Long.class, user)).isZero();
    }

    @Test
    void concurrentFinishesForSameUserAndLexemeDoNotLoseReview() throws Exception {
        long user = user();
        long lexeme = lexeme();
        long first = gradedSession(user, List.of(questionItem(1, lexeme, question())));
        long second = gradedSession(user, List.of(questionItem(1, lexeme, question())));
        answerQuestion(user, first, 1, "A", 1_500);
        answerQuestion(user, second, 1, "B", 1_500);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { start.await(); return practice.finish(user, first, List.of()); });
            var two = executor.submit(() -> { start.await(); return practice.finish(user, second, List.of()); });
            start.countDown();
            one.get(15, TimeUnit.SECONDS);
            two.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Map<String, Object> row = schedule(user, lexeme);
        assertThat(row.get("review_count")).isEqualTo(1);
        assertThat(((java.sql.Timestamp) row.get("last_seen_at")).toInstant()).isEqualTo(NOW);
    }

    @Test
    // Use one KID to measure ReviewListener query growth by item count, not MasteryUpdater cost by KID.
    void selectCountIsIndependentOfTenOrFiftyItems() {
        long sharedLexeme = lexeme();
        long sharedQuestion = question();
        QueryCounts ten = measuredFinish(10, sharedLexeme, sharedQuestion);
        QueryCounts fifty = measuredFinish(50, sharedLexeme, sharedQuestion);

        assertThat(fifty.total()).isEqualTo(ten.total());
        assertThat(ten.statementInspector()).isEqualTo(fifty.statementInspector());
        System.out.println("T13 SELECT count 10 items=" + ten + ", 50 items=" + fifty);
    }

    private QueryCounts measuredFinish(int count, long lexeme, long question) {
        long user = user();
        List<ServedItem> served = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            served.add(questionItem(index, lexeme, question));
        }
        long session = gradedSession(user, served);
        for (int index = 1; index <= count; index++) answerQuestion(user, session, index, "A", 1_500);
        sqlCapture.start();
        clearInvocations(jdbcSpy);
        practice.finish(user, session, List.of());
        List<String> inspected = sqlCapture.stop();
        long inspectorReads = inspected.stream().filter(ReviewListenerIntegrationTest::isSelect).count();
        long jdbcReads = mockingDetails(jdbcSpy).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().startsWith("query"))
                .map(invocation -> invocation.getArgument(0))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(ReviewListenerIntegrationTest::isSelect).count();
        return new QueryCounts(inspectorReads, jdbcReads);
    }

    private long studySession(long user, long... lexemes) {
        List<ServedItem> served = new ArrayList<>();
        for (int index = 0; index < lexemes.length; index++) {
            served.add(new ServedItem(index + 1, ItemType.STUDY_CARD, Skill.VOCAB, lexemes[index],
                    new Prompt("fixture", null, null, "fixture meaning", null, null, null, null, "WORD"),
                    null, null, null, null, new Rules(0, 2, false), null));
        }
        return practice.start(new StartPracticeCommand(user, PracticeSource.TOPIC_STUDY, 1L, null,
                served, List.of(), null, Map.of())).sessionId();
    }

    private long gradedSession(long user, List<ServedItem> served) {
        return practice.start(new StartPracticeCommand(user, PracticeSource.TOPIC_PRACTICE, 1L, null,
                served, List.of(), null, Map.of())).sessionId();
    }

    private long deferredGradedSession(long user, List<ServedItem> served) {
        return practice.start(new StartPracticeCommand(user, PracticeSource.TOPIC_TEST, 1L, null,
                served, List.of(), null, Map.of())).sessionId();
    }

    private void answerStudy(long user, long session, int no, String answer, int duration) {
        practice.answer(user, session, new Answer(no, answer, duration, 0, false, false, Map.of()));
    }

    private void answerQuestion(long user, long session, int no, String answer, int duration) {
        practice.answer(user, session, new Answer(no, answer, duration, 0, false, false, Map.of()));
    }

    private ServedItem questionItem(int no, long lexeme, long question) {
        return new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, lexeme,
                new Prompt("fixture question", null, null, null, null, null, null, null, null),
                List.of(new Option("A", "self-authored correct"), new Option("B", "self-authored other")),
                "A", null, "self-authored explanation", new Rules(1_000, 0, false), question);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'T13 fixture') RETURNING id",
                Long.class, "t13-" + UUID.randomUUID() + "@example.test",
                "$2a$12$fixtureTestHashForDatabaseOnly00000000000000000000000000");
    }

    private long lexeme() {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi) VALUES ('WORD',?,'Fixture meaning') RETURNING id",
                Long.class, "T13 fixture " + UUID.randomUUID());
    }

    private long question() {
        long question = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,status,code) "
                        + "VALUES ('MULTIPLE_CHOICE',1,'Self-authored fixture',"
                        + "'[{\"key\":\"A\",\"text\":\"self-authored correct\",\"is_correct\":true},"
                        + "{\"key\":\"B\",\"text\":\"self-authored other\",\"is_correct\":false}]'::jsonb,"
                        + "'A','APPROVED',?) RETURNING id", Long.class, "T13-" + UUID.randomUUID());
        List<Integer> availableKidNumbers = jdbc.queryForList("SELECT n FROM generate_series(0,999) n "
                + "WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points "
                + "WHERE code = 'NL6-' || lpad(n::text,3,'0')) ORDER BY n LIMIT 1", Integer.class);
        if (availableKidNumbers.isEmpty()) {
            throw new IllegalStateException("No unused NL6 fixture KID code remains");
        }
        String kidCode = String.format(Locale.ROOT, "NL6-%03d", availableKidNumbers.get(0));
        long fixtureKid = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                + "VALUES (?,'Self-authored fixture node','NL','字',6) RETURNING id", Long.class, kidCode);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id) VALUES (?,?)",
                question, fixtureKid);
        return question;
    }

    private void seedSchedule(long user, long lexeme, double stability, double difficulty,
            int reviewCount, Instant lastSeenAt) {
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                        + "review_count,lapse_count,last_seen_at) VALUES (?,'LEXEME',?,?,?, ?,0,?)",
                user, lexeme, stability, difficulty, reviewCount, java.sql.Timestamp.from(lastSeenAt));
    }

    private void assertFirstResult(long user, long lexeme, FsrsScheduler.Rating rating) {
        Map<String, Object> row = schedule(user, lexeme);
        var expected = new FsrsScheduler(config.settings()).first(rating, NOW);
        assertThat((java.math.BigDecimal) row.get("stability"))
                .isEqualByComparingTo(expected.stability().setScale(3, java.math.RoundingMode.HALF_UP));
        assertThat(((java.math.BigDecimal) row.get("stability")).scale()).isEqualTo(3);
        assertThat(((java.math.BigDecimal) row.get("difficulty")).scale()).isEqualTo(2);
    }

    private Map<String, Object> schedule(long user, long lexeme) {
        return jdbc.queryForMap("SELECT mastery, stability, difficulty, next_review_at, review_count, lapse_count, "
                + "last_seen_at, mp_state FROM learning.user_progress WHERE user_id=? AND target_type='LEXEME' AND target_id=?",
                user, lexeme);
    }

    private long scheduleCount(long user, long lexeme) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? "
                + "AND target_type='LEXEME' AND target_id=?", Long.class, user, lexeme);
    }

    private static boolean isSelect(String sql) {
        return sql.stripLeading().regionMatches(true, 0, "select", 0, "select".length());
    }

    record QueryCounts(long statementInspector, long jdbcTemplate) {
        long total() { return statementInspector + jdbcTemplate; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture reviewSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer reviewStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> stop() {
            List<String> statements = List.copyOf(active.get());
            active.remove();
            return statements;
        }
        @Override public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql);
            return sql;
        }
    }
}
