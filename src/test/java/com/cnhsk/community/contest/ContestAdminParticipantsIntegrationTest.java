package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.shared.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ContestAdminParticipantsIntegrationTest extends ContestFixtures {
    private static final String ADMIN = "/api/admin/contests";
    @MockitoSpyBean AuditService audit;

    @Test
    void FR022_listsEveryStateWithOneProfileBatchAndOneScopedAuditAggregate() throws Exception {
        endContest();
        long other = user(true);
        long inProgress = user(true);
        attempt(learner, contest, "SUBMITTED", 8L, 10L, 2000, true);
        attempt(other, contest, "EXPIRED", null, null, null, false);
        attempt(inProgress, contest, "IN_PROGRESS", null, null, null, false);
        long otherContest = contest(-120, 3600, null);
        audit.recordDetail(learner, "CONTEST_CHEAT_EVENT", "CONTEST", contest, Map.of("type", "TAB_HIDDEN"));
        audit.recordDetail(learner, "CONTEST_CHEAT_EVENT", "CONTEST", contest, Map.of("type", "TAB_HIDDEN"));
        audit.recordDetail(other, "CONTEST_CHEAT_EVENT", "CONTEST", contest, Map.of("type", "TAB_HIDDEN"));
        audit.recordDetail(learner, "CONTEST_CHEAT_EVENT", "CONTEST", otherContest, Map.of());
        audit.recordDetail(learner, "CONTEST_CHEAT_EVENT", "OTHER", contest, Map.of());
        audit.recordDetail(learner, "OTHER_EVENT", "CONTEST", contest, Map.of());
        clearInvocations(auth, audit, attempts);

        JsonNode rows = response(editor(get(ADMIN + "/{id}/participants", contest)), 200);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).path("userId").asLong()).isEqualTo(learner);
        assertThat(rows.get(0).path("suspicious").asBoolean()).isTrue();
        assertThat(rows.get(0).path("cheatEventCount").asLong()).isEqualTo(2);
        assertThat(rows.get(0).path("displayName").asText()).isEqualTo("Thí sinh tự soạn");
        assertThat(time(rows.get(0), "submittedAt")).isEqualTo(NOW.minusSeconds(5));
        for (JsonNode row : rows) {
            if (row.path("userId").asLong() == other) assertThat(row.path("cheatEventCount").asLong()).isEqualTo(1);
            if (row.path("userId").asLong() == inProgress) assertThat(row.path("cheatEventCount").asLong()).isZero();
            if (row.path("userId").asLong() != learner) {
                assertThat(row.path("score").isNull()).isTrue();
                assertThat(row.path("submittedAt").isNull()).isTrue();
            }
        }
        verify(auth, times(1)).publicProfiles(Set.of(learner, other, inProgress));
        verify(audit, times(1)).countByActors("CONTEST_CHEAT_EVENT", "CONTEST", contest);
        verify(attempts, times(1)).participants(contest);
        noPrivateFields(rows);
    }

    @Test
    void FR022_missingPublicProfileRemainsNullAndNoEventIsZero() throws Exception {
        endContest();
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        doReturn(Map.of()).when(auth).publicProfiles(Set.of(learner));
        JsonNode row = response(editor(get(ADMIN + "/{id}/participants", contest)), 200).get(0);
        assertThat(row.has("displayName")).isTrue();
        assertThat(row.path("displayName").isNull()).isTrue();
        assertThat(row.path("cheatEventCount").asLong()).isZero();
    }

    @Test
    void AT12_includingSuspiciousAndExcludingOrdinaryAttemptsChangesRankingAndWritesAudits() throws Exception {
        endContest();
        long other = user(true);
        attempt(learner, contest, "SUBMITTED", 8L, 10L, 2000, true);
        attempt(other, contest, "SUBMITTED", 7L, 10L, 3000, false);
        assertThat(attempts.rankOf(learner, contest)).isEmpty();
        JsonNode kept = response(decision(learner, false, "Đã kiểm tra lượt tự soạn"), 200);
        assertThat(kept.path("suspicious").asBoolean()).isFalse();
        assertThat(attempts.rankOf(learner, contest)).contains(1L);
        JsonNode excluded = response(decision(other, true, "Ghi chú quyết định tự soạn"), 200);
        assertThat(excluded.path("suspicious").asBoolean()).isTrue();
        assertThat(attempts.rankOf(other, contest)).isEmpty();
        assertThat(attempts.ranking(contest, 0, 20).total()).isEqualTo(1);
        JsonNode auditRow = mapper.readTree(jdbc.queryForObject("SELECT detail::text FROM shared.audit_logs "
                + "WHERE action='CONTEST_PARTICIPANT_EXCLUDE' AND actor_id=? AND target_type='CONTEST' AND target_id=?", String.class, creator, contest));
        assertThat(auditRow.path("userId").asLong()).isEqualTo(other);
        assertThat(auditRow.path("note").asText()).isEqualTo("Ghi chú quyết định tự soạn");
        assertThat(audits("CONTEST_PARTICIPANT_INCLUDE")).isEqualTo(1);
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isEqualTo(1);
        noPrivateFields(kept);
        noPrivateFields(excluded);
    }

    @ParameterizedTest
    @ValueSource(longs = {-61, -1})
    void FR022_getAndPatchAreLockedBeforeTheContestEnds(long seconds) throws Exception {
        endContest();
        now = NOW.plusSeconds(seconds);
        error(editor(get(ADMIN + "/{id}/participants", contest)), 409, "CONTEST_LOCKED");
        error(decision(learner, true, "Tự soạn"), 409, "CONTEST_LOCKED");
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isZero();
    }

    @Test
    void AT12_decisionsAreLockedAfterRankingPublicationButParticipantsRemainReadable() throws Exception {
        endContest();
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        response(editor(post(ADMIN + "/{id}/ranking/publish", contest)), 200);
        error(decision(learner, true, "Tự soạn"), 409, "CONTEST_LOCKED");
        assertThat(attempts.rankOf(learner, contest)).contains(1L);
        response(editor(get(ADMIN + "/{id}/participants", contest)), 200);
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "EXPIRED", "ABANDONED", "MISSING"})
    void FR022_onlySubmittedParticipantsCanBeAdjudicated(String state) throws Exception {
        endContest();
        if (!state.equals("MISSING")) attempt(learner, contest, state, null, null, null, false);
        error(decision(learner, true, "Tự soạn"), 404, "CONTEST_NOT_FOUND");
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"excluded\":true}", "{\"excluded\":null,\"note\":\"Tự soạn\"}",
            "{\"excluded\":true,\"note\":\"\"}", "{\"excluded\":true,\"note\":\"   \"}"})
    void FR022_decisionRequiresBooleanAndNonblankNote(String body) throws Exception {
        endContest();
        error(editor(patch(ADMIN + "/{id}/participants/{userId}", contest, learner))
                .contentType(MediaType.APPLICATION_JSON).content(body), 422, "VALIDATION_FAILED");
    }

    @Test
    void FR022_noteAccepts500CharactersAndRejects501() throws Exception {
        endContest();
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        error(decision(learner, true, "x".repeat(501)), 422, "VALIDATION_FAILED");
        response(decision(learner, true, "x".repeat(500)), 200);
    }

    @Test
    void AT12_auditFailureRollsBackTheParticipantDecision() throws Exception {
        endContest();
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(anyLong(), eq("CONTEST_PARTICIPANT_EXCLUDE"), eq("CONTEST"), eq(contest), any());
        error(decision(learner, true, "Tự soạn"), 500, "INTERNAL_ERROR");
        assertThat(attempts.myAttempt(learner, contest).orElseThrow().suspicious()).isFalse();
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void AT17_rankingPublicationUsesTheEndBoundaryAndIsWrittenOnce(long seconds) throws Exception {
        now = NOW.plusSeconds(3600 + seconds);
        var request = editor(post(ADMIN + "/{id}/ranking/publish", contest));
        if (seconds < 0) {
            error(request, 409, "CONTEST_RANKING_TOO_EARLY");
            assertThat(audits("CONTEST_RANKING_PUBLISH")).isZero();
        } else {
            JsonNode published = response(request, 200);
            assertThat(time(published, "rankingPublishedAt")).isEqualTo(now);
            now = now.plusSeconds(10);
            error(editor(post(ADMIN + "/{id}/ranking/publish", contest)), 409, "CONTEST_LOCKED");
            assertThat(audits("CONTEST_RANKING_PUBLISH")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT ranking_published_at FROM community.contests WHERE id=?", Timestamp.class, contest).toInstant())
                    .isEqualTo(NOW.plusSeconds(3600 + seconds));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CANCELLED"})
    void FR023_onlyPublishedContestsCanPublishRanking(String state) throws Exception {
        endContest();
        jdbc.update("UPDATE community.contests SET status=? WHERE id=?", state, contest);
        error(editor(post(ADMIN + "/{id}/ranking/publish", contest)), 409, "CONTEST_LOCKED");
    }

    @Test
    void FR023_auditFailureRollsBackPublication() throws Exception {
        endContest();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(anyLong(), eq("CONTEST_RANKING_PUBLISH"), eq("CONTEST"), eq(contest), any());
        error(editor(post(ADMIN + "/{id}/ranking/publish", contest)), 500, "INTERNAL_ERROR");
        assertThat(jdbc.queryForObject("SELECT ranking_published_at FROM community.contests WHERE id=?", Timestamp.class, contest)).isNull();
        assertThat(audits("CONTEST_RANKING_PUBLISH")).isZero();
    }

    private void endContest() {
        jdbc.update("UPDATE community.contests SET ends_at=? WHERE id=?", Timestamp.from(NOW), contest);
    }

    private long audits(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action=? "
                + "AND target_type='CONTEST' AND target_id=?", Long.class, creator, action, contest);
    }

    private MockHttpServletRequestBuilder decision(long userId, boolean excluded, String note) {
        return editor(patch(ADMIN + "/{id}/participants/{userId}", contest, userId)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode().put("excluded", excluded).put("note", note).toString());
    }

    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(creator, "CONTENT_EDITOR"));
    }
}
