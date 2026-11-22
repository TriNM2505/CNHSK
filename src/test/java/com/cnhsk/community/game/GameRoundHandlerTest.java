package com.cnhsk.community.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameRoundHooks;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class GameRoundHandlerTest {
    private static final Instant NOW = Instant.parse("2035-01-01T00:00:00Z");
    private final GameRoundHandler handler = new GameRoundHandler(new GameRegistry(GameDefinitionFixtures.all()));

    @Test void AT15_twelveCorrectFiveWrongThreeMissedAndFloorAccuracyComeFromServerFacts() {
        var extra = handler.onFinish(round(20), finish(12, 20, false, 12, 5, 8));
        assertThat(extra).containsEntry("points", 120L).containsEntry("maxPoints", 200L)
                .containsEntry("correct", 12L).containsEntry("wrong", 5L).containsEntry("missed", 3L)
                .containsEntry("accuracy", 70).containsEntry("durationMs", 5000).containsEntry("skill", "VOCAB")
                .containsEntry("bestPoints", 120L).containsEntry("newBest", true).containsEntry("suspicious", false);
    }

    @Test void AT15_noAnswersHasNullAccuracyAndZeroDoesNotCreateANewRecord() {
        var extra = handler.onFinish(round(20), finish(0, 20, false, 0, 0, 0));
        assertThat(extra).containsEntry("accuracy", null).containsEntry("missed", 20L)
                .containsEntry("bestPoints", 0L).containsEntry("newBest", false);
    }

    @Test void Q_T06_02_suspiciousAndEqualOrLowerScoresKeepThePreviousBest() {
        assertThat(handler.onFinish(round(20), finish(12, 20, true, 12, 5, 8)))
                .containsEntry("points", 120L).containsEntry("bestPoints", 80L).containsEntry("newBest", false);
        assertThat(handler.onFinish(round(20), finish(8, 20, false, 8, 2, 8)))
                .containsEntry("bestPoints", 80L).containsEntry("newBest", false);
        assertThat(handler.onFinish(round(20), finish(1, 20, false, 1, 2, 8)))
                .containsEntry("bestPoints", 80L).containsEntry("newBest", false);
    }

    @Test void AT14_scoreAboveMaximumOrMaximumAboveSnapshotFailsInsteadOfClamping() {
        assertThatThrownBy(() -> handler.onFinish(round(20), finish(21, 20, false, 21, 0, 0)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invalid server game score bounds.");
        assertThatThrownBy(() -> handler.onFinish(round(20), finish(20, 21, false, 20, 0, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> handler.onFinish(round(20), finish(-1, 20, false, -1, 0, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void AT10_deadlinePlusTwoSecondsIsInclusiveAndTheNextInstantIsRejected() {
        var round = round(1);
        var answer = new PracticeApi.Answer(1, "A", 1000, 0, false, false, Map.of());
        handler.validateAnswer(round, totals(0, 0), round.items().get(0), answer, NOW.plusSeconds(35));
        assertThatThrownBy(() -> handler.validateAnswer(round, totals(0, 0), round.items().get(0), answer, NOW.plusSeconds(35).plusNanos(1)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ROUND_TIME_OVER));
    }

    @Test void theAnswerAfterTheLastLifeIsRefusedWhateverWasAnsweredCorrectly() {
        var registry = org.mockito.Mockito.mock(GameRegistry.class);
        var game = org.mockito.Mockito.mock(GameDefinition.class);
        org.mockito.Mockito.when(registry.find(GameCode.CHAR_RAIN)).thenReturn(java.util.Optional.of(game));
        org.mockito.Mockito.when(game.maxLives()).thenReturn(3);
        var limited = new GameRoundHandler(registry);
        var round = round(20);
        var answer = new PracticeApi.Answer(1, "A", 1000, 0, false, false, Map.of());
        limited.validateAnswer(round, totals(9, 11), round.items().get(0), answer, NOW);
        assertThatThrownBy(() -> limited.validateAnswer(round, totals(9, 12), round.items().get(0), answer, NOW))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ROUND_TIME_OVER));
    }

    private static GameRoundHooks.AnswerTotals totals(long correct, long answered) {
        return new GameRoundHooks.AnswerTotals(correct, answered);
    }

    private GameRoundHooks.Finish finish(long score, long max, boolean suspicious, long correct, long wrong, long before) {
        return new GameRoundHooks.Finish(score, max, suspicious, NOW.plusSeconds(5), correct, wrong, before);
    }
    private GameRoundHooks.Round round(int count) {
        var items = IntStream.rangeClosed(1, count).mapToObj(no -> new PracticeApi.ServedItem(no,
                PracticeApi.ItemType.MEANING_CHOICE, PracticeApi.Skill.VOCAB, null,
                new PracticeApi.Prompt("Mục tự soạn", null, null, null, null, null, null, null, null),
                List.of(new PracticeApi.Option("A", "Một"), new PracticeApi.Option("B", "Hai")),
                "A", null, null, new PracticeApi.Rules(300, 0, false))).toList();
        return new GameRoundHooks.Round(1, 7, GameCode.CHAR_RAIN, "IN_PROGRESS", NOW, NOW.plusSeconds(48),
                30, 10, count, Map.of(), items);
    }
}
