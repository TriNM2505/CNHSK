package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class ContestAttemptControllerIntegrationTest extends ContestFixtures {
    @ParameterizedTest
    @CsvSource({"-1,422", "0,201", "59999999,201", "60000000,422"})
    void AT05_clockControlsBothEntryBoundariesAtMicrosecondPrecision(long micros, int expected) throws Exception {
        contest = contest(0, 60, null);
        now = NOW.plusNanos(micros * 1000);
        if (expected == 422) error(enter(learner), 422, "CONTEST_NOT_OPEN");
        else {
            var session = response(enter(learner), 201);
            assertThat(time(session, "deadline")).isEqualTo(NOW.plusSeconds(60));
            assertThat(time(session, "serverTime")).isEqualTo(now);
        }
    }

    @Test
    void AT05_newSessionUsesDurationAndResumeKeepsOriginalSnapshotWhenExamChanges() throws Exception {
        var first = response(enter(learner), 201);
        assertThat(fields(first)).containsExactlyInAnyOrder("sessionId", "contestId", "deadline", "serverTime", "resumed", "items");
        assertThat(first.path("contestId").asLong()).isEqualTo(contest);
        assertThat(first.path("resumed").asBoolean()).isFalse();
        assertThat(first.path("items")).hasSize(1);
        assertThat(time(first, "deadline")).isEqualTo(NOW.plusSeconds(900));
        noPrivateFields(first);
        jdbc.update("UPDATE learning.exams SET status='ARCHIVED',sections='[]'::jsonb WHERE id=?", exam);
        now = NOW.plusSeconds(10);
        var resumed = response(enter(learner), 200);
        assertThat(resumed.path("resumed").asBoolean()).isTrue();
        for (String field : List.of("sessionId", "deadline", "items")) assertThat(resumed.path(field)).isEqualTo(first.path(field));
        assertThat(time(resumed, "serverTime")).isEqualTo(now);
        assertThat(attempts.countAttempts(contest)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void AT05_zeroDurationOrLongExamEndsAtTheContestDeadline(int duration) throws Exception {
        jdbc.update("UPDATE learning.exams SET sections=?::jsonb WHERE id=?", "[{\"duration_min\":" + duration + "}]", exam);
        var session = response(enter(learner), 201);
        assertThat(time(session, "deadline")).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void FR011_validationOrderIsPublishedVerifiedCreatorScheduleThenCapacity() throws Exception {
        jdbc.update("UPDATE community.contests SET status='DRAFT',max_participants=1 WHERE id=?", contest);
        jdbc.update("UPDATE auth.users SET email_verified_at=NULL WHERE id=?", creator);
        now = NOW.plusSeconds(3600);
        error(enter(creator), 404, "CONTEST_NOT_FOUND");
        jdbc.update("UPDATE community.contests SET status='PUBLISHED' WHERE id=?", contest);
        error(enter(creator), 403, "ACCOUNT_UNVERIFIED");
        jdbc.update("UPDATE auth.users SET email_verified_at=? WHERE id=?", Timestamp.from(NOW), creator);
        error(enter(creator), 403, "FORBIDDEN");
        error(enter(learner), 422, "CONTEST_NOT_OPEN");
        now = NOW;
        attempt(creator, contest, "EXPIRED", null, null, null, false);
        error(enter(learner), 422, "CONTEST_FULL");
        assertThat(attempts.myAttempt(learner, contest)).isEmpty();
    }

    @Test
    void AT10_emailVerificationUsesTheDatabaseEvenWhenJwtClaimsVerified() throws Exception {
        long unverified = user(false);
        error(enter(unverified), 403, "ACCOUNT_UNVERIFIED");
        assertThat(attempts.myAttempt(unverified, contest)).isEmpty();
        error(enter(creator), 403, "FORBIDDEN");
    }

    @Test
    void FR011_requestCannotChooseAnotherUserOrSubmitAScore() throws Exception {
        var session = response(enter(learner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + creator + ",\"score\":999999}"), 201);
        assertThat(jdbc.queryForObject("SELECT user_id FROM learning.attempts WHERE id=?", Long.class, session.path("sessionId").asLong()))
                .isEqualTo(learner);
        assertThat(jdbc.queryForObject("SELECT score FROM learning.attempts WHERE id=?", Long.class, session.path("sessionId").asLong())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "ABANDONED", "EXPIRED"})
    void FR011_terminalAttemptsUseTheExistingConflictEvenWhenContestIsFull(String state) throws Exception {
        attempt(learner, contest, state, null, null, null, false);
        jdbc.update("UPDATE community.contests SET max_participants=1 WHERE id=?", contest);
        error(enter(learner), 409, "CONTEST_ALREADY_ATTEMPTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHIVED", "EMPTY"})
    void FR011_invalidExamUsesTheExisting422AndDoesNotReserveCapacity(String state) throws Exception {
        if (state.equals("ARCHIVED")) jdbc.update("UPDATE learning.exams SET status='ARCHIVED' WHERE id=?", exam);
        else jdbc.update("UPDATE learning.questions SET status='ARCHIVED' WHERE id=?", questions.get(0));
        error(enter(learner), 422, "CONTEST_EXAM_INVALID");
        assertThat(attempts.countAttempts(contest)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"4,200", "5,200", "6,422"})
    void FR012_submitHonorsEngineGraceWithoutReturningScore(int lateSeconds, int expected) throws Exception {
        var session = response(enter(learner), 201);
        now = time(session, "deadline").plusSeconds(lateSeconds);
        var submit = post(BASE + "/{id}/attempts/submit", contest).header("Authorization", bearer(learner));
        if (expected == 200) {
            var result = response(submit, 200);
            assertThat(fields(result)).containsExactly("submittedAt");
            assertThat(time(result, "submittedAt")).isEqualTo(now);
            error(post(BASE + "/{id}/attempts/submit", contest).header("Authorization", bearer(learner)), 409, "CONTEST_ALREADY_ATTEMPTED");
        } else {
            error(submit, 422, "CONTEST_NOT_OPEN");
            assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, session.path("sessionId").asLong()))
                    .isEqualTo("EXPIRED");
        }
    }

    @Test
    void AT08_serverScoresTheOriginalAnswersButSubmitReturnsOnlyTimestamp() throws Exception {
        var session = response(enter(learner), 201);
        long id = session.path("sessionId").asLong();
        var accepted = response(post("/api/practice/sessions/{id}/answers", id).header("Authorization", bearer(learner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"no\":1,\"answer\":\"A\",\"durationMs\":3000}"), 200);
        noPrivateFields(accepted);
        now = NOW.plusSeconds(30);
        var submitted = response(post(BASE + "/{id}/attempts/submit", contest).header("Authorization", bearer(learner)), 200);
        assertThat(fields(submitted)).containsExactly("submittedAt");
        assertThat(jdbc.queryForMap("SELECT score,max_score FROM learning.attempts WHERE id=?", id))
                .containsEntry("score", 1L).containsEntry("max_score", 1L);
        noPrivateFields(submitted);
        for (String action : List.of("finish", "abandon")) {
            error(post("/api/practice/sessions/{id}/" + action, id).header("Authorization", bearer(learner)), 409, "CONTEST_SUBMIT_REQUIRED");
        }
    }

    @Test
    void FR012_submitWithoutOwnAttemptIsNotFoundAndCannotSubmitAnotherPersonsAttempt() throws Exception {
        response(enter(learner), 201);
        long other = user(true);
        error(post(BASE + "/{id}/attempts/submit", contest).header("Authorization", bearer(other)), 404, "CONTEST_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN"})
    void AT10_allFourOperationsRequireLearnerRole(String role) throws Exception {
        for (var request : List.of(post(BASE + "/{id}/attempts", contest), post(BASE + "/{id}/attempts/submit", contest),
                post(BASE + "/{id}/attempts/events", contest).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TAB_HIDDEN\"}"),
                get(BASE + "/{id}/results", contest))) {
            request.header("Authorization", bearer(learner, role));
            error(request, 403, "FORBIDDEN");
        }
    }

    @Test
    void AT10_guestsAreUnauthorizedAndCookieWritesRequireMatchingCsrf() throws Exception {
        for (var request : List.of(post(BASE + "/{id}/attempts", contest), post(BASE + "/{id}/attempts/submit", contest),
                post(BASE + "/{id}/attempts/events", contest).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TAB_HIDDEN\"}"),
                get(BASE + "/{id}/results", contest))) error(request, 401, "UNAUTHENTICATED");
        Cookie access = cookie(learner);
        Cookie csrf = new Cookie("XSRF-TOKEN", "contest-t05-csrf");
        error(post(BASE + "/{id}/attempts", contest).cookie(access), 403, "CSRF_TOKEN_INVALID");
        error(post(BASE + "/{id}/attempts", contest).cookie(access, csrf).header("X-XSRF-TOKEN", "wrong"), 403, "CSRF_TOKEN_INVALID");
        response(post(BASE + "/{id}/attempts", contest).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue()), 201);
        response(get(BASE + "/{id}/results", contest).cookie(access), 200);
        response(post(BASE + "/{id}/attempts/events", contest).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TAB_HIDDEN\"}"), 204);
        now = NOW.plusSeconds(30);
        response(post(BASE + "/{id}/attempts/submit", contest).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue()), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "9223372036854775807", "9223372036854775808", "invalid"})
    void FR011_missingAndInvalidContestIdsReturnTheSame404(String id) throws Exception {
        error(post(BASE + "/" + id + "/attempts").header("Authorization", bearer(learner)), 404, "CONTEST_NOT_FOUND");
    }
}
