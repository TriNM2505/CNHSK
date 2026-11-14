package com.cnhsk.community.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.community.game.web.GameViews.CommonGame;
import com.cnhsk.community.game.web.GameViews.MyStats;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.learning.api.GameStatsApi.GameStats;
import com.cnhsk.learning.api.PracticeApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GameCatalogServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T01:00:00Z");
    private final GameCatalogCache cache = mock(GameCatalogCache.class);
    private final GameStatsApi stats = mock(GameStatsApi.class);
    private final GameCatalogService service = new GameCatalogService(cache, stats);

    @Test void AT02_guestGetsCommonMetadataAndNullStatsWithoutReadingPersonalData() {
        when(cache.snapshot()).thenReturn(List.of(common(GameCode.CHAR_RAIN, 10, true)));
        var game = service.list(null).get(0);
        assertThat(game.code()).isEqualTo(GameCode.CHAR_RAIN);
        assertThat(game.name()).isEqualTo(GameCode.CHAR_RAIN.displayName());
        assertThat(game.description()).isEqualTo("Mô tả tự soạn");
        assertThat(game.skill()).isEqualTo(PracticeApi.Skill.VOCAB);
        assertThat(game.howToPlay()).isEqualTo("Chọn một mục");
        assertThat(game.timeLimitSeconds()).isEqualTo(30);
        assertThat(game.playable()).isTrue(); assertThat(game.unavailableReason()).isNull();
        assertThat(game.myStats()).isNull(); verifyNoInteractions(stats);
    }

    @Test void AT02_unavailableReasonComesFromTheCommonSnapshot() {
        when(cache.snapshot()).thenReturn(List.of(common(GameCode.PINYIN_MATCH, 10, false)));
        var game = service.list(null).get(0);
        assertThat(game.playable()).isFalse();
        assertThat(game.unavailableReason()).isEqualTo("NOT_ENOUGH_CONTENT");
    }

    @Test void AT03_oneStatsCallServesAllSixGamesUsingEachGamesServerPointValue() {
        var common = java.util.Arrays.stream(GameCode.values()).map(code -> common(code, code.ordinal() + 2, true)).toList();
        when(cache.snapshot()).thenReturn(common);
        Map<GameCode, GameStats> recorded = new java.util.EnumMap<>(GameCode.class);
        for (GameCode code : GameCode.values()) recorded.put(code, new GameStats(8, 3, NOW));
        when(stats.gameStats(71)).thenReturn(recorded);
        var games = service.list(71L);
        assertThat(games).extracting(game -> game.code()).containsExactly(GameCode.values());
        for (var game : games) {
            assertThat(game.myStats()).isEqualTo(new MyStats(8L * (game.code().ordinal() + 2), 3, NOW));
        }
        verify(stats).gameStats(71); verifyNoMoreInteractions(stats);
    }

    @Test void Q_T04_03_signedInWithoutSubmittedRoundsGetsZerosAndNullLastPlayedAt() {
        when(cache.snapshot()).thenReturn(List.of(common(GameCode.CHAR_RAIN, 10, true)));
        when(stats.gameStats(71)).thenReturn(Map.of());
        assertThat(service.list(71L).get(0).myStats()).isEqualTo(new MyStats(0, 0, null));
    }

    @Test void FR005_sameSnapshotDoesNotMixUsersOrCacheTheirNewStatistics() {
        when(cache.snapshot()).thenReturn(List.of(common(GameCode.CHAR_RAIN, 10, true)));
        when(stats.gameStats(71)).thenReturn(Map.of(GameCode.CHAR_RAIN, new GameStats(5, 1, NOW)),
                Map.of(GameCode.CHAR_RAIN, new GameStats(8, 2, NOW.plusSeconds(1))));
        when(stats.gameStats(72)).thenReturn(Map.of(GameCode.CHAR_RAIN, new GameStats(1, 1, NOW)));
        var first = service.list(71L);
        assertThat(first.get(0).myStats().bestPoints()).isEqualTo(50);
        assertThat(service.list(72L).get(0).myStats().bestPoints()).isEqualTo(10);
        assertThat(service.list(71L).get(0).myStats()).isEqualTo(new MyStats(80, 2, NOW.plusSeconds(1)));
        assertThat(service.list(null).get(0).myStats()).isNull();
        assertThat(first.get(0).myStats().bestPoints()).isEqualTo(50);
        verify(stats, times(2)).gameStats(71); verify(stats).gameStats(72); verifyNoMoreInteractions(stats);
    }

    @Test void FR004_zeroRecordRemainsZeroWhenSuspiciousRoundsHaveBeenCounted() {
        when(cache.snapshot()).thenReturn(List.of(common(GameCode.CHAR_RAIN, 10, true)));
        when(stats.gameStats(71)).thenReturn(Map.of(GameCode.CHAR_RAIN, new GameStats(0, 3, NOW)));
        assertThat(service.list(71L).get(0).myStats()).isEqualTo(new MyStats(0, 3, NOW));
    }

    @Test void Q_T03_01_emptyRegistryReturnsEmptyWithoutReadingStats() {
        when(cache.snapshot()).thenReturn(List.of());
        assertThat(service.list(null)).isEmpty(); assertThat(service.list(71L)).isEmpty();
        verifyNoInteractions(stats);
    }

    private CommonGame common(GameCode code, int points, boolean playable) {
        return new CommonGame(code, code.displayName(), "Mô tả tự soạn", PracticeApi.Skill.VOCAB, "Chọn một mục", 30,
                points, playable, playable ? null : "NOT_ENOUGH_CONTENT");
    }
}
