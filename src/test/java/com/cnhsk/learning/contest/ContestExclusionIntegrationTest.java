package com.cnhsk.learning.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.cnhsk.learning.api.ContestAttemptApi;
import com.cnhsk.learning.api.ContestAttemptApi.ContestParticipant;
import com.cnhsk.learning.api.ContestAttemptApi.RankingRow;
import com.cnhsk.learning.contest.ContestAttemptIntegrationTest.Fixture;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ContestExclusionIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-15T04:00:00Z");
    @Autowired private ContestAttemptApi contests;
    @Autowired private JdbcTemplate jdbc;
    private Fixture fixture;
    private long contest;

    @BeforeEach
    void setup() {
        fixture = new Fixture(jdbc);
        contest = fixture.exam("DRAFT");
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void AT12_keepAndExcludeRecomputeRankingTotalAndRankOfWithoutChangingOtherEvidence() {
        long flagged = fixture.user(), regular = fixture.user();
        long attempt = row(flagged, contest, "CONTEST", "SUBMITTED", 9L, 10L, true);
        row(regular, contest, "CONTEST", "SUBMITTED", 5L, 10L, false);
        String before = snapshotExceptSuspicious(attempt);
        assertThat(contests.ranking(contest, 0, 20).items()).extracting(RankingRow::userId).containsExactly(regular);
        assertThat(contests.rankOf(flagged, contest)).isEmpty();

        contests.setExcluded(contest, flagged, false);
        contests.setExcluded(contest, flagged, false);
        var kept = contests.ranking(contest, 0, 20);
        assertThat(kept.items()).extracting(RankingRow::userId).containsExactly(flagged, regular);
        assertThat(kept.items()).extracting(RankingRow::rank).containsExactly(1L, 2L);
        assertThat(kept.total()).isEqualTo(2);
        assertThat(contests.rankOf(flagged, contest)).contains(1L);
        assertThat(contests.rankOf(regular, contest)).contains(2L);
        assertThat(contests.myAttempt(flagged, contest).orElseThrow().suspicious()).isFalse();

        contests.setExcluded(contest, flagged, true);
        contests.setExcluded(contest, flagged, true);
        var excluded = contests.ranking(contest, 0, 20);
        assertThat(excluded.items()).extracting(RankingRow::userId).containsExactly(regular);
        assertThat(excluded.items().get(0).rank()).isEqualTo(1);
        assertThat(excluded.total()).isEqualTo(1);
        assertThat(contests.rankOf(flagged, contest)).isEmpty();
        assertThat(contests.rankOf(regular, contest)).contains(1L);
        assertThat(contests.participants(contest)).extracting(ContestParticipant::userId).containsExactly(flagged, regular);
        assertThat(contests.participants(contest).get(0).suspicious()).isTrue();
        assertThat(snapshotExceptSuspicious(attempt)).isEqualTo(before);
        assertThat(contests.countAttempts(contest)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, attempt)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id IN (?,?)", Long.class, flagged, regular)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id IN (?,?)", Long.class, flagged, regular)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,true", "IN_PROGRESS,false", "EXPIRED,true", "EXPIRED,false", "ABANDONED,true", "ABANDONED,false"})
    void FR007_Q1_onlySubmittedAttemptsCanBeReviewed(String state, boolean excluded) {
        long user = fixture.user();
        long attempt = row(user, contest, "CONTEST", state, 2L, 10L, !excluded);
        String before = snapshot(attempt);
        assertMissing(() -> contests.setExcluded(contest, user, excluded));
        assertThat(snapshot(attempt)).isEqualTo(before);
    }

    @Test
    void FR007_Q1_missingUserContestOrContestAttemptReturns404AndKeepsOtherRows() {
        long user = fixture.user(), other = fixture.user();
        long attempt = row(user, contest, "CONTEST", "SUBMITTED", 2L, 10L, false);
        long otherKind = row(other, contest, "EXAM", "SUBMITTED", 8L, 10L, false);
        String before = snapshot(attempt), otherBefore = snapshot(otherKind);

        assertMissing(() -> contests.setExcluded(contest, Long.MAX_VALUE, true));
        assertMissing(() -> contests.setExcluded(Long.MAX_VALUE, user, true));
        assertMissing(() -> contests.setExcluded(contest, other, true));
        assertThat(snapshot(attempt)).isEqualTo(before);
        assertThat(snapshot(otherKind)).isEqualTo(otherBefore);
    }

    @Test
    void FR007_decisionChangesOnlyTheSelectedContestUserAndKind() {
        long user = fixture.user(), other = fixture.user(), otherContest = fixture.exam("DRAFT");
        long selected = row(user, contest, "CONTEST", "SUBMITTED", 2L, 10L, false);
        long anotherUser = row(other, contest, "CONTEST", "SUBMITTED", 3L, 10L, false);
        long anotherContest = row(user, otherContest, "CONTEST", "SUBMITTED", 4L, 10L, false);
        long anotherKind = row(user, contest, "EXAM", "SUBMITTED", 5L, 10L, false);
        List<Long> untouched = List.of(anotherUser, anotherContest, anotherKind);
        List<String> before = untouched.stream().map(this::snapshot).toList();

        contests.setExcluded(contest, user, true);
        assertThat(jdbc.queryForObject("SELECT suspicious FROM learning.attempts WHERE id=?", Boolean.class, selected)).isTrue();
        assertThat(untouched.stream().map(this::snapshot).toList()).containsExactlyElementsOf(before);
    }

    @Test
    void FR007_Q3_keepingASubmittedAttemptWithMissingPointsStillDoesNotRankIt() {
        long noScore = fixture.user(), noMax = fixture.user();
        row(noScore, contest, "CONTEST", "SUBMITTED", null, 10L, true);
        row(noMax, contest, "CONTEST", "SUBMITTED", 9L, null, true);
        contests.setExcluded(contest, noScore, false);
        contests.setExcluded(contest, noMax, false);

        var page = contests.ranking(contest, 0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(contests.rankOf(noScore, contest)).isEmpty();
        assertThat(contests.rankOf(noMax, contest)).isEmpty();
        assertThat(contests.myAttempt(noScore, contest).orElseThrow().score()).isNull();
        assertThat(contests.myAttempt(noMax, contest).orElseThrow().maxScore()).isNull();
        assertThat(contests.participants(contest)).hasSize(2)
                .allSatisfy(participant -> assertThat(participant.suspicious()).isFalse());
    }

    private long row(long user, long contestId, String kind, String state, Long score, Long maxScore, boolean suspicious) {
        return jdbc.queryForObject("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,score,max_score,duration_ms,submitted_at,suspicious)
                VALUES (?,?,?,?,?,?,?,3000,?,?) RETURNING id
                """, Long.class, user, kind, kind.equals("CONTEST") ? "CONTEST" : "MOCK_EXAM", contestId,
                state, score, maxScore, state.equals("SUBMITTED") ? Timestamp.from(NOW) : null, suspicious);
    }

    private String snapshot(long attempt) {
        return jdbc.queryForObject("SELECT row_to_json(a)::text FROM learning.attempts a WHERE id=?", String.class, attempt);
    }

    private String snapshotExceptSuspicious(long attempt) {
        return jdbc.queryForObject("SELECT (to_jsonb(a) - 'suspicious')::text FROM learning.attempts a WHERE id=?", String.class, attempt);
    }

    private static void assertMissing(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }
}
