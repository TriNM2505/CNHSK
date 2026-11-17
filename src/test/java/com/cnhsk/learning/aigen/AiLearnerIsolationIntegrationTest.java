package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Exercises the learner entry points against committed PostgreSQL fixtures and real Redis. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AiLearnerIsolationIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-17T04:05:06Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final String OPTIONS = """
            [{"key":"A","text":"nước","is_correct":true},{"key":"B","text":"lửa","is_correct":false}]
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private SettingsApi settings;
    @Autowired private RateLimiter counters;
    @MockitoBean private Clock clock;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> nodes = new ArrayList<>();
    private final List<Long> questions = new ArrayList<>();
    private final List<Long> topics = new ArrayList<>();
    private final List<Long> exams = new ArrayList<>();
    private final List<Long> lexemes = new ArrayList<>();
    private long learner;
    private long teacher;

    @BeforeEach
    void identitiesAndTime() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        learner = user("LEARNER");
        teacher = user("TEACHER");
    }

    @AfterEach
    void removeOwnedFixtures() {
        for (long user : users) {
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN "
                    + "(SELECT id FROM learning.attempts WHERE user_id=?)", user);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", user);
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", user);
            counters.clear("quiz:start:" + user);
            counters.clear("practice:start:" + user + ":2026111704");
        }
        for (long exam : exams) {
            jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id=?", exam);
            jdbc.update("DELETE FROM learning.exams WHERE id=?", exam);
        }
        for (long topic : topics) jdbc.update("DELETE FROM learning.topics WHERE id=?", topic);
        for (long question : questions) {
            jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
            jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
        }
        for (long lexeme : lexemes) jdbc.update("DELETE FROM learning.lexemes WHERE id=?", lexeme);
        for (long node : nodes) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
        for (long user : users) {
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user);
            jdbc.update("DELETE FROM auth.users WHERE id=?", user);
        }
    }

    @Test
    void targetedPracticeExcludesUnreviewedAiAndTeacherApprovalMakesItReachableInANewSession() throws Exception {
        Bank bank = bank(2);
        JsonNode before = targeted(bank.kid());
        assertQuestionSession(before, bank.approved(), bank, false);
        assertThat(before.path("source").asText()).isEqualTo("TARGETED_PRACTICE");

        JsonNode approval = json(mvc.perform(post("/api/admin/ai-questions/{id}/approve", bank.pending().id())
                        .header("Authorization", bearer(teacher, "TEACHER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(approval.path("id").asLong()).isEqualTo(bank.pending().id());
        assertThat(approval.path("status").asText()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM learning.questions WHERE id=?",
                Long.class, bank.pending().id())).isEqualTo(teacher);

        List<Question> approved = new ArrayList<>(bank.approved());
        approved.add(bank.pending());
        JsonNode after = targeted(bank.kid());
        assertThat(after.path("sessionId").asLong()).isNotEqualTo(before.path("sessionId").asLong());
        assertQuestionSession(after, approved, bank, false);
        assertThat(snapshotIds(after)).contains(bank.pending().id()).doesNotContain(bank.rejected().id());
        assertThat(prompts(after, false)).contains(bank.pending().stem());
        // Approval creates eligibility for fresh sessions; previously issued snapshots stay unchanged.
        assertThat(snapshotIds(before)).doesNotContain(bank.pending().id(), bank.rejected().id());
        assertQuestionStates(bank, "APPROVED");
    }

    @Test
    void publishedMockExamNeverSnapshotsPendingOrRejectedAiAmongApprovedControls() throws Exception {
        Bank bank = bank(2);
        long exam = jdbc.queryForObject("""
                INSERT INTO learning.exams(code,title,hsk_level,status,sections)
                VALUES (?, 'Đề thi tự soạn', 1, 'PUBLISHED', '[{"index":0,"name":"Đọc"}]'::jsonb)
                RETURNING id
                """, Long.class, "AI-ISO-" + UUID.randomUUID());
        exams.add(exam);
        List<Question> linked = new ArrayList<>(bank.approved());
        linked.add(bank.pending());
        linked.add(bank.rejected());
        for (int i = 0; i < linked.size(); i++) {
            jdbc.update("""
                    INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index,points)
                    VALUES (?, ?, 0, ?, 1)
                    """, exam, linked.get(i).id(), i + 1);
        }

        JsonNode first = start("/api/exams/" + exam + "/attempts", null);
        assertThat(first.path("source").asText()).isEqualTo("MOCK_EXAM");
        assertQuestionSession(first, bank.approved(), bank, false);
        abandon(first);
        JsonNode second = start("/api/exams/" + exam + "/attempts", null);
        assertThat(second.path("sessionId").asLong()).isNotEqualTo(first.path("sessionId").asLong());
        assertQuestionSession(second, bank.approved(), bank, false);
        assertQuestionStates(bank, "PENDING_REVIEW");
    }

    @Test
    void quizNeverServesPendingOrRejectedAiWhenItsApprovedBankCanFillTheWholeSession() throws Exception {
        int count = settings.getInt(SettingKey.QUIZ_QUESTION_COUNT);
        Bank bank = bank(count);
        long topic = jdbc.queryForObject("""
                INSERT INTO learning.topics(code,name,hsk_level,knowledge_point_id)
                VALUES (?, 'Chủ đề tự soạn', 1, ?) RETURNING id
                """, Long.class, "AI-ISO-" + UUID.randomUUID(), bank.node());
        topics.add(topic);

        JsonNode first = start("/api/quizzes/" + topic + "/attempts", null);
        assertThat(first.path("topicId").asLong()).isEqualTo(topic);
        assertQuestionSession(first, bank.approved(), bank, true);
        abandon(first);
        JsonNode second = start("/api/quizzes/" + topic + "/attempts", null);
        assertThat(second.path("sessionId").asLong()).isNotEqualTo(first.path("sessionId").asLong());
        assertQuestionSession(second, bank.approved(), bank, true);
        assertQuestionStates(bank, "PENDING_REVIEW");
    }

    @Test
    void dueReviewUsesOnlyDueLexemesAndCannotReachTheAiQuestionBank() throws Exception {
        Bank bank = bank(2);
        String text = "我想喝水。" + UUID.randomUUID();
        long lexeme = jdbc.queryForObject("""
                INSERT INTO learning.lexemes(kind,text,meaning_vi,hsk_level,knowledge_point_id)
                VALUES ('GRAMMAR', ?, 'Tôi muốn uống nước.', 1, ?) RETURNING id
                """, Long.class, text, bank.node());
        lexemes.add(lexeme);
        jdbc.update("""
                INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,
                    review_count,lapse_count,last_seen_at,next_review_at)
                VALUES (?, 'LEXEME', ?, 1, 5, 0, 0, ?, ?)
                """, learner, lexeme, Timestamp.from(NOW.minusSeconds(86_400)), Timestamp.from(NOW.minusSeconds(60)));

        JsonNode session = start("/api/reviews/sessions", Map.of());
        assertThat(session.path("source").asText()).isEqualTo("DUE_REVIEW");
        assertThat(session.path("items")).hasSize(1);
        assertThat(session.path("items").get(0).path("type").asText()).isEqualTo("FLASHCARD_RATING");
        assertThat(prompts(session, false)).containsExactly(text);
        assertThat(session.findValue("questionId")).isNull();
        JsonNode snapshot = snapshot(session);
        assertThat(snapshot.path("items")).hasSize(1);
        JsonNode item = snapshot.path("items").get(0);
        assertThat(item.path("lexemeId").asLong()).isEqualTo(lexeme);
        assertThat(item.path("questionId").isMissingNode() || item.path("questionId").isNull()).isTrue();
        assertThat(item.path("prompt").path("text").asText()).isEqualTo(text);
        assertThat(snapshot.toString()).doesNotContain(bank.pending().stem(), bank.rejected().stem());
        assertQuestionStates(bank, "PENDING_REVIEW");
    }

    private void assertQuestionSession(JsonNode session, List<Question> expected, Bank bank, boolean quiz) throws Exception {
        assertThat(session.path("sessionId").asLong()).isPositive();
        assertThat(session.path("items")).hasSize(expected.size());
        assertThat(prompts(session, quiz)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Question::stem).toList());
        assertThat(prompts(session, quiz)).doesNotContain(bank.rejected().stem());
        if (!expected.contains(bank.pending())) assertThat(prompts(session, quiz)).doesNotContain(bank.pending().stem());
        assertThat(snapshotIds(session)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Question::id).toList());
        JsonNode snapshot = snapshot(session);
        assertThat(snapshot.path("items")).hasSize(expected.size());
        assertThat(prompts(snapshot, false)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Question::stem).toList());
        assertThat(expected).anyMatch(question -> "AI".equals(question.source()));
        assertThat(session.findValue("questionId")).isNull();
        for (String field : List.of("answer", "correctKey", "is_correct", "isCorrect", "explanation")) {
            assertThat(session.findValue(field)).as(field).isNull();
        }
    }

    private List<String> prompts(JsonNode session, boolean quiz) {
        List<String> prompts = new ArrayList<>();
        for (JsonNode item : session.path("items")) {
            prompts.add(quiz ? item.path("prompt").asText() : item.path("prompt").path("text").asText());
        }
        return prompts;
    }

    private JsonNode snapshot(JsonNode session) throws Exception {
        String stored = jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=? AND user_id=?",
                String.class, session.path("sessionId").asLong(), learner);
        return mapper.readTree(stored);
    }

    private List<Long> snapshotIds(JsonNode session) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : snapshot(session).path("items")) {
            assertThat(item.path("questionId").isIntegralNumber()).isTrue();
            ids.add(item.path("questionId").asLong());
        }
        return ids;
    }

    private void assertQuestionStates(Bank bank, String pendingState) {
        assertThat(jdbc.queryForObject("SELECT status FROM learning.questions WHERE id=?", String.class, bank.pending().id()))
                .isEqualTo(pendingState);
        assertThat(jdbc.queryForObject("SELECT status FROM learning.questions WHERE id=?", String.class, bank.rejected().id()))
                .isEqualTo("REJECTED");
    }

    private Bank bank(int approvedCount) {
        assertThat(approvedCount).isPositive();
        String kid = jdbc.queryForObject("""
                SELECT 'NP1-' || lpad(n::text,3,'0') FROM generate_series(0,999) n
                WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points WHERE code='NP1-' || lpad(n::text,3,'0'))
                ORDER BY n LIMIT 1
                """, String.class);
        long node = jdbc.queryForObject("""
                INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level)
                VALUES (?, 'Nút tự soạn', 'NP', '语法', 1) RETURNING id
                """, Long.class, kid);
        nodes.add(node);
        List<Question> approved = new ArrayList<>();
        // Exactly a full usable bank makes inclusion of the approved AI control deterministic.
        for (int i = 0; i < approvedCount; i++) approved.add(question(node, i == 0 ? "AI" : "IMPORT", "APPROVED"));
        return new Bank(node, kid, List.copyOf(approved), question(node, "AI", "PENDING_REVIEW"),
                question(node, "AI", "REJECTED"));
    }

    private Question question(long node, String source, String state) {
        String code = "AI-ISO-" + UUID.randomUUID();
        String stem = "Chọn nghĩa của 水 — câu tự soạn " + code;
        Long reviewer = "AI".equals(source) && "APPROVED".equals(state) ? teacher : null;
        long id = jdbc.queryForObject("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,
                    source,status,reviewed_by,reviewed_at,review_reason)
                VALUES (?, 'MULTIPLE_CHOICE', 1, ?, ?::jsonb, 'A', '水 có nghĩa là nước.', ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, code, stem, OPTIONS, source, state, reviewer,
                reviewer == null ? null : Timestamp.from(NOW.minusSeconds(60)),
                "REJECTED".equals(state) ? "Lý do từ chối tự soạn" : null);
        questions.add(id);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')",
                id, node);
        return new Question(id, stem, source);
    }

    private long user(String role) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name)
                VALUES (?, ?, 'Người kiểm thử') RETURNING id
                """, Long.class, "ai-isolation-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        users.add(id);
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)", id, role);
        return id;
    }

    private JsonNode targeted(String kid) throws Exception {
        return start("/api/targeted-practice/sessions", Map.of("kid", kid));
    }

    private JsonNode start(String path, Map<String, Object> body) throws Exception {
        var request = post(path).header("Authorization", bearer(learner, "LEARNER"));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        return json(mvc.perform(request).andExpect(status().isCreated()).andReturn());
    }

    private void abandon(JsonNode session) throws Exception {
        mvc.perform(post("/api/practice/sessions/{id}/abandon", session.path("sessionId").asLong())
                        .header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isNoContent());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(long user, String role) {
        return "Bearer " + jwt.issueAccessToken(user, List.of(role), true, UUID.randomUUID());
    }

    private record Question(long id, String stem, String source) { }
    private record Bank(long node, String kid, List<Question> approved, Question pending, Question rejected) { }
}
