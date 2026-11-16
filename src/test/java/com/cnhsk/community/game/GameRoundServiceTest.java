package com.cnhsk.community.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.api.*;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameRoundServiceTest {
    private static final Instant NOW = Instant.parse("2035-01-01T00:00:00.200Z");
    private final GameRoundApi rounds = mock(GameRoundApi.class);
    private final GameStatsApi stats = mock(GameStatsApi.class);
    private final ContentLookup content = mock(ContentLookup.class);
    private final PracticeApi practice = mock(PracticeApi.class);
    private final GameRegistry registry = mock(GameRegistry.class);
    private final GameDefinition definition = mock(GameDefinition.class);
    private final GameRoundService service = new GameRoundService(registry, rounds, stats, content, practice,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach void fixture() {
        when(stats.countRecentGameRounds(7, NOW.minusSeconds(3600))).thenReturn(new GameStatsApi.RecentGameRounds(0, null));
        when(registry.find(GameCode.CHAR_RAIN)).thenReturn(java.util.Optional.of(definition));
        when(definition.isAvailable(any())).thenReturn(true);
    }

    @Test void AT05_limitIsCheckedUnderLockBeforeAnyGameOrContentAccessAndRoundsUpRetry() {
        when(stats.countRecentGameRounds(7, NOW.minusSeconds(3600)))
                .thenReturn(new GameStatsApi.RecentGameRounds(30, NOW.minusSeconds(3598).plusMillis(1)));
        assertThatThrownBy(() -> service.start(7, GameCode.CHAR_RAIN, null)).isInstanceOfSatisfying(ApiException.class,
                error -> { assertThat(error.getCode()).isEqualTo(ErrorCode.ROUND_LIMIT_EXCEEDED);
                    assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 3L); });
        var order = inOrder(rounds, stats);
        order.verify(rounds).lockStart(7);
        order.verify(stats).countRecentGameRounds(7, NOW.minusSeconds(3600));
        verifyNoInteractions(registry, content, practice);
        verifyNoMoreInteractions(rounds, stats);
    }

    @Test void AT05_retryNeverFallsBelowOneSecond() {
        when(stats.countRecentGameRounds(7, NOW.minusSeconds(3600)))
                .thenReturn(new GameStatsApi.RecentGameRounds(30, NOW.minusSeconds(3600)));
        assertThatThrownBy(() -> service.start(7, GameCode.CHAR_RAIN, null)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getExtra()).containsEntry("retryAfterSeconds", 1L));
    }

    @Test void AT07_registryWithoutDefinitionsIsUnavailable() {
        when(registry.find(GameCode.CHAR_RAIN)).thenReturn(java.util.Optional.empty());
        unavailable();
    }

    @Test void AT07_unavailableDefinitionDoesNotBuildOrAbandon() {
        when(definition.isAvailable(any())).thenReturn(false);
        unavailable(); verify(definition, never()).buildRound(any());
    }

    @Test void AT07_nullRoundIsUnavailableWithoutAbandoning() {
        when(definition.buildRound(any())).thenReturn(null); unavailable();
    }

    @Test void AT07_emptyRoundIsUnavailableWithoutAbandoning() {
        when(definition.buildRound(any())).thenReturn(List.of()); unavailable();
    }

    private void unavailable() {
        assertThatThrownBy(() -> service.start(7, GameCode.CHAR_RAIN, null))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.GAME_UNAVAILABLE);
                    assertThat(error.getStatus().value()).isEqualTo(422);
                });
        verify(rounds, never()).abandonActive(anyLong(), any(), any()); verifyNoInteractions(practice);
    }

    @Test void AT08_serverSnapshotTtlPublicProjectionAndUserContextArePassedWithoutClientScores() {
        var item = new PracticeApi.ServedItem(1, PracticeApi.ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, 10L,
                new PracticeApi.Prompt("你好", null, null, null, null, null, null, null, null),
                List.of(new PracticeApi.Option("A", "xin chào")), "A", null, "Tự soạn", new PracticeApi.Rules(300, 0, false));
        when(definition.buildRound(any())).thenReturn(List.of(item));
        when(definition.timeLimitSeconds()).thenReturn(45);
        when(definition.pointsPerCorrect()).thenReturn(2);
        when(definition.uiConfig()).thenReturn(Map.of("rows", 3));
        when(definition.uiConfig(List.of(item))).thenCallRealMethod();
        var publicItem = new PracticeApi.ServedItemView(1, item.type(), item.skill(), item.lexemeId(), item.prompt(), item.options(), item.rules());
        when(practice.start(any())).thenReturn(new PracticeApi.SessionView(99, PracticeApi.PracticeSource.GAME_ROUND,
                null, PracticeApi.FeedbackMode.IMMEDIATE, "IN_PROGRESS", NOW, NOW.plusSeconds(63), List.of(publicItem), List.of(), Map.of()));
        var result = service.start(7, GameCode.CHAR_RAIN, 9);
        assertThat(result.roundId()).isEqualTo(99); assertThat(result.gameCode()).isEqualTo(GameCode.CHAR_RAIN);
        assertThat(result.serverTime()).isEqualTo(NOW); assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.deadline()).isEqualTo(NOW.plusSeconds(48)); assertThat(result.items()).containsExactly(publicItem);
        assertThat(result.timeLimitSeconds()).isEqualTo(45); assertThat(result.pointsPerCorrect()).isEqualTo(2);
        assertThat(result.uiConfig()).containsEntry("rows", 3);
        var command = ArgumentCaptor.forClass(PracticeApi.StartPracticeCommand.class);
        var order = inOrder(rounds, stats, definition, practice);
        order.verify(rounds).lockStart(7); order.verify(stats).countRecentGameRounds(7, NOW.minusSeconds(3600));
        order.verify(definition).isAvailable(new GameContext(7L, 9, content));
        order.verify(definition).buildRound(new GameContext(7L, 9, content));
        order.verify(rounds).abandonActive(7, GameCode.CHAR_RAIN, NOW); order.verify(practice).start(command.capture());
        var value = command.getValue();
        assertThat(value.userId()).isEqualTo(7); assertThat(value.source()).isEqualTo(PracticeApi.PracticeSource.GAME_ROUND);
        assertThat(value.gameCode()).isEqualTo("CHAR_RAIN"); assertThat(value.refId()).isNull();
        assertThat(value.items()).containsExactly(item); assertThat(value.preRecorded()).isEmpty();
        assertThat(value.ttlOverride()).isEqualTo(Duration.ofSeconds(63));
        assertThat(value.sessionMeta()).containsExactlyInAnyOrderEntriesOf(Map.of("timeLimitSeconds", 45, "pointsPerCorrect", 2, "uiConfig", Map.of("rows", 3)));
        verify(stats, times(1)).countRecentGameRounds(anyLong(), any());
    }
}
