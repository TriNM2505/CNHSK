package com.cnhsk.learning.practice;

import java.time.Clock;
import java.util.Map;

import com.cnhsk.learning.api.GameRoundApi;
import com.cnhsk.learning.api.GameRoundHooks;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.learning.practice.entity.Attempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Keeps the engine's entities/internal types out of the community module. */
@Component
public class GameRoundPracticeHandler implements PracticeSourceHandler {
    private final GameRoundApi rounds;
    private final GameRoundHooks hooks;
    private final ObjectMapper mapper;
    private final Clock clock;

    public GameRoundPracticeHandler(GameRoundApi rounds, GameRoundHooks hooks, ObjectMapper mapper, Clock clock) {
        this.rounds = rounds;
        this.hooks = hooks;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override public PracticeSource source() { return PracticeSource.GAME_ROUND; }

    @Override
    public void validateAnswerContext(Attempt attempt, ServedItem item, Answer answer) {
        var round = round(attempt.getId());
        hooks.validateAnswer(round, rounds.totals(attempt.getId()), mapper.convertValue(item, PracticeApi.ServedItem.class),
                new PracticeApi.Answer(answer.no(), answer.answer(), answer.durationMs(), answer.replayCount(),
                        answer.skipped(), answer.audioFailed(), answer.clientMeta()), clock.instant());
    }

    @Override
    public Map<String, Object> onFinish(FinishContext context) {
        var round = round(context.sessionId());
        long correct = context.items().stream().filter(item -> item.counted() && Boolean.TRUE.equals(item.correct())).count();
        long wrong = context.items().stream().filter(item -> item.counted() && Boolean.FALSE.equals(item.correct())).count();
        long previous = rounds.bestCorrectBefore(context.userId(), round.gameCode(), context.sessionId());
        return hooks.onFinish(round, new GameRoundHooks.Finish(context.score(), context.maxScore(),
                context.suspicious(), context.submittedAt(), correct, wrong, previous));
    }

    private GameRoundHooks.Round round(long id) {
        return rounds.find(id).orElseThrow(() -> new IllegalStateException("Engine game snapshot is unavailable."));
    }
}
