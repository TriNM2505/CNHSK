package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.api.ExamUsageGuard;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ExamPublicationIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ExamAdminService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private ExamUsageGuard guard;
    @MockitoSpyBean private AuditService audit;
    @MockitoSpyBean private ExamAdminStore store;
    private ExamAdminFixture fixture;
    private final List<Long> attemptIds = new ArrayList<>();
    private final List<Long> learnerIds = new ArrayList<>();

    @BeforeEach void setup() { fixture = new ExamAdminFixture(jdbc); }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (long id : attemptIds) {
                jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id=?", id);
                jdbc.update("DELETE FROM learning.attempts WHERE id=?", id);
            }
            for (long id : learnerIds) {
                jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", id);
                jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", id);
            }
            fixture.close();
        });
    }

    @Test
    void publicationMakesTheExamUsableAndArchivingPreservesTheRunningAttempt() throws Exception {
        var draft = service.create(fixture.actor, fixture.input(2, 1));
        String editor = bearer(fixture.actor, "CONTENT_EDITOR");
        JsonNode published = json(mvc.perform(post(path(draft.id(), "publish")).header("Authorization", editor)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("etag", draft.etag(), "actorId", 999))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andReturn().getResponse().getContentAsByteArray());
        assertThat(published.path("etag").asText()).isNotEqualTo(draft.etag());
        var log = jdbc.queryForMap("SELECT actor_id,target_type,detail::text FROM shared.audit_logs WHERE target_id=? AND action='EXAM_PUBLISH'", draft.id());
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(fixture.actor);
        assertThat(log.get("target_type")).isEqualTo("EXAM");
        assertThat(mapper.readTree(log.get("detail").toString())).isEqualTo(mapper.valueToTree(Map.of("before", "DRAFT", "after", "PUBLISHED")));
        long learner = fixture.user();
        learnerIds.add(learner);
        String learnerToken = bearer(learner, "LEARNER");
        assertThat(publicExamIds(learnerToken)).contains(draft.id());
        JsonNode session = json(mvc.perform(post("/api/exams/{id}/attempts", draft.id()).header("Authorization", learnerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        long sessionId = session.path("sessionId").asLong();
        attemptIds.add(sessionId);
        assertThat(session.path("items")).hasSize(2);
        String served = jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, sessionId);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(path(draft.id(), "archive")).header("Authorization", editor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.publishedAt").value(published.path("publishedAt").asText()));
        }
        assertThat(auditCount(draft.id(), "EXAM_ARCHIVE")).isEqualTo(1);
        verify(guard).isInUse(draft.id());
        assertThat(publicExamIds(learnerToken)).doesNotContain(draft.id());
        mvc.perform(post("/api/exams/{id}/attempts", draft.id()).header("Authorization", bearer(fixture.user(), "LEARNER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("EXAM_NOT_PUBLISHED"));
        assertThat(jdbc.queryForObject("SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, sessionId)).isEqualTo(served);
        mvc.perform(post("/api/practice/sessions/{id}/finish", sessionId).header("Authorization", learnerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"no\":1,\"answer\":\"A\",\"durationMs\":2000},{\"no\":2,\"answer\":\"A\",\"durationMs\":2000}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(get("/api/exam-attempts/{id}/result", sessionId).header("Authorization", learnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(2)).andExpect(jsonPath("$.maxScore").value(2));
    }

    @Test
    void publicationCollectsAllFourFailuresAndDoesNotChangeTheDraft() throws Exception {
        var input = fixture.input(2, 3);
        var sections = List.of(input.sections().get(0), new Section("Nghe", SectionType.LISTENING, 0), input.sections().get(2));
        var draft = service.create(fixture.actor, new ExamWrite(input.code(), input.title(), 3, 99, sections, input.questions()));
        jdbc.update("UPDATE learning.questions SET status='PENDING_REVIEW' WHERE id=?", input.questions().get(0).questionId());
        JsonNode error = json(mvc.perform(post(path(draft.id(), "publish")).header("Authorization", bearer(fixture.actor, "CONTENT_EDITOR"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new EtagBody(draft.etag()))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("EXAM_PUBLISH_INVALID"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(error.path("details").findValuesAsText("code"))
                .containsExactly("EMPTY_SECTION", "QUESTION_NOT_APPROVED", "LISTENING_AUDIO_MISSING", "SCORE_MISMATCH");
        assertThat(error.path("details").toString()).doesNotContain("is_correct", "options", "correctKey", "explanation");
        assertThat(service.detail(draft.id()).status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(service.detail(draft.id()).publishedAt()).isNull();
        assertThat(auditCount(draft.id(), "EXAM_PUBLISH")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "status", "kid", "essay"})
    void publicationRechecksCurrentQuestionsAndRejectsIncompleteImportedSections(String kind) throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        long question = draft.questions().get(0).questionId();
        String expected;
        switch (kind) {
            case "legacy" -> {
                jdbc.update("UPDATE learning.exams SET sections='[{\"index\":0,\"name\":\"Phần cũ\"}]'::jsonb WHERE id=?", draft.id());
                expected = "SECTION_TYPE_MISSING";
                assertThat(service.detail(draft.id()).sections()).containsExactly(new SectionView("Phần cũ", null, null));
            }
            case "status" -> { jdbc.update("UPDATE learning.questions SET status='ARCHIVED' WHERE id=?", question); expected = "QUESTION_NOT_APPROVED"; }
            case "kid" -> { jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question); expected = "QUESTION_WITHOUT_KID"; }
            // Essays are graded by teachers, so a machine-graded paper cannot serve them.
            case "essay" -> { jdbc.update("UPDATE learning.questions SET qtype='ESSAY' WHERE id=?", question); expected = "QUESTION_UNSUPPORTED"; }
            default -> throw new AssertionError();
        }
        var current = service.detail(draft.id());
        if (!kind.equals("legacy")) assertThat(current.etag()).isEqualTo(draft.etag());
        assertThatThrownBy(() -> service.publish(draft.id(), fixture.actor, () -> new EtagBody(current.etag())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.EXAM_PUBLISH_INVALID);
                    assertThat(mapper.valueToTree(exception.getExtra()).path("details").findValuesAsText("code")).containsExactly(expected);
                });
        assertThat(service.detail(draft.id()).status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(auditCount(draft.id(), "EXAM_PUBLISH")).isZero();
    }

    @Test
    void guardBlocksArchiveAndItsFailureCannotChangeTheExam() {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        when(guard.isInUse(draft.id())).thenReturn(true);
        assertThatThrownBy(() -> service.archive(draft.id(), fixture.actor)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.EXAM_IN_USE));
        doThrow(new IllegalStateException("guard-fixture")).when(guard).isInUse(draft.id());
        assertThatThrownBy(() -> service.archive(draft.id(), fixture.actor)).isInstanceOf(IllegalStateException.class);
        assertThat(service.detail(draft.id()).status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(auditCount(draft.id(), "EXAM_ARCHIVE")).isZero();
        doReturn(false).when(guard).isInUse(draft.id());
        assertThat(service.archive(draft.id(), fixture.actor).status()).isEqualTo(ExamStatus.ARCHIVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXAM_PUBLISH", "EXAM_ARCHIVE"})
    void auditFailureRollsBackStatusAndPublicationTime(String action) {
        var before = prepare(action);
        doThrow(new IllegalStateException("audit-fixture")).when(audit).recordDetail(eq(fixture.actor), eq(action), eq("EXAM"), eq(before.id()), anyMap());
        long count = auditCount(before.id(), action);
        assertThatThrownBy(() -> transition(action, before)).isInstanceOf(IllegalStateException.class);
        var after = service.detail(before.id());
        assertThat(after).isEqualTo(before);
        assertThat(auditCount(before.id(), action)).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXAM_PUBLISH", "EXAM_ARCHIVE"})
    void failureAfterTheStatusSqlRollsBackWithoutAnAudit(String action) {
        var before = prepare(action);
        clearInvocations(audit);
        org.mockito.stubbing.Answer<Object> fail = call -> { call.callRealMethod(); throw new DataAccessResourceFailureException("write-fixture"); };
        if (action.equals("EXAM_PUBLISH")) doAnswer(fail).when(store).publish(before.id());
        else doAnswer(fail).when(store).archive(before.id());
        assertThatThrownBy(() -> transition(action, before)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(service.detail(before.id())).isEqualTo(before);
        verifyNoInteractions(audit);
    }

    private ExamDetail prepare(String action) {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        return action.equals("EXAM_ARCHIVE") ? service.publish(draft.id(), fixture.actor, () -> new EtagBody(draft.etag())) : draft;
    }
    private ExamDetail transition(String action, ExamDetail before) {
        return action.equals("EXAM_PUBLISH") ? service.publish(before.id(), fixture.actor, () -> new EtagBody(before.etag())) : service.archive(before.id(), fixture.actor);
    }
    private long auditCount(long id, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_type='EXAM' AND target_id=? AND action=?", Long.class, id, action);
    }
    private List<Long> publicExamIds(String token) throws Exception {
        JsonNode list = json(mvc.perform(get("/api/exams").header("Authorization", token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return java.util.stream.StreamSupport.stream(list.spliterator(), false).map(item -> item.path("id").asLong()).toList();
    }
    private JsonNode json(byte[] value) throws Exception { return mapper.readTree(value); }
    private String path(long id, String action) { return "/api/admin/exams/" + id + "/" + action; }
    private String bearer(long user, String role) { return "Bearer " + jwt.issueAccessToken(user, List.of(role), true, UUID.randomUUID()); }
}
