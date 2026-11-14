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
import java.util.stream.IntStream;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ExamAdminIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ExamAdminService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoSpyBean private AuditService audit;
    @MockitoSpyBean private ExamAdminStore store;
    private ExamAdminFixture fixture;

    @BeforeEach void setup() { fixture = new ExamAdminFixture(jdbc); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test
    void allFiveEndpointsPersistACompleteDraftAndCloneWithTheAuthenticatedActor() throws Exception {
        var input = fixture.input(20, 3);
        String bearer = "Bearer " + jwt.issueAccessToken(fixture.actor, List.of("CONTENT_EDITOR"), true, UUID.randomUUID());
        var created = json(mvc.perform(post("/api/admin/exams").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("publicationErrors")).isEmpty();
        assertThat(created.path("sections")).hasSize(3);
        assertThat(created.path("questions")).hasSize(20);
        for (String forbidden : List.of("correctKey", "is_correct", "isCorrect", "options", "explanation")) {
            assertThat(created.findValue(forbidden)).as(forbidden).isNull();
        }
        assertThat(jdbc.queryForObject("SELECT created_by FROM learning.exams WHERE id=?", Long.class, id)).isEqualTo(fixture.actor);
        var stored = json(jdbc.queryForObject("SELECT sections::text FROM learning.exams WHERE id=?", String.class, id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(stored.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("index", "name", "type", "duration_min");
        var links = jdbc.queryForList("SELECT question_id,section_index,order_index,points FROM learning.exam_questions WHERE exam_id=? ORDER BY order_index", id);
        assertThat(links).hasSize(20);
        for (int index = 0; index < 20; index++) {
            assertThat(((Number) links.get(index).get("question_id")).longValue()).isEqualTo(input.questions().get(index).questionId());
            assertThat(((Number) links.get(index).get("order_index")).intValue()).isEqualTo(index);
        }
        mvc.perform(get("/api/admin/exams").param("q", input.code()).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].questionCount").value(20)).andExpect(jsonPath("$.items[0].inUse").value(false));
        mvc.perform(get("/api/admin/exams/" + id).header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etag").value(created.path("etag").asText()));
        var updatedInput = new ExamWrite(input.code(), "Đề đã sửa", 3, 20, input.sections(), input.questions());
        var updated = json(mvc.perform(put("/api/admin/exams/" + id).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ExamAdminServiceTest.update(updatedInput, created.path("etag").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Đề đã sửa"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(updated.path("etag").asText()).isNotEqualTo(created.path("etag").asText());
        String copyCode = fixture.code();
        var clone = json(mvc.perform(post("/api/admin/exams/" + id + "/clone").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("code", copyCode))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Đề đã sửa"))
                .andReturn().getResponse().getContentAsByteArray());
        long cloneId = clone.path("id").asLong();
        assertThat(cloneId).isNotEqualTo(id);
        assertThat(clone.path("questions")).isEqualTo(updated.path("questions"));
        assertThat(clone.path("sections")).isEqualTo(updated.path("sections"));
        assertThat(clone.path("publishedAt").isNull()).isTrue();
        var log = jdbc.queryForMap("SELECT actor_id,target_type,target_id,detail::text FROM shared.audit_logs WHERE actor_id=? AND action='EXAM_CLONE'", fixture.actor);
        assertThat(((Number) log.get("actor_id")).longValue()).isEqualTo(fixture.actor);
        assertThat(log.get("target_type")).isEqualTo("EXAM");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(cloneId);
        assertThat(mapper.readTree(log.get("detail").toString())).isEqualTo(mapper.readTree(mapper.writeValueAsString(Map.of("sourceExamId", id))));
    }

    @Test
    void listingEscapesWildcardsSupportsCombinedFiltersAndPaginatesWithAStableTieBreak() {
        var firstInput = fixture.input(1, 1);
        var first = service.create(fixture.actor, new ExamWrite(firstInput.code(), fixture.prefix + "100%_!", 3, 1,
                firstInput.sections(), firstInput.questions()));
        var secondInput = fixture.input(1, 1);
        var second = service.create(fixture.actor, new ExamWrite(secondInput.code(), fixture.prefix + "100XYZ", 6, 1,
                secondInput.sections(), secondInput.questions()));
        jdbc.update("UPDATE learning.exams SET created_at='2026-01-01T00:00:00Z' WHERE created_by=?", fixture.actor);
        assertThat(service.list(null, null, (fixture.prefix + "100%_!").toLowerCase(java.util.Locale.ROOT), 0, 50).items())
                .extracting(ExamSummary::id).containsExactly(first.id());
        assertThat(service.list(null, null, fixture.prefix, 0, 1).items()).extracting(ExamSummary::id).containsExactly(second.id());
        assertThat(service.list(null, null, fixture.prefix, 1, 1).items()).extracting(ExamSummary::id).containsExactly(first.id());
        assertThat(service.list(null, null, fixture.prefix, 2, 1).items()).isEmpty();
        jdbc.update("UPDATE learning.exams SET status='ARCHIVED' WHERE id=?", second.id());
        var filtered = service.list(ExamStatus.ARCHIVED, 6, fixture.prefix, 0, 20);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.items()).extracting(ExamSummary::id).containsExactly(second.id());
        assertThat(service.list(ExamStatus.DRAFT, 6, fixture.prefix, 0, 20).items()).isEmpty();
    }

    @Test
    void bankMetadataChangesAreVisibleImmediatelyWithoutChangingTheEtag() {
        var input = fixture.input(1, 1);
        var before = service.create(fixture.actor, input);
        long question = input.questions().get(0).questionId();
        jdbc.update("UPDATE learning.questions SET status='ARCHIVED',audio_url='https://example.test/audio.mp3' WHERE id=?", question);
        jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
        var after = service.detail(before.id());
        assertThat(after.etag()).isEqualTo(before.etag());
        assertThat(after.questions().get(0).status()).isEqualTo("ARCHIVED");
        assertThat(after.questions().get(0).hasAudio()).isTrue();
        assertThat(after.questions().get(0).kid()).isNull();
        assertThat(after.publicationErrors()).extracting(PublicationError::code)
                .containsExactly(PublicationErrorCode.QUESTION_NOT_APPROVED, PublicationErrorCode.QUESTION_WITHOUT_KID);
    }

    @Test
    void legacyPublishedExamReadsUnknownFieldsAndClonesItsOriginalJsonUntilExplicitlyEdited() throws Exception {
        String sections = "[{\"index\":2,\"name\":\"Phần nhập cũ\"}]";
        long source = jdbc.queryForObject("""
                INSERT INTO learning.exams(code,title,hsk_level,total_score,sections,status,created_by,published_at)
                VALUES (?, 'Đề nhập cũ',3,1,CAST(? AS jsonb),'PUBLISHED',?,now()) RETURNING id
                """, Long.class, fixture.code(), sections, fixture.actor);
        long question = fixture.question();
        jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index,points) VALUES (?,?,2,0,1)", source, question);
        var detail = service.detail(source);
        assertThat(detail.sections()).containsExactly(new SectionView("Phần nhập cũ", null, null));
        assertThat(detail.publicationErrors()).extracting(PublicationError::code).containsExactly(PublicationErrorCode.SECTION_TYPE_MISSING);
        assertThat(detail.etag()).isEqualTo(service.detail(source).etag());
        String bearer = "Bearer " + jwt.issueAccessToken(fixture.actor, List.of("CONTENT_EDITOR"), true, UUID.randomUUID());
        var body = json(mvc.perform(get("/api/admin/exams/" + source).header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(body.path("sections").get(0).get("type").isNull()).isTrue();
        assertThat(body.path("sections").get(0).get("durationMin").isNull()).isTrue();
        var clone = service.clone(source, fixture.actor, new CloneWrite(fixture.code(), null));
        assertThat(clone.status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(clone.publishedAt()).isNull();
        assertThat(clone.sections()).isEqualTo(detail.sections());
        String rawSource = jdbc.queryForObject("SELECT sections::text FROM learning.exams WHERE id=?", String.class, source);
        String rawClone = jdbc.queryForObject("SELECT sections::text FROM learning.exams WHERE id=?", String.class, clone.id());
        assertThat(mapper.readTree(rawClone)).isEqualTo(mapper.readTree(rawSource)).isEqualTo(mapper.readTree(sections));
        var input = new ExamWrite(clone.code(), clone.title(), 3, 1, List.of(new Section("Đọc", SectionType.READING, 40)),
                List.of(new QuestionWrite(question, 0, 1)));
        var updated = service.update(clone.id(), () -> ExamAdminServiceTest.update(input, clone.etag()));
        assertThat(updated.publicationErrors()).isEmpty();
        assertThat(updated.sections()).containsExactly(new SectionView("Đọc", SectionType.READING, 40));
        assertThat(service.detail(source).etag()).isEqualTo(detail.etag());
    }

    @Test
    void failedLinkWritesRollbackBothTheNewExamAndAllLinks() {
        var input = fixture.input(2, 1);
        doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataAccessResourceFailureException("link-write-fixture"); })
                .when(store).replaceLinks(anyLong(), anyList());
        assertThatThrownBy(() -> service.create(fixture.actor, input))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class).hasMessage("link-write-fixture");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.exams WHERE code=?", Integer.class, input.code())).isZero();
    }

    @Test
    void failedUpdateRestoresTheOldFieldsAndLinks() {
        var input = fixture.input(2, 1);
        var before = service.create(fixture.actor, input);
        var oldLinks = jdbc.queryForList("SELECT * FROM learning.exam_questions WHERE exam_id=? ORDER BY order_index", before.id());
        var changed = new ExamWrite(input.code(), "Sửa dở", 3, 1, input.sections(), List.of(input.questions().get(1)));
        doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataAccessResourceFailureException("update-fixture"); })
                .when(store).replaceLinks(eq(before.id()), anyList());
        assertThatThrownBy(() -> service.update(before.id(), () -> ExamAdminServiceTest.update(changed, before.etag())))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class).hasMessage("update-fixture");
        assertThat(jdbc.queryForObject("SELECT title FROM learning.exams WHERE id=?", String.class, before.id())).isEqualTo(input.title());
        assertThat(jdbc.queryForList("SELECT * FROM learning.exam_questions WHERE exam_id=? ORDER BY order_index", before.id())).isEqualTo(oldLinks);
    }

    @Test
    void auditFailureRollsBackTheCloneInTheSameTransaction() {
        var source = service.create(fixture.actor, fixture.input(1, 1));
        String code = fixture.code();
        doThrow(new IllegalStateException("audit-fixture")).when(audit).recordDetail(eq(fixture.actor), eq("EXAM_CLONE"), eq("EXAM"), anyLong(), anyMap());
        assertThatThrownBy(() -> service.clone(source.id(), fixture.actor, new CloneWrite(code, null))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.exams WHERE code=?", Integer.class, code)).isZero();
        assertThat(service.detail(source.id()).etag()).isEqualTo(source.etag());
    }

    @Test
    void duplicateCodeOnCreateUpdateAndCloneReturns409AndLeavesTheSourceUntouched() {
        var firstInput = fixture.input(1, 1);
        var first = service.create(fixture.actor, firstInput);
        var secondInput = fixture.input(1, 1);
        var second = service.create(fixture.actor, secondInput);
        assertCode(() -> service.create(fixture.actor, firstInput), ErrorCode.EXAM_CODE_TAKEN);
        var duplicate = new ExamWrite(first.code(), "Trùng mã", 3, 1, secondInput.sections(), secondInput.questions());
        assertCode(() -> service.update(second.id(), () -> ExamAdminServiceTest.update(duplicate, second.etag())), ErrorCode.EXAM_CODE_TAKEN);
        assertCode(() -> service.clone(second.id(), fixture.actor, new CloneWrite(first.code(), null)), ErrorCode.EXAM_CODE_TAKEN);
        assertThat(service.detail(first.id()).etag()).isEqualTo(first.etag());
        assertThat(service.detail(second.id()).etag()).isEqualTo(second.etag());
    }

    @ParameterizedTest
    @EnumSource(value = ExamStatus.class, names = {"PUBLISHED", "ARCHIVED"})
    void immutableExamsCanBeClonedButCannotBeEditedEvenWithMalformedJson(ExamStatus status) throws Exception {
        var source = service.create(fixture.actor, fixture.input(1, 1));
        jdbc.update("UPDATE learning.exams SET status=?,published_at=now() WHERE id=?", status.name(), source.id());
        String bearer = "Bearer " + jwt.issueAccessToken(fixture.actor, List.of("CONTENT_EDITOR"), true, UUID.randomUUID());
        mvc.perform(put("/api/admin/exams/" + source.id()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("EXAM_NOT_EDITABLE"));
        var clone = service.clone(source.id(), fixture.actor, new CloneWrite(fixture.code(), "Bản sao"));
        assertThat(clone.status()).isEqualTo(ExamStatus.DRAFT);
        assertThat(clone.title()).isEqualTo("Bản sao");
    }

    private JsonNode json(byte[] value) throws Exception { return mapper.readTree(value); }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}

class ExamAdminFixture implements AutoCloseable {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private final JdbcTemplate jdbc;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> questions = new ArrayList<>();
    final String prefix = "EA-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(java.util.Locale.ROOT);
    final long actor;
    private Long kid;

    ExamAdminFixture(JdbcTemplate jdbc) { this.jdbc = jdbc; actor = user(); }

    long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Exam fixture') RETURNING id", Long.class,
                "exam-admin-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        users.add(id);
        return id;
    }

    String code() { return "EA-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT); }

    ExamWrite input(int count, int parts) {
        List<Section> sections = IntStream.range(0, parts).mapToObj(index -> new Section("Phần " + index, SectionType.READING, 0)).toList();
        List<QuestionWrite> links = IntStream.range(0, count).mapToObj(index -> new QuestionWrite(question(), index % parts, 1)).toList();
        return new ExamWrite(code(), prefix + " Đề tự soạn", 3, Math.max(1, count), sections, links);
    }

    long question() {
        if (kid == null) {
            String code = jdbc.queryForObject("SELECT 'NP3-'||lpad(n::text,3,'0') FROM generate_series(800,999) n "
                    + "WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points WHERE code='NP3-'||lpad(n::text,3,'0')) ORDER BY n LIMIT 1", String.class);
            kid = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,hsk_level,domain,name_zh) VALUES (?,'KID tự soạn',3,'NP','测试') RETURNING id", Long.class, code);
        }
        long id = jdbc.queryForObject("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,status)
                VALUES (?,'MULTIPLE_CHOICE',3,'Câu tự soạn',
                    '[{"key":"A","text":"Một","is_correct":true},{"key":"B","text":"Hai","is_correct":false}]'::jsonb,'APPROVED') RETURNING id
                """, Long.class, code());
        questions.add(id);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", id, kid);
        return id;
    }

    @Override public void close() {
        var named = new NamedParameterJdbcTemplate(jdbc);
        var args = Map.of("users", users);
        named.update("DELETE FROM shared.audit_logs WHERE actor_id IN (:users)", args);
        named.update("DELETE FROM learning.exam_questions WHERE exam_id IN (SELECT id FROM learning.exams WHERE created_by IN (:users))", args);
        named.update("DELETE FROM learning.exams WHERE created_by IN (:users)", args);
        if (!questions.isEmpty()) {
            named.update("DELETE FROM learning.question_knowledge_points WHERE question_id IN (:ids)", Map.of("ids", questions));
            named.update("DELETE FROM learning.questions WHERE id IN (:ids)", Map.of("ids", questions));
        }
        if (kid != null) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", kid);
        named.update("DELETE FROM auth.users WHERE id IN (:users)", args);
    }
}
