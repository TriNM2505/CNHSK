package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

class QuizSessionIntegrationTest extends QuizIntegrationFixture {
    @Test
    void postCreatesThenResumesSameSnapshotWithoutAnswersEvenWhenBankBecomesUnavailable() throws Exception {
        Topic topic = topic(8, 8);
        var response = mvc.perform(post(startPath(topic)).header("Authorization", bearer()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rankingEligible").value(true))
                .andExpect(jsonPath("$.topicId").value(topic.id())).andReturn().getResponse();
        JsonNode first = mapper.readTree(response.getContentAsByteArray());
        assertThat(first.path("items")).hasSize(10);
        assertThat(first.path("items").findValues("itemId")).extracting(JsonNode::asInt)
                .containsExactly(1,2,3,4,5,6,7,8,9,10);
        assertThat(response.getContentAsString()).doesNotContain("\"answer\"", "\"correct\"", "isCorrect",
                "is_correct", "explanation", "questionId");
        jdbc.update("UPDATE learning.questions SET status='ARCHIVED' WHERE id IN "
                + "(SELECT question_id FROM learning.question_knowledge_points WHERE knowledge_point_id=?)", topic.kid());
        JsonNode resumed = mapper.readTree(mvc.perform(post(startPath(topic)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(resumed).isEqualTo(first);
        assertThat(redisCount()).isEqualTo("1");
        assertThat(practice.finish(user, first.path("sessionId").asLong(), answers(10)).score()).isEqualTo(100);
    }

    @Test
    void catalogAndStartExcludeUnsupportedTypesAndBothKindsOfMedia() throws Exception {
        Topic topic = topic(8, 0);
        addQuestions(topic.kid(), 2, "ESSAY", null, null, "APPROVED");
        addQuestions(topic.kid(), 1, "MULTIPLE_CHOICE", "https://example.test/a.mp3", null, "APPROVED");
        addQuestions(topic.kid(), 1, "TRUE_FALSE", null, "https://example.test/i.png", "APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.question_knowledge_points WHERE knowledge_point_id=?",
                Integer.class, topic.kid())).isEqualTo(12);
        assertThat(catalog.list(user, 1, 0, 50).items()).extracting(QuizCatalog.QuizListing::topicId)
                .doesNotContain(topic.id());
        mvc.perform(post(startPath(topic)).header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("QUIZ_NOT_AVAILABLE"));
        assertThat(redisCount()).isNull();
    }

    @Test
    void foreignPrimaryKidUnapprovedQuestionsAndUnlinkedTopicCannotSupplyQuiz() {
        Topic insufficient = topic(9, 0);
        Topic foreign = topic(10, 0);
        long foreignQuestion = jdbc.queryForObject("SELECT question_id FROM learning.question_knowledge_points "
                + "WHERE knowledge_point_id=? LIMIT 1", Long.class, foreign.kid());
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) "
                + "VALUES (?,?,'SECONDARY')", foreignQuestion, insufficient.kid());
        addQuestions(insufficient.kid(), 2, "MULTIPLE_CHOICE", null, null, "PENDING_REVIEW");
        addQuestions(insufficient.kid(), 1, "MULTIPLE_CHOICE", null, null, "ARCHIVED");
        addQuestions(insufficient.kid(), 1, "MULTIPLE_CHOICE", null, null, "REJECTED");
        assertUnavailable(insufficient);
        jdbc.update("UPDATE learning.topics SET knowledge_point_id=NULL WHERE id=?", insufficient.id());
        assertUnavailable(insufficient);
    }

    @Test
    void missingAndInvalidTopicHaveContractErrors() throws Exception {
        mvc.perform(post("/api/quizzes/9223372036854775807/attempts").header("Authorization", bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("TOPIC_NOT_FOUND"));
        mvc.perform(post("/api/quizzes/0/attempts").header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/quizzes/abc/attempts").header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void normalizedFirstAndRetryScoresKeepFirstUnchangedAndDoNotAffectMastery() {
        Topic topic = topic(10, 0);
        var first = sessions.start(user, topic.id()).session();
        assertThat(attempts.existsActiveTest(user, NOW)).isTrue();
        advance(20);
        var result = practice.finish(user, first.sessionId(), answers(8));
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.maxScore()).isEqualTo(100);
        assertThat(result.percent()).isEqualTo(80);
        assertThat(result.suspicious()).isFalse();
        assertThat(result.extra()).containsEntry("rankingEligible", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
        var retry = sessions.start(user, topic.id());
        assertThat(retry.created()).isTrue();
        assertThat(retry.session().rankingEligible()).isFalse();
        assertThat(attempts.existsActiveTest(user, NOW.plusSeconds(20))).isFalse();
        advance(40);
        assertThat(practice.finish(user, retry.session().sessionId(), answers(10)).score()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT score FROM learning.attempts WHERE id=?", Long.class, first.sessionId())).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT ref_type FROM learning.study_events WHERE user_id=? ORDER BY id DESC LIMIT 1",
                String.class, user)).isEqualTo("QUIZ_RETRY");
    }

    @Test
    void deferredAnswersUseSnapshotOrdinalsAndDoNotRevealGradingBeforeFinish() throws Exception {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        mvc.perform(post("/api/practice/sessions/"+id+"/answers").header("Authorization", bearer())
                        .contentType("application/json").content("{\"no\":1,\"answer\":\"A\",\"durationMs\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.correct").doesNotExist()).andExpect(jsonPath("$.correctAnswer").doesNotExist());
        mvc.perform(post("/api/practice/sessions/"+id+"/finish").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(10))
                .andExpect(jsonPath("$.maxScore").value(100));
    }

    @Test
    void skippedAndUnansweredItemsRemainInFullServedDenominator() {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        List<Answer> answers = new ArrayList<>(answers(8));
        answers.set(8, new Answer(9, null, 0, 0, true, false, null));
        answers.remove(9);
        var result = practice.finish(user, id, answers);
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.maxScore()).isEqualTo(100);
        assertThat(result.items()).hasSize(10);
    }

    @Test
    void suspiciousUsesRawServerTimestampsAndStillConsumesFirstSubmission() {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        advance(12);
        assertThat(practice.finish(user, id, answers(10)).suspicious()).isTrue();
        jdbc.queryForObject("SELECT served_at,submitted_at,duration_ms,suspicious FROM learning.attempts WHERE id=?",
                (row, no) -> {
                    assertThat(row.getTimestamp("served_at").toInstant()).isEqualTo(NOW);
                    assertThat(row.getTimestamp("submitted_at").toInstant()).isEqualTo(NOW.plusSeconds(12));
                    assertThat(row.getInt("duration_ms")).isEqualTo(12000);
                    assertThat(row.getBoolean("suspicious")).isTrue();
                    return true;
                }, id);
        assertThat(sessions.start(user, topic.id()).session().rankingEligible()).isFalse();
    }

    @Test
    void abandonedAndExpiredAttemptsDoNotConsumeFirstSubmission() {
        Topic topic = topic(10, 0);
        long abandoned = sessions.start(user, topic.id()).session().sessionId();
        practice.abandon(user, abandoned);
        var second = sessions.start(user, topic.id()).session();
        assertThat(second.rankingEligible()).isTrue();
        advance(1801);
        assertThatThrownBy(() -> practice.finish(user, second.sessionId(), List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SESSION_EXPIRED));
        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, second.sessionId()))
                .isEqualTo("EXPIRED");
        var third = sessions.start(user, topic.id()).session();
        assertThat(third.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(third.rankingEligible()).isTrue();
    }

    @Test
    void thirtyNewSessionsShareQuotaAcrossSourcesAndResumeDoesNotCount() throws Exception {
        Topic topic = topic(10, 0);
        var first = sessions.start(user, topic.id()).session();
        advance(20);
        practice.finish(user, first.sessionId(), answers(10));
        for (int i=2; i<=30; i++) {
            var started = sessions.start(user, topic.id());
            assertThat(started.created()).isTrue();
            assertThat(started.session().rankingEligible()).isFalse();
            assertThat(sessions.start(user, topic.id()).created()).isFalse();
            practice.abandon(user, started.session().sessionId());
        }
        assertThat(redisCount()).isEqualTo("30");
        mvc.perform(post(startPath(topic)).header("Authorization", bearer()))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=?", Integer.class, user))
                .isEqualTo(30);
    }

    @Test
    void configuredQuestionCountAndLifetimeDriveCatalogSnapshotScoringAndExpiry() {
        config.setQuestionCount(12);
        config.setSessionMinutes(7);
        Topic eleven = topic(11, 0);
        Topic twelve = topic(6, 6);
        assertUnavailable(eleven);
        assertThat(catalog.list(user, 1, 0, 50).items()).extracting(QuizCatalog.QuizListing::topicId).doesNotContain(eleven.id());
        var session = sessions.start(user, twelve.id()).session();
        assertThat(session.items()).hasSize(12);
        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(420));
        advance(24);
        assertThat(practice.finish(user, session.sessionId(), answers(10)).score()).isEqualTo(83);
    }

