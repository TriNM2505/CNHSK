package com.cnhsk.learning.aigen;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.knowledge.web"})
class AiReviewKnowledgeIntegrationTest extends AbstractIntegrationTest {
    private static final String QUESTIONS = "/api/admin/questions";
    private static final String REVIEW = "/api/admin/ai-questions";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private QuestionRepository questions;
    @Autowired private KnowledgePointRepository points;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiReviewViewedStore viewed;
    @MockitoSpyBean private StringRedisTemplate redis;

    @Test
    void editorCannotEditOrApprovePendingAi() throws Exception {
        Question question = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        for (String content : List.of("", "{}", "{bad-json")) assertEditorForbidden(question.getId().toString(), content);
        mvc.perform(post(REVIEW + "/{id}/approve", question.getId()).header("Authorization", actor("CONTENT_EDITOR").bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        assertThat(jdbc.queryForObject("SELECT status FROM learning.questions WHERE id=?", String.class, question.getId()))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void teacherCanEditPendingAiThenApproveTheStoredCorrectOption() throws Exception {
        Question question = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        Actor editor = actor("TEACHER");
        mvc.perform(put(QUESTIONS + "/{id}", question.getId()).header("Authorization", editor.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correctKey").value("B"))
                .andExpect(jsonPath("$.source").value("AI")).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(jdbc.queryForObject("SELECT correct_key FROM learning.questions WHERE id=?", String.class, question.getId())).isEqualTo("B");
        mvc.perform(post(REVIEW + "/{id}/approve", question.getId()).header("Authorization", editor.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TEACHER_APPROVED"));
        assertThat(jdbc.queryForObject("SELECT status FROM learning.questions WHERE id=?", String.class, question.getId())).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_type='question' AND target_id=? AND actor_id=? AND action IN ('QUESTION_UPDATE','AI_QUESTION_TEACHER_APPROVE')",
                Long.class, question.getId(), editor.id())).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionSource.class, names = {"IMPORT", "MANUAL"})
    void editorCannotEditNonAiSourcesEvenWithAnUnreadableBody(QuestionSource source) throws Exception {
        Question question = question(source, QuestionStatus.PENDING_REVIEW);
        assertEditorForbidden(question.getId().toString(), "{bad-json");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionStatus.class, names = {"APPROVED", "REJECTED", "ARCHIVED"})
    void editorCannotEditAiOutsidePendingBeforeArchiveOrBodyChecks(QuestionStatus state) throws Exception {
        Question question = question(QuestionSource.AI, state);
        for (String content : List.of("", "{}", "{bad-json")) assertEditorForbidden(question.getId().toString(), content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void editorInvalidAndMissingTargetsReturn403BeforeParsingTheBody(String id) throws Exception {
        assertEditorForbidden(id, "{bad-json");
    }

    @Test
    void teacherOrdinaryUpdateKeepsTheExistingCorrectKeyBehaviorAndErrors() throws Exception {
        Actor teacher = actor("TEACHER");
        Question question = question(QuestionSource.IMPORT, QuestionStatus.APPROVED);
        mvc.perform(put(QUESTIONS + "/{id}", question.getId()).header("Authorization", teacher.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correctKey").isEmpty());
        mvc.perform(put(QUESTIONS + "/abc").header("Authorization", teacher.bearer()).content("{bad-json"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("QUESTION_NOT_FOUND"));
        question.setStatus(QuestionStatus.ARCHIVED); questions.saveAndFlush(question);
        mvc.perform(put(QUESTIONS + "/{id}", question.getId()).header("Authorization", teacher.bearer()).content("{bad-json"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("QUESTION_ARCHIVED"));
    }

    @Test
    void adminAloneCannotReadOrWriteReviewRoutesOrQuestionDetail() throws Exception {
        Actor admin = actor("ADMIN");
        Question question = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        mvc.perform(get(REVIEW).header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(REVIEW + "/unknown").header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        mvc.perform(put(REVIEW).header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(QUESTIONS + "/{id}", question.getId()).header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        mvc.perform(put(QUESTIONS + "/{id}", question.getId()).header("Authorization", admin.bearer()).content("{}"))
                .andExpect(status().isForbidden());
        for (String action : List.of("approve", "reject")) {
            mvc.perform(post(REVIEW + "/{id}/" + action, question.getId()).header("Authorization", admin.bearer()).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        mvc.perform(post(REVIEW + "/bulk-approve").header("Authorization", admin.bearer()).content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT_EDITOR", "TEACHER"})
    void actualDetailWritesAnActorSpecificTwentyFourHourMarkerAndKeepsItsResponseShape(String role) throws Exception {
        Actor first = actor(role); Actor second = actor(role);
        Question question = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        JsonNode result = mapper.readTree(mvc.perform(get(QUESTIONS + "/{id}", question.getId()).header("Authorization", first.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "code", "qtype", "hskLevel", "difficulty", "stem", "options",
                "correctKey", "explanation", "audioUrl", "imageUrl", "source", "status", "reviewReason", "labels", "used");
        String key = key(first.id(), question.getId());
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(Duration.ofHours(24).minusSeconds(30).toMillis(), Duration.ofHours(24).toMillis());
        assertThat(viewed.hasViewed(first.id(), question.getId())).isTrue();
        assertThat(viewed.hasViewed(second.id(), question.getId())).isFalse();
        assertThat(viewed.viewed(first.id(), List.of(question.getId()))).containsExactly(question.getId());
        redis.expire(key, Duration.ZERO);
        assertThat(viewed.hasViewed(first.id(), question.getId())).isFalse();
    }

    @Test
    void listAndNonpendingOrOrdinaryDetailNeverCreateViewedMarkers() throws Exception {
        Actor editor = actor("TEACHER");
        Question pending = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        mvc.perform(get(REVIEW).header("Authorization", editor.bearer())).andExpect(status().isOk());
        mvc.perform(get(QUESTIONS).header("Authorization", editor.bearer())).andExpect(status().isOk());
        assertThat(redis.hasKey(key(editor.id(), pending.getId()))).isFalse();
        for (QuestionSource source : QuestionSource.values()) {
            for (QuestionStatus state : QuestionStatus.values()) {
                if (source == QuestionSource.AI && state == QuestionStatus.PENDING_REVIEW) continue;
                Question question = question(source, state);
                mvc.perform(get(QUESTIONS + "/{id}", question.getId()).header("Authorization", editor.bearer())).andExpect(status().isOk());
                assertThat(redis.hasKey(key(editor.id(), question.getId()))).isFalse();
            }
        }
    }

    @Test
    void detailStillReturns200WhenTheActualRedisMarkerWriteFails() throws Exception {
        Actor editor = actor("CONTENT_EDITOR");
        Question question = question(QuestionSource.AI, QuestionStatus.PENDING_REVIEW);
        ValueOperations<String, String> values = spy(redis.opsForValue());
        doReturn(values).when(redis).opsForValue();
        doThrow(new RedisConnectionFailureException("Synthetic review-marker outage"))
                .when(values).set(eq(key(editor.id(), question.getId())), anyString(), eq(Duration.ofHours(24)));
        mvc.perform(get(QUESTIONS + "/{id}", question.getId()).header("Authorization", editor.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(question.getId()));
        assertThat(redis.hasKey(key(editor.id(), question.getId()))).isFalse();
    }

    @Test
    void generatedKnowledgeOperationsDoNotExposeTheInjectedCaller() throws Exception {
        JsonNode runtime = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        JsonNode getOperation = runtime.path("paths").path(QUESTIONS + "/{id}").path("get");
        assertThat(getOperation.path("parameters")).extracting(value -> value.path("name").asText()).containsExactly("id");
        JsonNode putOperation = runtime.path("paths").path(QUESTIONS + "/{id}").path("put");
        assertThat(putOperation.path("parameters")).extracting(value -> value.path("name").asText()).containsExactlyInAnyOrder("id", "X-XSRF-TOKEN");
        assertThat(putOperation.path("responses").fieldNames()).toIterable().containsExactlyInAnyOrder("200", "401", "403", "404", "409", "422");
        assertThat(putOperation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/QuestionUpdate");
        assertThat(runtime.path("components").path("schemas").has("CurrentUser")).isFalse();
    }

    private void assertEditorForbidden(String id, String content) throws Exception {
        mvc.perform(put(QUESTIONS + "/{id}", id).header("Authorization", actor("CONTENT_EDITOR").bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    private Question question(QuestionSource source, QuestionStatus state) {
        String code = "T05_BRIDGE_" + UUID.randomUUID();
        Question question = new Question(code, QuestionType.MULTIPLE_CHOICE, (short) 1, "Câu hỏi tự soạn");
        question.setSource(source); question.setStatus(state); question.setCorrectKey(source == QuestionSource.AI ? "A" : null);
        if (source == QuestionSource.AI && state == QuestionStatus.APPROVED) {
            question.setReviewedBy(actor("TEACHER").id());
            question.setReviewedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        }
        if (state == QuestionStatus.REJECTED) question.setReviewReason("Lý do từ chối tự soạn");
        question.setExplanation("Lời giải tự soạn");
        question.setOptions(List.of(new QuestionOption("A", "我", true, null, null), new QuestionOption("B", "你", false, null, null)));
        return questions.saveAndFlush(question);
    }

    private ObjectNode body() {
        String kid = "NP1-981";
        KnowledgePoint point = points.findByCode(kid).orElseGet(() -> points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.NP, "示例", (short) 1)));
        assertThat(point.getId()).isPositive();
        ObjectNode body = mapper.createObjectNode().put("stem", "Câu mới tự soạn").put("explanation", "Lời giải sửa tự soạn")
                .put("hskLevel", 1).put("difficulty", 2).put("primaryKid", kid).put("correctKey", "A");
        body.putArray("secondaryKids");
        body.putArray("options").addObject().put("key", "A").put("text", "我").put("isCorrect", false);
        body.withArray("options").addObject().put("key", "B").put("text", "你").put("isCorrect", true);
        return body;
    }

    private Actor actor(String role) {
        User user = users.saveAndFlush(new User("t05-bridge-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người kiểm thử"));
        return new Actor(user.getId(), "Bearer " + jwt.issueAccessToken(user.getId(), List.of(role), true, UUID.randomUUID()));
    }

    private static String key(long actor, long question) { return "ai-review:viewed:" + actor + ":" + question; }
    private record Actor(long id, String bearer) { }
}
