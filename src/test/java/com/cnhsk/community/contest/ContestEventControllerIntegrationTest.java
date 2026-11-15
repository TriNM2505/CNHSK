package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ContestEventControllerIntegrationTest extends ContestFixtures {
    @Test
    void AT15_threeTabEventsProduceOneAuditAndNeverChangeScoreOrSuspicion() throws Exception {
        var session = response(enter(learner), 201);
        long id = session.path("sessionId").asLong();
        var before = jdbc.queryForMap("SELECT status,score,max_score,suspicious FROM learning.attempts WHERE id=?", id);
        for (int index = 0; index < 3; index++) response(event(learner, contest, "{\"type\":\"TAB_HIDDEN\",\"userId\":" + creator + "}"), 204);
        assertThat(countEvents(learner, contest)).isEqualTo(1);
        var detail = mapper.readTree(jdbc.queryForObject("SELECT detail::text FROM shared.audit_logs WHERE actor_id=? AND action='CONTEST_CHEAT_EVENT' AND target_id=?",
                String.class, learner, contest));
        assertThat(fields(detail)).containsExactly("type");
        assertThat(detail.path("type").asText()).isEqualTo("TAB_HIDDEN");
        assertThat(jdbc.queryForObject("SELECT target_type FROM shared.audit_logs WHERE actor_id=? AND action='CONTEST_CHEAT_EVENT' AND target_id=?",
                String.class, learner, contest)).isEqualTo("CONTEST");
        assertThat(jdbc.queryForMap("SELECT status,score,max_score,suspicious FROM learning.attempts WHERE id=?", id)).isEqualTo(before);
        Long ttl = redis.getExpire("contest:tab-hidden:" + learner, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 10L);
    }

    @Test
    void FR013_rateLimitBelongsToTheUserAcrossContestsAndOtherUsersHaveTheirOwnWindow() throws Exception {
        attempt(learner, contest, "IN_PROGRESS", null, null, null, false);
        long second = contest(-60, 3600, null);
        attempt(learner, second, "IN_PROGRESS", null, null, null, false);
        long other = user(true);
        attempt(other, contest, "IN_PROGRESS", null, null, null, false);
        response(event(learner, contest, "{\"type\":\"TAB_HIDDEN\"}"), 204);
        response(event(learner, second, "{\"type\":\"TAB_HIDDEN\"}"), 204);
        response(event(other, contest, "{\"type\":\"TAB_HIDDEN\"}"), 204);
        assertThat(countEvents(learner, contest)).isEqualTo(1);
        assertThat(countEvents(learner, second)).isZero();
        assertThat(countEvents(other, contest)).isEqualTo(1);
    }

    @Test
    void AT15_missingOwnAttemptCannotUseSomeoneElsesAttemptOrConsumeTheRateWindow() throws Exception {
        attempt(creator, contest, "IN_PROGRESS", null, null, null, false);
        error(event(learner, contest, "{\"type\":\"TAB_HIDDEN\"}"), 404, "CONTEST_NOT_FOUND");
        assertThat(countEvents(learner, contest)).isZero();
        assertThat(redis.hasKey("contest:tab-hidden:" + learner)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "EXPIRED", "ABANDONED"})
    void FR013_onlyInProgressAttemptsMayReportEvents(String state) throws Exception {
        attempt(learner, contest, state, null, null, null, false);
        error(event(learner, contest, "{\"type\":\"TAB_HIDDEN\"}"), 404, "CONTEST_NOT_FOUND");
        assertThat(countEvents(learner, contest)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "not-json", "{\"type\":null}", "{\"type\":\"OTHER\"}", "{\"type\":\"tab_hidden\"}"})
    void FR013_invalidPayloadReturns422WithoutAuditOrRateConsumption(String body) throws Exception {
        error(event(learner, contest, body), 422, "VALIDATION_FAILED");
        assertThat(countEvents(learner, contest)).isZero();
        assertThat(redis.hasKey("contest:tab-hidden:" + learner)).isFalse();
    }

    private MockHttpServletRequestBuilder event(long user, long id, String body) {
        return post(BASE + "/{id}/attempts/events", id).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long countEvents(long user, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND target_id=? AND action='CONTEST_CHEAT_EVENT'",
                Long.class, user, id);
    }
}