    @Test
    void engineKeepsOwnershipChecksAndRejectsInvalidQuizAnswerBeforeWritingItems() {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        assertThatThrownBy(() -> practice.finish(user + 1, id, List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ATTEMPT_NOT_OWNED));
        assertThatThrownBy(() -> practice.finish(user, id, List.of(new Answer(11,"A",0,0,false,false,null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ITEM_NOT_IN_SESSION));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, id)).isEqualTo("IN_PROGRESS");
    }

    private void assertUnavailable(Topic topic) {
        assertThatThrownBy(() -> sessions.start(user, topic.id())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.QUIZ_NOT_AVAILABLE));
    }
}

/** Committed fixtures are visible to the engine's REQUIRES_NEW; cleanup touches only this test's IDs. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
abstract class QuizIntegrationFixture extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-14T04:05:06.123456Z");
    static final String OPTIONS = "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
            + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]";
    @Autowired QuizSessionService sessions;
    @Autowired QuizQuestionPicker picker;
    @Autowired QuizCatalog catalog;
    @Autowired QuizConfig config;
    @Autowired PracticeService practice;
    @Autowired AttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired RateLimiter counters;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @MockitoBean Clock clock;
    long user;
    private final List<Topic> topics = new ArrayList<>();
    private final List<Long> questions = new ArrayList<>();
    private int questionCount;
    private int sessionMinutes;
    private int minimumSeconds;

    @BeforeEach
    void fixture() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        questionCount = config.getQuestionCount();
        sessionMinutes = config.getSessionMinutes();
        minimumSeconds = config.getMinSecondsPerQuestion();
        config.setQuestionCount(10);
        config.setSessionMinutes(30);
        config.setMinSecondsPerQuestion(2);
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,'fixture-unused','Quiz engine fixture') RETURNING id", Long.class,
                "quiz-engine-"+UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'LEARNER')", user);
    }

    @AfterEach
    void cleanup() {
        config.setQuestionCount(questionCount);
        config.setSessionMinutes(sessionMinutes);
        config.setMinSecondsPerQuestion(minimumSeconds);
        jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", user);
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", user);
        jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", user);
        jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", user);
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user);
        jdbc.update("DELETE FROM auth.users WHERE id=?", user);
        for (Topic topic : topics) jdbc.update("DELETE FROM learning.topics WHERE id=?", topic.id());
        for (long question : questions) {
            jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
            jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
        }
        for (Topic topic : topics) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", topic.kid());
        // Delete only counters belonging to this test's unique user, never FLUSHDB or suite-wide keys.
        counters.clear("quiz:start:"+user);
        for (String operation : List.of("start", "ans")) {
            for (String hour : List.of("2026111404", "2026111405")) counters.clear("practice:"+operation+":"+user+":"+hour);
        }
    }

    Topic topic(int multiple, int trueFalse) {
        String code = jdbc.queryForObject("SELECT 'VB1-' || lpad(n::text,3,'0') FROM generate_series(0,999) n "
                + "WHERE NOT EXISTS(SELECT 1 FROM learning.knowledge_points WHERE code='VB1-' || lpad(n::text,3,'0')) "
                + "ORDER BY n LIMIT 1", String.class);
        long kid = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                + "VALUES (?,'Quiz tự soạn','VB','阅读',1) RETURNING id", Long.class, code);
        long id = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,knowledge_point_id,order_index) "
                + "VALUES (?,'Quiz tự soạn',1,?,-10000) RETURNING id", Long.class, "QE-"+UUID.randomUUID(), kid);
        Topic topic = new Topic(id, kid);
        topics.add(topic);
        addQuestions(kid, multiple, "MULTIPLE_CHOICE", null, null, "APPROVED");
        addQuestions(kid, trueFalse, "TRUE_FALSE", null, null, "APPROVED");
        return topic;
    }

    void addQuestions(long kid, int count, String type, String audio, String image, String status) {
        for (int i=0; i<count; i++) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions"
                    + "(qtype,hsk_level,stem,options,correct_key,status,code,audio_url,image_url,review_reason) "
                    + "VALUES (?,1,'Chọn nghĩa của 水.',?::jsonb,'A',?,?,?,?,?) RETURNING id", Long.class,
                    type, OPTIONS, status, "QE-"+UUID.randomUUID(), audio, image,
                    status.equals("REJECTED") ? "Kiểm thử tự soạn" : null);
            questions.add(id);
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", id, kid);
        }
    }

    long directQuiz(Topic topic) {
        return practice.start(new StartPracticeCommand(user, PracticeSource.QUIZ, topic.id(), null,
                picker.pick(topic.id(), 10), List.of(), null, Map.of())).sessionId();
    }

    List<Answer> answers(int correct) {
        return IntStream.rangeClosed(1, config.getQuestionCount()).mapToObj(no ->
                new Answer(no, no <= correct ? "A" : "B", 0, 0, false, false, null)).toList();
    }

    void advance(long seconds) { when(clock.instant()).thenReturn(NOW.plusSeconds(seconds)); }
    String bearer() { return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()); }
    String redisCount() { return redis.opsForValue().get("quiz:start:"+user); }
    String startPath(Topic topic) { return "/api/quizzes/"+topic.id()+"/attempts"; }
    record Topic(long id, long kid) { }
}
