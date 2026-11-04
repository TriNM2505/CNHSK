package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExamQueryService exams;
    @Autowired private EntityManagerFactory entities;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;
    private long learner;
    private long other;

    @BeforeAll
    void identities() throws Exception {
        learner = createUser();
        other = createUser();
    }

    @Test
    void FR001_examRoutesRequireLearnerAndCookieStartRequiresCsrf() throws Exception {
        mvc.perform(get("/api/exams")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/exams").header("Authorization", bearer(learner, "CONTENT_EDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/exams/1/attempts").cookie(new Cookie("access_token", token(learner, "LEARNER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(multipart("/api/admin/imports").param("dataType", "EXAM")
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void FR010_onlyUsablePublishedExamsAndOwnHistoryAreListed() throws Exception {
        long draft = exam("DRAFT", 2);
        question(draft, "Nháp", 0, 1, 1, true);
        long empty = exam("PUBLISHED", 2);
        question(empty, "Chờ duyệt", 0, 1, 1, false);
        long published = exam("PUBLISHED", 2);
        question(published, "Đọc", 0, 1, 8, true);
        question(published, "Viết", 1, 1, 2, true);
        question(published, "Loại", 1, 2, 1, false);

        long first = start(learner, published, 201).path("sessionId").asLong();
        finish(learner, first, "A", "B");
        jdbc.update("UPDATE learning.exam_questions SET points = CASE section_index WHEN 0 THEN 9 ELSE 11 END WHERE exam_id = ?", published);
        long second = start(learner, published, 201).path("sessionId").asLong();
        finish(learner, second, "A", "B");
        long theirs = start(other, published, 201).path("sessionId").asLong();
        finish(other, theirs, "A", "A");
        long active = start(learner, published, 201).path("sessionId").asLong();

        JsonNode mine = onlyMatchingExam(list(learner, 2), published, draft, empty);
        assertThat(mine.path("questionCount").asInt()).isEqualTo(2);
        assertThat(mine.path("sections").get(0).path("questionCount").asInt()).isEqualTo(1);
        assertThat(mine.path("sections").get(1).path("questionCount").asInt()).isEqualTo(1);
        assertThat(mine.path("myAttempts").asInt()).isEqualTo(2);
        assertThat(mine.path("myBest")).isEqualTo(mapper.readTree("""
                {"score":8,"maxScore":10,"percent":80}
                """));
        assertThat(mine.path("activeSessionId").asLong()).isEqualTo(active);
        JsonNode theirSummary = onlyMatchingExam(list(other, 2), published);
        assertThat(theirSummary.path("myAttempts").asInt()).isEqualTo(1);
        assertThat(theirSummary.path("myBest").path("percent").asInt()).isEqualTo(100);
        assertThat(theirSummary.path("activeSessionId").isNull()).isTrue();

        jdbc.update("UPDATE learning.attempts SET expires_at = now() - interval '1 minute' WHERE id = ?", active);
        assertThat(onlyMatchingExam(list(learner, 2), published).path("activeSessionId").isNull()).isTrue();
        assertThat(list(learner, 9).size()).isZero();
        mvc.perform(get("/api/exams").param("hskLevel", "10").header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void FR010_queryCountIsFourWithOneOrSeveralPublishedExams() {
        question(exam("PUBLISHED", 6), "Một", 0, 1, 1, true);
        assertFourQueries();
        question(exam("PUBLISHED", 6), "Hai", 0, 1, 1, true);
        question(exam("PUBLISHED", 6), "Ba", 0, 1, 1, true);
        assertFourQueries();
    }

    @Test
    void FR010_equalPercentUsesHigherScoreThenLatestSubmission() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Đọc", 0, 1, 1, true);
        question(exam, "Viết", 1, 1, 1, true);
        finish(learner, start(learner, exam, 201).path("sessionId").asLong(), "A", "B");
        jdbc.update("UPDATE learning.exam_questions SET points = 2 WHERE exam_id = ?", exam);
        finish(learner, start(learner, exam, 201).path("sessionId").asLong(), "A", "B");
        JsonNode best = onlyMatchingExam(list(learner, 1), exam).path("myBest");
        assertThat(best.path("score").asLong()).isEqualTo(2);
        assertThat(best.path("maxScore").asLong()).isEqualTo(4);
        assertThat(best.path("percent").asInt()).isEqualTo(50);

        long zeroExam = exam("PUBLISHED", 1);
        question(zeroExam, "Chọn", 0, 1, 3, true);
        long older = start(learner, zeroExam, 201).path("sessionId").asLong();
        finish(learner, older, "B");
        jdbc.update("UPDATE learning.attempts SET submitted_at = now() - interval '1 minute' WHERE id = ?", older);
        jdbc.update("UPDATE learning.exam_questions SET points = 1 WHERE exam_id = ?", zeroExam);
        finish(learner, start(learner, zeroExam, 201).path("sessionId").asLong(), "B");
        JsonNode latest = onlyMatchingExam(list(learner, 1), zeroExam).path("myBest");
        assertThat(latest.path("score").asLong()).isZero();
        assertThat(latest.path("maxScore").asLong()).isEqualTo(1);
        assertThat(latest.path("percent").asInt()).isZero();
    }

    @Test
    void FR011_startUsesSnapshotOrderAndHidesAnswersUntilSubmission() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Phần sau", 1, 1, 3, true);
        question(exam, "Câu hai", 0, 2, 2, true);
        question(exam, "Câu một", 0, 1, 5, true);

        JsonNode session = start(learner, exam, 201);
        long id = session.path("sessionId").asLong();
        assertThat(session.path("feedback").asText()).isEqualTo("DEFERRED");
        assertThat(session.path("meta").path("exam").path("sectionOf")).isEqualTo(mapper.readTree("""
                {"1":0,"2":0,"3":1}
                """));
        assertThat(session.path("meta").path("exam").path("points")).isEqualTo(mapper.readTree("""
                {"1":5,"2":2,"3":3}
                """));
        JsonNode items = session.path("items");
        assertThat(items.get(0).path("prompt").path("text").asText()).isEqualTo("Câu một");
        assertThat(items.get(1).path("prompt").path("text").asText()).isEqualTo("Câu hai");
        assertThat(items.get(2).path("prompt").path("text").asText()).isEqualTo("Phần sau");
        assertNoAnswers(items);

        mvc.perform(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(learner, "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"no":1,"answer":"A","durationMs":2000}
                        """))
                .andExpect(status().isOk());
        JsonNode saved = json(mvc.perform(get("/api/practice/sessions/{id}", id)
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.path("answered").size()).isEqualTo(1);
        assertThat(saved.path("answered").get(0).has("correct")).isFalse();
        assertThat(saved.path("answered").get(0).has("correctAnswer")).isFalse();
        assertNoAnswers(saved.path("items"));
        assertThat(start(learner, exam, 200).path("sessionId").asLong()).isEqualTo(id);
    }

    @Test
    void FR011_startRejectsMissingUnpublishedAndUnusableExams() throws Exception {
        assertThat(start(learner, Long.MAX_VALUE, 404).path("error_code").asText()).isEqualTo("EXAM_NOT_FOUND");
        for (String state : List.of("DRAFT", "ARCHIVED")) {
            assertThat(start(learner, exam(state, 1), 403).path("error_code").asText()).isEqualTo("EXAM_NOT_PUBLISHED");
        }
        long empty = exam("PUBLISHED", 1);
        question(empty, "Loại", 0, 1, 1, false);
        assertThat(start(learner, empty, 422).path("error_code").asText()).isEqualTo("EXAM_HAS_NO_QUESTIONS");
    }

    @Test
    void FR011_parallelStartsCreateExactlyOneAttempt() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Đọc", 0, 1, 1, true);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var request = (java.util.concurrent.Callable<MvcResult>) () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return requestStart(learner, exam);
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).containsExactlyInAnyOrder(200, 201);
            assertThat(json(a).path("sessionId").asLong()).isEqualTo(json(b).path("sessionId").asLong());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM learning.attempts WHERE ref_type = 'MOCK_EXAM' AND user_id = ? AND ref_id = ?
                    """, Long.class, learner, exam)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void FR011_archivingAnExamDoesNotPreventFinishingItsSnapshot() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Đọc", 0, 1, 3, true);
        long session = start(learner, exam, 201).path("sessionId").asLong();
        jdbc.update("UPDATE learning.exams SET status = 'ARCHIVED' WHERE id = ?", exam);
        assertThat(finish(learner, session, "A").path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(list(learner, 1)).noneMatch(row -> row.path("id").asLong() == exam);
    }

    private void assertFourQueries() {
        var stats = entities.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            clearInvocations(namedJdbc);
            long before = stats.getPrepareStatementCount();
            assertThat(exams.list(learner, 6)).isNotEmpty();
            assertThat(stats.getPrepareStatementCount() - before).isEqualTo(1);
            verify(namedJdbc, times(2)).query(anyString(), anyMap(), any(RowMapper.class));
            verify(namedJdbc).query(anyString(), anyMap(), any(ResultSetExtractor.class));
        } finally {
            stats.setStatisticsEnabled(enabled);
        }
    }

    private static void assertNoAnswers(JsonNode items) {
        for (JsonNode item : items) {
            for (String field : List.of("answer", "accept", "explanation", "is_correct", "isCorrect", "correctKey")) {
                assertThat(item.findValue(field)).as(field).isNull();
            }
        }
    }

    private static JsonNode onlyMatchingExam(JsonNode list, long expected, long... excluded) {
        JsonNode match = null;
        for (JsonNode row : list) {
            long id = row.path("id").asLong();
            for (long unexpected : excluded) assertThat(id).isNotEqualTo(unexpected);
            if (id == expected) match = row;
        }
        assertThat(match).isNotNull();
        return match;
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Exam test user",
                                "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn()).path("userId").asLong();
    }

    private long exam(String state, int level) {
        return jdbc.queryForObject("""
                INSERT INTO learning.exams (code, title, hsk_level, status, sections)
                VALUES (?, 'Đề tự soạn', ?, ?, '[{"index":0,"name":"Đọc"},{"index":1,"name":"Viết"}]'::jsonb)
                RETURNING id
                """, Long.class, "TEST-" + UUID.randomUUID(), level, state);
    }

    private void question(long exam, String stem, int section, int order, int points, boolean approved) {
        long question = jdbc.queryForObject("""
                INSERT INTO learning.questions (code, qtype, hsk_level, stem, options, correct_key, explanation, status)
                VALUES (?, 'MULTIPLE_CHOICE', 1, ?,
                        '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]'::jsonb,
                        'A', 'Giải thích tự soạn', ?)
                RETURNING id
                """, Long.class, "TEST-" + UUID.randomUUID(), stem, approved ? "APPROVED" : "PENDING_REVIEW");
        jdbc.update("""
                INSERT INTO learning.exam_questions (exam_id, question_id, section_index, order_index, points)
                VALUES (?, ?, ?, ?, ?)
                """, exam, question, section, order, points);
    }

    private JsonNode list(long user, int level) throws Exception {
        return json(mvc.perform(get("/api/exams").param("hskLevel", Integer.toString(level))
                        .header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode start(long user, long exam, int expected) throws Exception {
        MvcResult result = requestStart(user, exam);
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
        return json(result);
    }

    private MvcResult requestStart(long user, long exam) throws Exception {
        return mvc.perform(post("/api/exams/{id}/attempts", exam).header("Authorization", bearer(user, "LEARNER")))
                .andReturn();
    }

    private JsonNode finish(long user, long session, String... keys) throws Exception {
        var answers = mapper.createArrayNode();
        for (int i = 0; i < keys.length; i++) {
            answers.addObject().put("no", i + 1).put("answer", keys[i]).put("durationMs", 2000);
        }
        return json(mvc.perform(post("/api/practice/sessions/{id}/finish", session)
                        .header("Authorization", bearer(user, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.createObjectNode().set("answers", answers).toString()))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(long user, String role) {
        return "Bearer " + token(user, role);
    }

    private String token(long user, String role) {
        return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID());
    }
}
