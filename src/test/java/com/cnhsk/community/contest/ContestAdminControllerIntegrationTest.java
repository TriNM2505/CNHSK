package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.cnhsk.auth.api.RoleCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ContestAdminControllerIntegrationTest extends ContestFixtures {
    private static final String ADMIN = "/api/admin/contests";

    @Test
    void FR018_createsDraftForTheCallerWithRequiredNullsAndVietnamTimes() throws Exception {
        ObjectNode input = input();
        input.putNull("examId");
        JsonNode created = create(input);
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("state").asText()).isEqualTo("UPCOMING");
        assertThat(created.path("participantCount").asLong()).isZero();
        for (String key : List.of("description", "examId", "examTitle", "maxParticipants", "rankingPublishedAt")) {
            assertThat(created.has(key)).as(key).isTrue();
            assertThat(created.path(key).isNull()).as(key).isTrue();
        }
        assertThat(created.path("prizes").isEmpty()).isTrue();
        assertThat(time(created, "startsAt")).isEqualTo(NOW.plusSeconds(60));
        assertThat(time(created, "endsAt")).isEqualTo(NOW.plusSeconds(3600));
        assertThat(jdbc.queryForObject("SELECT created_by FROM community.contests WHERE id=?", Long.class, id)).isEqualTo(creator);
        assertThat(response(editor(get(ADMIN + "/{id}", id)), 200)).isEqualTo(created);
        noPrivateFields(created);
    }

    @Test
    void FR017_listsEveryStatusWithExamTitleAttemptCountAndDescendingStartTime() throws Exception {
        long draft = create(input()).path("id").asLong();
        long cancelled = contest(120, 4000, null);
        jdbc.update("UPDATE community.contests SET status='CANCELLED' WHERE id=?", cancelled);
        attempt(learner, contest, "ABANDONED", null, null, null, false);
        JsonNode page = response(editor(get(ADMIN)), 200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("items").findValuesAsText("id"))
                .containsExactly(Long.toString(cancelled), Long.toString(draft), Long.toString(contest));
        JsonNode active = page.path("items").get(2);
        assertThat(active.path("examTitle").asText()).isEqualTo("Đề tự soạn T-05");
        assertThat(active.path("participantCount").asLong()).isEqualTo(1);
        JsonNode filtered = response(editor(get(ADMIN).param("status", "DRAFT").param("size", "1")), 200);
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("id").asLong()).isEqualTo(draft);
        JsonNode beyond = response(editor(get(ADMIN).param("page", "2147483647").param("size", "50")), 200);
        assertThat(beyond.path("items").isEmpty()).isTrue();
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(3);
        noPrivateFields(page);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,invalid", "size,0", "size,51", "size,invalid", "status,ONGOING"})
    void FR017_invalidFiltersAndPagingReturn422(String field, String value) throws Exception {
        error(editor(get(ADMIN).param(field, value)), 422, "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            name|null
            name|""
            startsAt|null
            startsAt|"invalid"
            endsAt|null
            maxParticipants|0
            maxParticipants|100001
            prizes|[null]
            prizes|[{"fromRank":0,"toRank":1,"description":"Tự soạn"}]
            prizes|[{"fromRank":1,"toRank":0,"description":"Tự soạn"}]
            prizes|[{"fromRank":1,"toRank":1}]
            """)
    void FR018_invalidBodyReturns422WithoutWriting(String field, String json) throws Exception {
        ObjectNode body = input();
        body.set(field, mapper.readTree(json));
        long before = jdbc.queryForObject("SELECT count(*) FROM community.contests", Long.class);
        error(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.contests", Long.class)).isEqualTo(before);
    }

    @Test
    void FR018_rejectsLengthsAndPrizeCountAboveContractLimits() throws Exception {
        for (String field : List.of("name", "description")) {
            ObjectNode body = input();
            body.put(field, "x".repeat(field.equals("name") ? 201 : 5001));
            error(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
        }
        ObjectNode body = input();
        var prizes = body.putArray("prizes");
        for (int i = 0; i < 11; i++) prizes.addObject().put("fromRank", 1).put("toRank", 1).put("description", "Tự soạn");
        error(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
        prizes.removeAll();
        prizes.addObject().put("fromRank", 1).put("toRank", 1).put("description", "x".repeat(201));
        error(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void FR018_startMustBeStrictlyBeforeEnd(long difference) throws Exception {
        ObjectNode body = input();
        body.put("endsAt", NOW.plusSeconds(60 + difference).toString());
        error(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
    }

    @Test
    void FR018_fullBodyAndDraftUpdatePreserveNullableExamAndPrizeValues() throws Exception {
        ObjectNode body = input();
        body.put("description", "Mô tả cuộc thi tự soạn").put("maxParticipants", 100000);
        body.putArray("prizes").addObject().put("fromRank", 1).put("toRank", 2).put("description", "Giải tự soạn");
        JsonNode created = create(body);
        long id = created.path("id").asLong();
        assertThat(created.path("prizes")).isEqualTo(body.path("prizes"));
        body.put("name", "Đã sửa").putNull("examId");
        body.put("startsAt", NOW.minusSeconds(120).toString());
        clearInvocations(attempts);
        JsonNode changed = response(editor(put(ADMIN + "/{id}", id)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 200);
        assertThat(changed.path("name").asText()).isEqualTo("Đã sửa");
        assertThat(changed.path("examId").isNull()).isTrue();
        verify(attempts, never()).exam(anyLong());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void AT16_publishedContestLocksAtAndAfterItsOriginalStart(long seconds) throws Exception {
        long id = contest(seconds, 3600, null);
        var request = editor(put(ADMIN + "/{id}", id)).contentType(MediaType.APPLICATION_JSON).content(input().toString());
        if (seconds > 0) response(request, 200);
        else error(request, 409, "CONTEST_LOCKED");
    }

    @Test
    void AT16_cancelledContestCannotBeEditedOrPublishedAgain() throws Exception {
        jdbc.update("UPDATE community.contests SET status='CANCELLED' WHERE id=?", contest);
        error(editor(put(ADMIN + "/{id}", contest)).contentType(MediaType.APPLICATION_JSON).content(input().toString()), 409, "CONTEST_LOCKED");
        error(editor(post(ADMIN + "/{id}/publish", contest)), 409, "CONTEST_LOCKED");
        error(editor(post(ADMIN + "/{id}/cancel", contest)), 409, "CONTEST_LOCKED");
    }

    @Test
    void FR019_publishedEditRejectsPastStartAndInvalidTimeRangeWithoutChangingData() throws Exception {
        long id = contest(60, 3600, null);
        ObjectNode body = input();
        body.put("startsAt", NOW.minusSeconds(1).toString());
        error(editor(put(ADMIN + "/{id}", id)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
        body.put("startsAt", body.path("endsAt").asText());
        error(editor(put(ADMIN + "/{id}", id)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT starts_at FROM community.contests WHERE id=?", Timestamp.class, id).toInstant())
                .isEqualTo(NOW.plusSeconds(60));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "MISSING", "ARCHIVED", "EMPTY", "UNUSABLE"})
    void AT16_publishAndPublishedEditRejectInvalidExamsBeforeWriting(String invalid) throws Exception {
        Long invalidExam = invalidExam(invalid);
        long id = create(input()).path("id").asLong();
        jdbc.update("UPDATE community.contests SET exam_id=? WHERE id=?", invalid.equals("MISSING") ? null : invalidExam, id);
        // Missing references cannot be stored due to the FK; exercise that input on PUT instead.
        error(editor(post(ADMIN + "/{id}/publish", id)), 422, "CONTEST_EXAM_INVALID");
        assertThat(jdbc.queryForObject("SELECT status FROM community.contests WHERE id=?", String.class, id)).isEqualTo("DRAFT");
        long published = contest(60, 3600, null);
        ObjectNode body = input();
        if (invalidExam == null) body.putNull("examId"); else body.put("examId", invalidExam);
        error(editor(put(ADMIN + "/{id}", published)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422, "CONTEST_EXAM_INVALID");
        assertThat(jdbc.queryForObject("SELECT exam_id FROM community.contests WHERE id=?", Long.class, published)).isEqualTo(exam);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED"})
    void FR020_acceptsUsableDraftAndPublishedExams(String status) throws Exception {
        jdbc.update("UPDATE learning.exams SET status=? WHERE id=?", status, exam);
        long id = create(input()).path("id").asLong();
        JsonNode result = response(editor(post(ADMIN + "/{id}/publish", id)), 200);
        assertThat(result.path("status").asText()).isEqualTo("PUBLISHED");
        error(editor(post(ADMIN + "/{id}/publish", id)), 409, "CONTEST_LOCKED");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void FR020_publishRequiresStartStrictlyInTheFuture(long seconds) throws Exception {
        long id = create(input()).path("id").asLong();
        jdbc.update("UPDATE community.contests SET starts_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(seconds)), id);
        error(editor(post(ADMIN + "/{id}/publish", id)), 422, "VALIDATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "SUBMITTED", "ABANDONED", "EXPIRED"})
    void AT16_anyExistingAttemptPreventsCancellation(String state) throws Exception {
        attempt(learner, contest, state, null, null, null, false);
        error(editor(post(ADMIN + "/{id}/cancel", contest)), 409, "CONTEST_HAS_ATTEMPTS");
        assertThat(jdbc.queryForObject("SELECT status FROM community.contests WHERE id=?", String.class, contest)).isEqualTo("PUBLISHED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED"})
    void AT16_cancelWithoutAttemptsKeepsTheRowAndSetsCancelled(String status) throws Exception {
        jdbc.update("UPDATE community.contests SET status=? WHERE id=?", status, contest);
        JsonNode result = response(editor(post(ADMIN + "/{id}/cancel", contest)), 200);
        assertThat(result.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.contests WHERE id=?", Integer.class, contest)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, mode = EnumSource.Mode.EXCLUDE, names = "CONTENT_EDITOR")
    void onlyContentEditorsCanUseEveryAdminOperation(RoleCode role) throws Exception {
        for (var request : operations()) error(request.header("Authorization", bearer(creator, role.name())), 403, "FORBIDDEN");
    }

    @Test
    void guestsReceive401ForEveryAdminOperation() throws Exception {
        for (var request : operations()) error(request, 401, "UNAUTHENTICATED");
    }

    @Test
    void editorCookieRequiresCsrfAndAnotherEditorMayManageTheContest() throws Exception {
        Cookie cookie = new Cookie("access_token", bearer(creator, "CONTENT_EDITOR").substring(7));
        error(post(ADMIN).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(input().toString()), 403, "CSRF_TOKEN_INVALID");
        JsonNode created = response(post(ADMIN).cookie(cookie, new Cookie("XSRF-TOKEN", "contest-admin-test"))
                .header("X-XSRF-TOKEN", "contest-admin-test").contentType(MediaType.APPLICATION_JSON).content(input().toString()), 201);
        contests.add(created.path("id").asLong());
        response(get(ADMIN + "/{id}", created.path("id").asLong())
                .header("Authorization", bearer(learner, "CONTENT_EDITOR", "LEARNER")), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808", "9223372036854775807"})
    void missingAndInvalidContestIdsUse404(String id) throws Exception {
        error(editor(get(ADMIN + "/" + id)), 404, "CONTEST_NOT_FOUND");
    }

    private Long invalidExam(String kind) {
        if (kind.equals("NULL")) return null;
        if (kind.equals("MISSING")) return Long.MAX_VALUE;
        long id = exam(15);
        if (kind.equals("ARCHIVED")) jdbc.update("UPDATE learning.exams SET status='ARCHIVED' WHERE id=?", id);
        if (kind.equals("UNUSABLE")) {
            question(id);
            jdbc.update("UPDATE learning.questions SET status='PENDING_REVIEW' WHERE id IN (SELECT question_id FROM learning.exam_questions WHERE exam_id=?)", id);
        }
        return id;
    }

    private ObjectNode input() {
        return mapper.createObjectNode().put("name", "Cuộc thi quản trị tự soạn").put("examId", exam)
                .put("startsAt", NOW.plusSeconds(60).toString()).put("endsAt", NOW.plusSeconds(3600).toString());
    }

    private JsonNode create(ObjectNode body) throws Exception {
        JsonNode created = response(editor(post(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body.toString()), 201);
        contests.add(created.path("id").asLong());
        return created;
    }

    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(creator, "CONTENT_EDITOR"));
    }

    private List<MockHttpServletRequestBuilder> operations() {
        return List.of(get(ADMIN), post(ADMIN), get(ADMIN + "/{id}", contest), put(ADMIN + "/{id}", contest),
                post(ADMIN + "/{id}/publish", contest), post(ADMIN + "/{id}/cancel", contest),
                get(ADMIN + "/{id}/participants", contest), patch(ADMIN + "/{id}/participants/{userId}", contest, learner),
                post(ADMIN + "/{id}/ranking/publish", contest));
    }
}
