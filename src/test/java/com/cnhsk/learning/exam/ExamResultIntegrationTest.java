package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamResultIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExamQueryService exams;
    @Autowired private EntityManagerFactory entities;
    @Autowired private ExamResultService results;
    private long learner;
    private long other;
    private Long knowledgeFixtureId;

    @BeforeAll
    void identities() throws Exception {
        learner = createUser();
        other = createUser();
    }

    @AfterEach
    void removeKnowledgeFixture() {
        if (knowledgeFixtureId == null) return;
        jdbc.update("DELETE FROM learning.question_knowledge_points WHERE knowledge_point_id = ?", knowledgeFixtureId);
        jdbc.update("DELETE FROM learning.knowledge_points WHERE id = ?", knowledgeFixtureId);
        knowledgeFixtureId = null;
    }

    @Test
    void FR012_weightedFinishAndExpiry() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Một", 0, 1, 2, true);
        question(exam, "Hai", 0, 2, 1, true);
        question(exam, "Ba", 1, 1, 1, true);
        long id = start(learner, exam, 201).path("sessionId").asLong();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<MvcResult> submit = () -> {
                assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
                return submit(id, java.util.Map.of(1, "A", 3, "A"));
            };
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            gate.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS);
            MvcResult b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
            JsonNode output = json(a.getResponse().getStatus() == 200 ? a : b);
            assertThat(output.path("extra").path("weightedScore").asInt()).isEqualTo(3);
            assertThat(output.path("extra").path("weightedMaxScore").asInt()).isEqualTo(4);
            assertThat(output.path("extra").path("weightedPercent").asInt()).isEqualTo(75);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT score FROM learning.attempts WHERE id=?", Long.class, id)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT max_score FROM learning.attempts WHERE id=?", Long.class, id)).isEqualTo(3L);
        long expired = start(learner, exam, 201).path("sessionId").asLong();
        jdbc.update("UPDATE learning.attempts SET expires_at=now()-interval '1 minute' WHERE id=?", expired);
        MvcResult result = submit(expired, Map.of());
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(result).path("error_code").asText()).isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void FR013_usesSnapshotAndBatchesKidQueries() throws Exception {
        knowledgeFixtureId = jdbc.queryForObject("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                VALUES ('NP1-982','Nút tự soạn','NP','样例',1) RETURNING id
                """, Long.class);
        for (int size : List.of(3, 6)) {
            long exam = exam("PUBLISHED", 1);
            for (int i = 1; i <= size; i++) question(exam, "Câu tự soạn " + i, 0, i, 1, true);
            jdbc.update("""
                    INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id)
                    SELECT question_id, ? FROM learning.exam_questions WHERE exam_id=? AND order_index <= 2
                    """, knowledgeFixtureId, exam);
            jdbc.update("""
                    UPDATE learning.questions SET options=jsonb_set(options,'{1}',
                        (options->1) || '{"error_kid":"NP1-982","error_note":"Lỗi mẫu"}'::jsonb)
                    WHERE id=(SELECT question_id FROM learning.exam_questions WHERE exam_id=? AND order_index=1)
                    """, exam);
            long id = start(learner, exam, 201).path("sessionId").asLong();
            assertThat(submit(id, Map.of(1, "B")).getResponse().getStatus()).isEqualTo(200);
            jdbc.update("""
                    UPDATE learning.questions SET correct_key='B', explanation='Đã sửa'
                    WHERE id IN (SELECT question_id FROM learning.exam_questions WHERE exam_id=?)
                    """, exam);
            var stats = entities.unwrap(SessionFactory.class).getStatistics();
            boolean enabled = stats.isStatisticsEnabled();
            stats.setStatisticsEnabled(true);
            try {
                long before = stats.getPrepareStatementCount();
                var result = results.result(learner, id);
                assertThat(stats.getPrepareStatementCount() - before).isEqualTo(5);
                assertThat(result.previous()).isNull();
                assertThat(result.items()).hasSize(size);
                var wrong = result.items().get(0);
                assertThat(wrong.result()).isEqualTo(ExamResultService.ItemResult.WRONG);
                assertThat(wrong.correctKey()).isEqualTo("A");
                assertThat(wrong.explanation()).isEqualTo("Giải thích tự soạn");
                assertThat(wrong.errorKid()).isEqualTo("NP1-982");
                assertThat(wrong.errorNote()).isEqualTo("Lỗi mẫu");
                assertThat(result.items().get(1).result()).isEqualTo(ExamResultService.ItemResult.BLANK);
                assertThat(result.items().get(2).primaryKid()).isNull();
                assertThat(result.wrongByKid()).containsExactly(new ExamResultService.KidCount("NP1-982", "Nút tự soạn", 2, 2));
            } finally {
                stats.setStatisticsEnabled(enabled);
            }
        }
    }

    @Test
    void FR013_previousFollowsSubmissionOrderAndSharesPercentWithList() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Một", 0, 1, 2, true);
        question(exam, "Hai", 0, 2, 1, true);
        long first = start(learner, exam, 201).path("sessionId").asLong();
        finish(learner, first, "A", "A");
        long second = start(learner, exam, 201).path("sessionId").asLong();
        assertThat(finish(learner, second, "A", "B").path("extra").path("weightedPercent").asInt()).isEqualTo(66);
        assertThat(results.result(learner, second).percent()).isEqualTo(66);
        long third = start(learner, exam, 201).path("sessionId").asLong();
        finish(learner, third, "B", "A");
        jdbc.update("UPDATE learning.attempts SET submitted_at=now() WHERE id IN (?, ?)", first, second);
        jdbc.update("UPDATE learning.attempts SET submitted_at=now()+interval '1 minute' WHERE id=?", third);
        assertThat(results.result(learner, third).previous().percent()).isEqualTo(66);
        long roundedExam = exam("PUBLISHED", 1);
        question(roundedExam, "Một", 0, 1, 2, true);
        question(roundedExam, "Hai", 0, 2, 1, true);
        long rounded = start(learner, roundedExam, 201).path("sessionId").asLong();
        finish(learner, rounded, "A", "B");
        var summaries = new java.util.ArrayList<JsonNode>();
        list(learner, 1).forEach(row -> { if (row.path("id").asLong() == roundedExam) summaries.add(row); });
        assertThat(summaries).singleElement().satisfies(row -> assertThat(row.path("myBest").path("percent").asInt())
                .isEqualTo(results.result(learner, rounded).percent()).isEqualTo(66));
    }

    @Test
    void FR013_ownershipBeforeSubmissionAndLegacyLevel() throws Exception {
        long exam = exam("PUBLISHED", 1);
        question(exam, "Một", 0, 1, 1, true);
        long id = start(learner, exam, 201).path("sessionId").asLong();
        error(other, id, 403, "ATTEMPT_NOT_OWNED");
        error(learner, Long.MAX_VALUE, 403, "ATTEMPT_NOT_OWNED");
        error(learner, id, 409, "ATTEMPT_NOT_SUBMITTED");
        mvc.perform(get("/api/exam-attempts/{id}/result", id)
                .header("Authorization", bearer(learner, "CONTENT_EDITOR"))).andExpect(status().isForbidden());
        finish(learner, id, "A");
        jdbc.update("UPDATE learning.exams SET hsk_level=2,status='ARCHIVED' WHERE id=?", exam);
        assertThat(results.result(learner, id).exam().hskLevel()).isEqualTo(1);
        jdbc.update("UPDATE learning.attempts SET served_items=served_items #- '{meta,exam,hskLevel}' WHERE id=?", id);
        assertThat(results.result(learner, id).exam().hskLevel()).isEqualTo(2);
        jdbc.update("""
                UPDATE learning.attempts SET served_items=jsonb_set(served_items,'{meta,exam,examId}',to_jsonb(?::bigint))
                WHERE id=?
                """, Long.MAX_VALUE, id);
        error(learner, id, 500, "INTERNAL_ERROR");
        jdbc.update("UPDATE learning.attempts SET ref_type='TOPIC_TEST' WHERE id=?", id);
        error(learner, id, 403, "ATTEMPT_NOT_OWNED");
    }

    private void error(long user, long id, int code, String error) throws Exception {
        mvc.perform(get("/api/exam-attempts/{id}/result", id).header("Authorization", bearer(user, "LEARNER")))
                .andExpect(status().is(code)).andExpect(jsonPath("$.error_code").value(error))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    private MvcResult submit(long id, Map<Integer, String> keys) throws Exception {
        var answers = mapper.createArrayNode();
        keys.forEach((no, key) -> answers.addObject().put("no", no).put("answer", key).put("durationMs", 2000));
        return mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                .header("Authorization", bearer(learner, "LEARNER")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode().set("answers", answers).toString())).andReturn();
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
