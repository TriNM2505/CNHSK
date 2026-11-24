package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.contest.web.GameContestViews.Exclusion;
import com.cnhsk.community.contest.web.GameContestViews.GameContestWrite;
import com.cnhsk.community.contest.web.GameContestViews.Phase;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.GameRegistry;
import com.cnhsk.community.post.ModeratorPostService;
import com.cnhsk.community.post.web.ModeratorPostWrite;
import com.cnhsk.community.post.web.OwnPost;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GameContestServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");
    private static final long MODERATOR = 42;
    /** CHAR_RAIN rounds last 60 s; results wait that long plus the 20 s submit grace. */
    private static final int TIME_LIMIT = 60;

    private final ContestRepository contests = mock(ContestRepository.class);
    private final GameContestRepository ranking = mock(GameContestRepository.class);
    private final ModeratorPostService posts = mock(ModeratorPostService.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final AuditService audit = mock(AuditService.class);
    private final GameDefinition rain = mock(GameDefinition.class);
    private GameContestService service;

    @BeforeEach
    void setUp() {
        GameRegistry games = mock(GameRegistry.class);
        when(games.find(any())).thenReturn(Optional.empty());
        when(games.find(GameCode.CHAR_RAIN)).thenReturn(Optional.of(rain));
        when(rain.timeLimitSeconds()).thenReturn(TIME_LIMIT);
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR), true));
        when(auth.bannedUserIds()).thenReturn(Set.of(99L));
        service = new GameContestService(contests, ranking, posts, games, auth, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createStoresTheContestAndPinsItsGuideUntilADayAfterTheClose() {
        doAnswer(call -> {
            Contest saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return null;
        }).when(contests).create(any());
        OwnPost post = mock(OwnPost.class);
        when(post.id()).thenReturn(300L);
        when(posts.create(any())).thenReturn(post);
        Instant start = NOW.plusSeconds(3600);
        Instant end = NOW.plusSeconds(7200);

        var created = service.create(new GameContestWrite("  Đua chữ  ", " Thể lệ ", "CHAR_RAIN", start, end, 3, List.of()));

        assertThat(created.contestId()).isEqualTo(7);
        assertThat(created.postId()).isEqualTo(300);
        ArgumentCaptor<Contest> contest = ArgumentCaptor.forClass(Contest.class);
        verify(contests).create(contest.capture());
        assertThat(contest.getValue().getName()).isEqualTo("Đua chữ");
        assertThat(contest.getValue().getGameCode()).isEqualTo("CHAR_RAIN");
        assertThat(contest.getValue().getStartsAt()).isEqualTo(start);
        assertThat(contest.getValue().getEndsAt()).isEqualTo(end);
        assertThat(contest.getValue().getPrizeCount()).isEqualTo(3);
        ArgumentCaptor<ModeratorPostWrite> guide = ArgumentCaptor.forClass(ModeratorPostWrite.class);
        verify(posts).create(guide.capture());
        assertThat(guide.getValue().category()).isEqualTo(PostCategory.CONTEST);
        assertThat(guide.getValue().contestId()).isEqualTo(7L);
        assertThat(guide.getValue().pinnedUntil()).isEqualTo(end.plusSeconds(86_400));
        verify(audit).recordDetail(eq(MODERATOR), eq("GAME_CONTEST_CREATE"), eq("CONTEST"), eq(7L),
                eq(Map.of("gameCode", "CHAR_RAIN", "postId", 300L)));
    }

    @Test
    void createRejectsUnknownOrClosedGamesAndBadWindowsBeforeWriting() {
        Instant start = NOW.plusSeconds(60);
        assertValidation(() -> service.create(write("NOT_A_GAME", start, start.plusSeconds(60))), "gameCode");
        assertValidation(() -> service.create(write("FLYING_DART", start, start.plusSeconds(60))), "gameCode");
        assertValidation(() -> service.create(write("CHAR_RAIN", start, start)), "endsAt");
        assertValidation(() -> service.create(write("CHAR_RAIN", NOW.minusSeconds(120), NOW.minusSeconds(60))), "endsAt");
        assertValidation(() -> service.create(write("CHAR_RAIN", start, start.plusSeconds(32L * 86_400))), "endsAt");
        verify(contests, never()).create(any());
        verifyNoInteractions(posts);
    }

    @Test
    void onlyModeratorsCreateReadResultsOrDecideExclusions() {
        when(auth.currentUser()).thenReturn(new CurrentUser(5, Set.of(RoleCode.LEARNER, RoleCode.ADMIN), true));
        for (Runnable call : List.<Runnable>of(
                () -> service.create(write("CHAR_RAIN", NOW.plusSeconds(60), NOW.plusSeconds(120))),
                () -> service.results(7),
                () -> service.decide(7, 8, new Exclusion(true, "Gian lận")))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
                assertThat(error.getStatus().value()).isEqualTo(403);
            });
        }
        verifyNoInteractions(contests, ranking, posts, audit);
    }

    @Test
    void liveWithholdsTheRankingWhileWaitingAndWhileTallying() {
        stubContest(NOW.plusSeconds(60), NOW.plusSeconds(3600));
        var waiting = service.live(7);
        assertThat(waiting.phase()).isEqualTo(Phase.WAITING);
        assertThat(waiting.ranking()).isEmpty();
        assertThat(waiting.participants()).isZero();

        // Closed 30 s ago: a round started at the last moment may still be submitted for another 50 s.
        stubContest(NOW.minusSeconds(3600), NOW.minusSeconds(30));
        var tallying = service.live(7);
        assertThat(tallying.phase()).isEqualTo(Phase.TALLYING);
        assertThat(tallying.ranking()).isEmpty();
        assertThat(tallying.resultsAt()).isEqualTo(NOW.minusSeconds(30).plusSeconds(TIME_LIMIT + 20));
        assertThat(tallying.serverTime()).isEqualTo(NOW);
        verifyNoInteractions(ranking);
    }

    @Test
    void liveShowsTheTopFiftyWithNamesWhileOpenWithoutBannedPlayers() {
        stubContest(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(ranking.ranking(any(), eq(Set.of(99L)), eq(GameContestService.LIVE_SIZE))).thenReturn(new GameContestRepository.Ranking(
                List.of(row(1, 8, 1500), row(2, 9, 900)), 12));
        when(auth.publicProfiles(Set.of(8L, 9L))).thenReturn(Map.of(8L, new PublicProfile("An", null),
                9L, new PublicProfile(null, null)));

        var live = service.live(7);

        assertThat(live.phase()).isEqualTo(Phase.OPEN);
        assertThat(live.participants()).isEqualTo(12);
        assertThat(live.ranking()).extracting(row -> row.displayName()).containsExactly("An", null);
        assertThat(live.ranking()).extracting(row -> row.points()).containsExactly(1500L, 900L);
        verify(auth, never()).contactEmails(any());
        verifyNoInteractions(audit);
    }

    @Test
    void resultsAreOnlyAvailableOnceFinishedAndAuditTheEmailRead() {
        stubContest(NOW.minusSeconds(3600), NOW.minusSeconds(30));
        assertThatThrownBy(() -> service.results(7)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.CONTEST_LOCKED);
            assertThat(error.getStatus().value()).isEqualTo(409);
        });
        verifyNoInteractions(ranking, audit);

        stubContest(NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        when(ranking.ranking(any(), eq(Set.of(99L)), eq(3))).thenReturn(new GameContestRepository.Ranking(
                List.of(row(1, 8, 1500)), 4));
        when(ranking.exclusions(7)).thenReturn(List.of(new GameContestRepository.Exclusion(11, "Dùng công cụ", NOW)));
        when(auth.publicProfiles(Set.of(8L, 11L))).thenReturn(Map.of(8L, new PublicProfile("An", null),
                11L, new PublicProfile("Cường", null)));
        when(auth.contactEmails(Set.of(8L))).thenReturn(Map.of(8L, "an@example.test"));

        var results = service.results(7);

        assertThat(results.contest().phase()).isEqualTo(Phase.FINISHED);
        assertThat(results.contest().participants()).isEqualTo(4);
        assertThat(results.winners()).singleElement().satisfies(winner -> {
            assertThat(winner.displayName()).isEqualTo("An");
            assertThat(winner.email()).isEqualTo("an@example.test");
        });
        assertThat(results.excluded()).singleElement().satisfies(row -> {
            assertThat(row.displayName()).isEqualTo("Cường");
            assertThat(row.note()).isEqualTo("Dùng công cụ");
        });
        // Emails are only looked up for winners, never for excluded players.
        verify(auth).contactEmails(Set.of(8L));
        verify(audit).recordDetail(eq(MODERATOR), eq("GAME_CONTEST_WINNERS_VIEW"), eq("CONTEST"), eq(7L),
                eq(Map.of("winnerCount", 1)));
    }

    @Test
    void exclusionsAreStoredOrRemovedAndAudited() {
        stubContest(NOW.minusSeconds(7200), NOW.minusSeconds(3600));

        service.decide(7, 8, new Exclusion(true, "  Gian lận  "));
        verify(ranking).exclude(7, 8, "Gian lận", MODERATOR, NOW);
        verify(audit).recordDetail(eq(MODERATOR), eq("CONTEST_PARTICIPANT_EXCLUDE"), eq("CONTEST"), eq(7L),
                eq(Map.of("userId", 8L, "note", "Gian lận")));

        service.decide(7, 8, new Exclusion(false, "Khôi phục"));
        verify(ranking).include(7, 8);
        verify(audit).recordDetail(eq(MODERATOR), eq("CONTEST_PARTICIPANT_INCLUDE"), eq("CONTEST"), eq(7L), anyMap());
    }

    @Test
    void examContestsAndUnknownIdsAreNotGameContests() {
        when(contests.findPublished(7)).thenReturn(Optional.empty());
        assertNotFound(() -> service.live(7));
        Contest exam = Contest.game("Thi", null, null, NOW, NOW.plusSeconds(60), 3, MODERATOR, NOW);
        when(contests.findPublished(8)).thenReturn(Optional.of(exam));
        assertNotFound(() -> service.live(8));
        assertNotFound(() -> service.decide(8, 1, new Exclusion(true, "x")));
        verify(ranking, never()).exclude(anyLong(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void roundsAreAcceptedOnlyWhileOpenAndForTheContestGame() {
        stubContest(NOW.plusSeconds(60), NOW.plusSeconds(3600));
        assertThatThrownBy(() -> service.requireOpen(7, GameCode.CHAR_RAIN)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.CONTEST_NOT_OPEN));
        stubContest(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        service.requireOpen(7, GameCode.CHAR_RAIN);
        assertValidation(() -> service.requireOpen(7, GameCode.PINYIN_MATCH), "gameCode");
    }

    private void stubContest(Instant start, Instant end) {
        Contest contest = Contest.game("Đua chữ", "Thể lệ", "CHAR_RAIN", start, end, 3, MODERATOR, NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(contest, "id", 7L);
        when(contests.findPublished(7)).thenReturn(Optional.of(contest));
    }

    private static GameContestRepository.Row row(long rank, long userId, long points) {
        return new GameContestRepository.Row(rank, userId, points, 60_000, NOW.minusSeconds(rank));
    }

    private static GameContestWrite write(String game, Instant start, Instant end) {
        return new GameContestWrite("Đua chữ", "Thể lệ", game, start, end, 3, List.of());
    }

    private static void assertValidation(Runnable call, String field) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(error.getStatus().value()).isEqualTo(422);
            assertThat(error.getExtra().toString()).contains("field=" + field);
        });
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
            assertThat(error.getStatus().value()).isEqualTo(404);
        });
    }

}
