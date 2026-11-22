package com.cnhsk.learning.practice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameRoundApi;
import com.cnhsk.learning.api.GameRoundHooks;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.learning.practice.entity.Attempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameRoundPracticeHandlerTest {
    private static final Instant NOW = Instant.parse("2035-01-01T00:00:00Z");
    private final GameRoundApi rounds = mock(GameRoundApi.class);
    private final GameRoundHooks hooks = mock(GameRoundHooks.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final GameRoundPracticeHandler handler = new GameRoundPracticeHandler(rounds, hooks, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void AT10_lockedContextCrossesTheBoundaryAsApiRecordsAndUsesServerClock() {
        var item = PracticeServiceTest.choice(1);
        var round = round(item);
        when(rounds.find(19)).thenReturn(Optional.of(round));
        var totals = new GameRoundHooks.AnswerTotals(2, 3);
        when(rounds.totals(19)).thenReturn(totals);
        var attempt = new Attempt(7L, "GAME", "GAME_ROUND", null, NOW, NOW.plusSeconds(48), mapper.createObjectNode());
        ReflectionTestUtils.setField(attempt, "id", 19L);
        var answer = new PracticeSourceHandler.Answer(1, "A", 1000, 0, false, false, Map.of());
        handler.validateAnswerContext(attempt, item, answer);
        verify(hooks).validateAnswer(eq(round), eq(totals), eq(mapper.convertValue(item, PracticeApi.ServedItem.class)),
                eq(new PracticeApi.Answer(1, "A", 1000, 0, false, false, Map.of())), eq(NOW));
        assertThat(handler.source()).isEqualTo(PracticeSource.GAME_ROUND);
    }

    @Test void AT15_finishCountsOnlyServerEvidenceAndExcludesThisRoundFromPreviousBest() {
        var item = PracticeServiceTest.choice(1);
        when(rounds.find(19)).thenReturn(Optional.of(round(item)));
        when(rounds.bestCorrectBefore(7, GameCode.CHAR_RAIN, 19)).thenReturn(8L);
        when(hooks.onFinish(any(), any())).thenReturn(Map.of("points", 10L));
        var answer = new PracticeSourceHandler.Answer(1, "A", 1000, 0, false, false, Map.of());
        var context = new PracticeSourceHandler.FinishContext(7, 19, PracticeSource.GAME_ROUND, null, 1L, 1L,
                false, NOW, NOW.plusSeconds(2), List.of(new PracticeSourceHandler.GradedItem(item, answer, true, true, Map.of())));
        assertThat(handler.onFinish(context)).containsEntry("points", 10L);
        var facts = ArgumentCaptor.forClass(GameRoundHooks.Finish.class);
        verify(hooks).onFinish(any(), facts.capture());
        assertThat(facts.getValue()).isEqualTo(new GameRoundHooks.Finish(1L, 1L, false, NOW.plusSeconds(2), 1, 0, 8));
    }

    @Test void missingServerSnapshotFailsWithoutCallingTheCommunityPolicy() {
        when(rounds.find(19)).thenReturn(Optional.empty());
        var context = new PracticeSourceHandler.FinishContext(7, 19, PracticeSource.GAME_ROUND, null,
                0L, 0L, false, NOW, NOW, List.of());
        assertThatThrownBy(() -> handler.onFinish(context)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(hooks);
    }

    private GameRoundHooks.Round round(ServedItem item) {
        return new GameRoundHooks.Round(19, 7, GameCode.CHAR_RAIN, "IN_PROGRESS", NOW, NOW.plusSeconds(48),
                30, 10, 1, Map.of(), List.of(mapper.convertValue(item, PracticeApi.ServedItem.class)));
    }
}
