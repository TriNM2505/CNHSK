package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.leaderboard.LeaderboardCache.Lookup;
import com.cnhsk.learning.leaderboard.LeaderboardCache.Payload;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.TopicOption;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.cnhsk.learning.leaderboard.LeaderboardRepository.Snapshot;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LeaderboardServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");
    private final LeaderboardRepository repository = mock(LeaderboardRepository.class);
    private final LeaderboardCatalogRepository catalog = mock(LeaderboardCatalogRepository.class);
    private final LeaderboardCache cache = mock(LeaderboardCache.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final LeaderboardPeriods periods = new LeaderboardPeriods(Clock.fixed(NOW, ZoneOffset.UTC));
    private final LeaderboardServiceImpl service = new LeaderboardServiceImpl(repository, periods, catalog, cache, auth);
    private final Row top = new Row(1, 7, 80, 100, NOW);

    @BeforeEach void defaults() {
        when(cache.read(anyString())).thenReturn(new Lookup(Optional.empty(), true));
        when(auth.bannedUserIds()).thenReturn(Set.of(99L));
        when(catalog.exists(9)).thenReturn(true);
        when(auth.publicProfiles(anySet())).thenReturn(Map.of(7L, new PublicProfile("Dũng", null)));
    }

    @Test void FR013_rankOfAlwaysUsesAllAndFreshBannedWithoutCacheOrProfiles() {
        when(repository.topicRanking(9, periods.resolve("all"), Set.of(99L), 7L, 1))
                .thenReturn(new Snapshot(List.of(top), Optional.of(top), 240));
        assertThat(service.rankOf(7, 9)).contains(new LeaderboardService.Rank(1, 240));
        verifyNoInteractions(cache, catalog);
        verify(auth, never()).publicProfiles(anySet());
    }

    @Test void FR013_missingOrBannedRankIsEmptyAndDatabaseErrorsPropagate() {
        when(repository.topicRanking(anyLong(), any(), anySet(), anyLong(), eq(1)))
                .thenReturn(new Snapshot(List.of(), Optional.empty(), 0));
        assertThat(service.rankOf(99, 9)).isEmpty();
        when(repository.topicRanking(anyLong(), any(), anySet(), anyLong(), eq(1))).thenThrow(new IllegalStateException("database"));
        assertThatIllegalStateException().isThrownBy(() -> service.rankOf(7, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> service.rankOf(0, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> service.rankOf(7, 0));
    }

    @Test void FR016_missUsesOneRankingStatementAndOneBatchProfileLookup() {
        Row mine = new Row(128, 8, 20, null, NOW);
        when(repository.topicRanking(9, periods.resolve("week"), Set.of(99L), 8L, 20))
                .thenReturn(new Snapshot(List.of(top), Optional.of(mine), 128));
        var result = service.topicLeaderboard(9, null, 8L);
        assertThat(result.type()).isEqualTo("TOPIC");
        assertThat(result.ref()).isEqualTo("9");
        assertThat(result.period()).isEqualTo("week");
        assertThat(result.myEntry().rank()).isEqualTo(128);
        assertThat(result.myEntry().displayName()).isEqualTo("Người học");
        verify(repository, times(1)).topicRanking(anyLong(), any(), anySet(), anyLong(), eq(20));
        verify(auth).publicProfiles(Set.of(7L, 8L));
        verify(cache).write(LeaderboardCache.key("topic", "9", periods.resolve("week")), List.of(top), 128, Set.of(99L));
    }

    @Test void FR016_guestHitSkipsRankingAndBannedLookup() {
        hit("topic", "9", "all");
        var result = service.topicLeaderboard(9, "all", null);
        assertThat(result.entries()).hasSize(1);
        assertThat(result.myEntry()).isNull();
        assertThat(result.periodStart()).isNull();
        assertThat(result.periodEnd()).isNull();
        verifyNoInteractions(repository);
        verify(auth, never()).bannedUserIds();
        verify(cache, never()).write(anyString(), anyList(), anyLong(), anySet());
    }

    @Test void AT14_authHitKeepsTopButReadsNewMyEntryAndNeverLeaksAnotherViewer() {
        hit("game", "CHAR_RAIN", "week");
        Row mine = new Row(1, 8, 100, 90, NOW);
        when(repository.gameRanking(GameCode.CHAR_RAIN, periods.resolve("week"), Set.of(99L), 8L, 20))
                .thenReturn(new Snapshot(List.of(mine), Optional.of(mine), 2));
        when(repository.gameRanking(GameCode.CHAR_RAIN, periods.resolve("week"), Set.of(99L), 9L, 20))
                .thenReturn(new Snapshot(List.of(mine), Optional.empty(), 2));
        var first = service.gameLeaderboard("CHAR_RAIN", "week", 8L);
        assertThat(first.entries().get(0).userId()).isEqualTo(7);
        assertThat(first.myEntry().score()).isEqualTo(100);
        assertThat(service.gameLeaderboard("CHAR_RAIN", "week", 9L).myEntry()).isNull();
        verify(auth, never()).bannedUserIds();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void FR003_missingNameUsesApprovedLabelAndNullAvatar(String displayName) {
        hit("topic", "9", "all");
        when(auth.publicProfiles(Set.of(7L))).thenReturn(Map.of(7L, new PublicProfile(displayName, "private-placeholder")));
        var entry = service.topicLeaderboard(9, "all", null).entries().get(0);
        assertThat(entry.displayName()).isEqualTo("Người học");
        assertThat(entry.avatarUrl()).isNull();
    }

    @Test void FR017_failedReadSkipsWriteButDatabaseAndAuthFailuresAreNotSwallowed() {
        when(cache.read(anyString())).thenReturn(new Lookup(Optional.empty(), false));
        when(repository.gameRanking(any(), any(), anySet(), isNull(), eq(20)))
                .thenReturn(new Snapshot(List.of(), Optional.empty(), 0));
        assertThat(service.gameLeaderboard("FLYING_DART", "all", null).entries()).isEmpty();
        verify(cache, never()).write(anyString(), anyList(), anyLong(), anySet());
        verify(auth, never()).publicProfiles(anySet());
        when(auth.bannedUserIds()).thenThrow(new IllegalStateException("database"));
        assertThatIllegalStateException().isThrownBy(() -> service.gameLeaderboard("FLYING_DART", "all", null));
    }

    @Test void FR014_catalogAndErrorsFollowContract() {
        when(catalog.topics(1)).thenReturn(List.of(new TopicOption(9, "Chủ đề", 1)));
        assertThat(service.listTopics(1)).containsExactly(new TopicOption(9, "Chủ đề", 1));
        assertThatThrownBy(() -> service.listTopics(0)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.topicLeaderboard(10, "week", null)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TOPIC_NOT_FOUND));
        assertThatThrownBy(() -> service.topicLeaderboard(0, "week", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.topicLeaderboard(9, "2026-W39", null)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.gameLeaderboard("char_rain", "all", null)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_GAME_CODE));
        assertThatThrownBy(() -> service.gameLeaderboard(null, "all", null)).isInstanceOf(ApiException.class);
        assertThat(service.listGames()).extracting(LeaderboardDtos.GameOption::code)
                .containsExactly(gameCodes());
    }

    private static String[] gameCodes() { return java.util.Arrays.stream(GameCode.values()).map(Enum::name).toArray(String[]::new); }

    private void hit(String type, String ref, String period) {
        String key = LeaderboardCache.key(type, ref, periods.resolve(period));
        when(cache.read(key)).thenReturn(new Lookup(Optional.of(new Payload(1, key, List.of(top), 1, Set.of(99L))), true));
    }
}
