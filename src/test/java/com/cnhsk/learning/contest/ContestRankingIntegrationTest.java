package com.cnhsk.learning.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ContestRankingIntegrationTest extends AbstractIntegrationTest {
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
    void AT11_fiveAttemptsWithOneSuspiciousProduceFourConsecutiveRanks() {
        long higher = fixture.user(), faster = fixture.user(), earlier = fixture.user();
        long later = fixture.user(), suspicious = fixture.user();
        submitted(later, 4L, 2000, NOW.plusSeconds(10), false);
        submitted(earlier, 4L, 2000, NOW, false);
        submitted(faster, 4L, 1000, NOW.plusSeconds(30), false);
        submitted(higher, 5L, 9000, NOW.plusSeconds(40), false);
        submitted(suspicious, 99L, 1, NOW, true);

        var page = contests.ranking(contest, 0, 20);
        assertThat(page.items()).extracting(RankingRow::userId).containsExactly(higher, faster, earlier, later);
        assertThat(page.items()).extracting(RankingRow::rank).containsExactly(1L, 2L, 3L, 4L);
        assertThat(page.total()).isEqualTo(4);
        assertThat(contests.rankOf(later, contest)).contains(4L);
        assertThat(contests.rankOf(suspicious, contest)).isEmpty();
        assertThat(contests.myAttempt(suspicious, contest).orElseThrow().suspicious()).isTrue();
    }

    @Test
    void AT11_tiesUseSubmissionThenUserIdAndNullDurationFollowsKnownDurations() {
        long lowerUser = fixture.user(), higherUser = fixture.user(), earlier = fixture.user();
        long noDuration = fixture.user(), zero = fixture.user();
        // Reverse insertion order makes an accidental attempt-id tie-break visible.
        submitted(higherUser, 7L, 3000, NOW, false);
        submitted(lowerUser, 7L, 3000, NOW, false);
        submitted(earlier, 7L, 3000, NOW.minusSeconds(1), false);
        submitted(noDuration, 7L, null, NOW.minusSeconds(100), false);
        submitted(zero, 0L, 1, NOW.minusSeconds(200), false);

        var page = contests.ranking(contest, 0, 20);
        assertThat(page.items()).extracting(RankingRow::userId)
                .containsExactly(earlier, lowerUser, higherUser, noDuration, zero);
        assertThat(page.items()).extracting(RankingRow::rank).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(page.items().get(3).durationMs()).isNull();
        assertThat(page.items().get(4).score()).isZero();
        for (RankingRow row : page.items()) assertThat(contests.rankOf(row.userId(), contest)).contains(row.rank());
    }

    @Test
    void FR006_paginationPreservesGlobalRanksAndTotalOnEmptyOrVeryDistantPages() {
        List<Long> users = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            long user = fixture.user();
            users.add(user);
            submitted(user, 9L - index, 3000, NOW, false);
        }
        for (int pageNo = 0; pageNo < 3; pageNo++) {
            var page = contests.ranking(contest, pageNo, 3);
            assertThat(page.page()).isEqualTo(pageNo);
            assertThat(page.size()).isEqualTo(3);
            assertThat(page.total()).isEqualTo(9);
            assertThat(page.items()).extracting(RankingRow::userId)
                    .containsExactlyElementsOf(users.subList(pageNo * 3, pageNo * 3 + 3));
            assertThat(page.items()).extracting(RankingRow::rank)
                    .containsExactly(1L + pageNo * 3, 2L + pageNo * 3, 3L + pageNo * 3);
        }
        for (int pageNo : List.of(3, Integer.MAX_VALUE)) {
            var page = contests.ranking(contest, pageNo, 50);
            assertThat(page.items()).isEmpty();
            assertThat(page.total()).isEqualTo(9);
        }
        assertThat(contests.rankOf(users.get(8), contest)).contains(9L);
    }

    @Test
    void FR006_Q3_rankingTotalAndRankOfShareEveryEligibilityConditionAndKeepZeroScores() {
        long regular = fixture.user(), zero = fixture.user();
        submitted(regular, 1L, 3000, NOW, false);
        row(zero, contest, "CONTEST", "SUBMITTED", 0L, 0L, null, NOW, false);
        List<Long> excluded = new ArrayList<>();
        long suspicious = fixture.user();
        excluded.add(suspicious);
        submitted(suspicious, 99L, 1, NOW, true);
        long noScore = fixture.user(), noMax = fixture.user(), neither = fixture.user();
        excluded.addAll(List.of(noScore, noMax, neither));
        row(noScore, contest, "CONTEST", "SUBMITTED", null, 10L, 1, NOW, false);
        row(noMax, contest, "CONTEST", "SUBMITTED", 99L, null, 1, NOW, false);
        row(neither, contest, "CONTEST", "SUBMITTED", null, null, 1, NOW, false);
        for (String state : List.of("IN_PROGRESS", "EXPIRED", "ABANDONED")) {
            long user = fixture.user();
            excluded.add(user);
            row(user, contest, "CONTEST", state, 99L, 100L, 1, NOW, false);
        }
        long otherKind = fixture.user(), otherContest = fixture.user();
        excluded.addAll(List.of(otherKind, otherContest));
        row(otherKind, contest, "EXAM", "SUBMITTED", 99L, 100L, 1, NOW, false);
        row(otherContest, fixture.exam("DRAFT"), "CONTEST", "SUBMITTED", 99L, 100L, 1, NOW, false);

        var first = contests.ranking(contest, 0, 1);
        var second = contests.ranking(contest, 1, 1);
        var beyond = contests.ranking(contest, 2, 1);
        assertThat(first.items()).extracting(RankingRow::userId).containsExactly(regular);
        assertThat(second.items()).extracting(RankingRow::userId).containsExactly(zero);
        assertThat(second.items().get(0).rank()).isEqualTo(2);
        assertThat(second.items().get(0).maxScore()).isZero();
        assertThat(beyond.items()).isEmpty();
        assertThat(List.of(first.total(), second.total(), beyond.total())).containsOnly(2L);
        assertThat(contests.rankOf(regular, contest)).contains(1L);
        assertThat(contests.rankOf(zero, contest)).contains(2L);
        for (long user : excluded) assertThat(contests.rankOf(user, contest)).as("user %s", user).isEmpty();

        var participants = contests.participants(contest);
        assertThat(participants).hasSize(9);
        assertThat(participants).extracting(ContestParticipant::userId).contains(noScore, noMax, neither, suspicious)
                .doesNotContain(otherKind, otherContest);
        assertThat(contests.myAttempt(noScore, contest).orElseThrow().score()).isNull();
        assertThat(contests.myAttempt(noMax, contest).orElseThrow().maxScore()).isNull();
        assertThat(contests.myAttempt(neither, contest).orElseThrow().score()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "SUBMITTED", "EXPIRED", "ABANDONED"})
    void FR006_myAttemptPreservesNullableFieldsAndIsScopedToTheUserAndContest(String state) {
        long user = fixture.user(), other = fixture.user();
        boolean submitted = state.equals("SUBMITTED");
        row(user, contest, "CONTEST", state, submitted ? 2L : null, submitted ? 10L : null,
                null, submitted ? NOW : null, true);
        var mine = contests.myAttempt(user, contest).orElseThrow();
        assertThat(mine.status()).isEqualTo(state);
        assertThat(mine.score()).isEqualTo(submitted ? 2L : null);
        assertThat(mine.maxScore()).isEqualTo(submitted ? 10L : null);
        assertThat(mine.durationMs()).isNull();
        assertThat(mine.submittedAt()).isEqualTo(submitted ? NOW : null);
        assertThat(mine.suspicious()).isTrue();
        assertThat(contests.myAttempt(other, contest)).isEmpty();
        assertThat(contests.myAttempt(user, Long.MAX_VALUE)).isEmpty();
        row(other, contest, "EXAM", "SUBMITTED", 10L, 10L, 1, NOW, false);
        assertThat(contests.myAttempt(other, contest)).isEmpty();
    }

    @Test
    void FR006_Q2_participantsIncludeEveryStateAndFlagWithNullsLastAtEachKey() {
        long expiredScore = fixture.user(), submitted = fixture.user();
        long lowerUser = fixture.user(), higherUser = fixture.user(), noSubmittedAt = fixture.user(), noDuration = fixture.user();
        row(expiredScore, contest, "CONTEST", "EXPIRED", 9L, null, null, null, true);
        row(submitted, contest, "CONTEST", "SUBMITTED", 2L, 10L, 3000, NOW, false);
        row(higherUser, contest, "CONTEST", "EXPIRED", null, null, 10, NOW, false);
        row(lowerUser, contest, "CONTEST", "ABANDONED", null, null, 10, NOW, true);
        row(noSubmittedAt, contest, "CONTEST", "IN_PROGRESS", null, null, 10, null, false);
        row(noDuration, contest, "CONTEST", "IN_PROGRESS", null, null, null, null, false);

        var participants = contests.participants(contest);
        assertThat(participants).extracting(ContestParticipant::userId)
                .containsExactly(expiredScore, submitted, lowerUser, higherUser, noSubmittedAt, noDuration);
        assertThat(participants).extracting(ContestParticipant::status)
                .containsExactly("EXPIRED", "SUBMITTED", "ABANDONED", "EXPIRED", "IN_PROGRESS", "IN_PROGRESS");
        assertThat(participants).extracting(ContestParticipant::suspicious).containsExactly(true, false, true, false, false, false);
        assertThat(participants.get(0).maxScore()).isNull();
        assertThat(participants.get(5).submittedAt()).isNull();
    }

    @Test
    void FR006_unknownContestOrUserReturnsEmptyViewsAndAZeroTotal() {
        long user = fixture.user();
        var page = contests.ranking(Long.MAX_VALUE, 0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(contests.rankOf(user, Long.MAX_VALUE)).isEmpty();
        assertThat(contests.myAttempt(user, Long.MAX_VALUE)).isEmpty();
        assertThat(contests.participants(Long.MAX_VALUE)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,51"})
    void FR006_invalidPaginationUsesTheExistingValidationError(int page, int size) {
        assertThatThrownBy(() -> contests.ranking(contest, page, size)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        });
    }

    private void submitted(long user, Long score, Integer duration, Instant submittedAt, boolean suspicious) {
        row(user, contest, "CONTEST", "SUBMITTED", score, 10L, duration, submittedAt, suspicious);
    }

    private void row(long user, long contestId, String kind, String state, Long score, Long maxScore,
            Integer duration, Instant submittedAt, boolean suspicious) {
        jdbc.update("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,score,max_score,duration_ms,submitted_at,suspicious)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, user, kind, kind.equals("CONTEST") ? "CONTEST" : "MOCK_EXAM", contestId, state,
                score, maxScore, duration, submittedAt == null ? null : Timestamp.from(submittedAt), suspicious);
    }
}
