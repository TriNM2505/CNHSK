package com.cnhsk.community.game;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.learning.api.GameRoundHooks;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Pure game policy invoked by the learning adapter inside the engine's locked transaction. */
@Component
public class GameRoundHandler implements GameRoundHooks {
    private static final Logger log = LoggerFactory.getLogger(GameRoundHandler.class);
    private final GameRegistry registry;

    public GameRoundHandler(GameRegistry registry) { this.registry = registry; }

    @Override
    public void validateAnswer(Round round, PracticeApi.ServedItem item, PracticeApi.Answer answer, Instant now) {
        if (now.isAfter(round.deadline().plusSeconds(2))) {
            throw new ApiException(ErrorCode.ROUND_TIME_OVER, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ván đã hết thời gian trả lời.", Map.of());
        }
        // Audio-failure evidence remains an engine concern; the public game request cannot supply it.
        if (!answer.audioFailed() && !answer.skipped()) {
            definition(round).validateAnswer(item, answer.answer(), answer.durationMs());
        }
    }

    @Override
    public Map<String, Object> onFinish(Round round, Finish finish) {
        long served = round.gradableCount();
        if (finish.score() == null || finish.maxScore() == null || finish.score() < 0
                || finish.maxScore() < 0 || finish.score() > finish.maxScore() || finish.maxScore() > served
                || finish.correct() != finish.score() || finish.correct() + finish.wrong() > served) {
            log.error("Game score exceeded its server snapshot bounds.");
            throw new IllegalStateException("Invalid server game score bounds.");
        }
        long points = Math.multiplyExact(finish.score(), round.pointsPerCorrect());
        long previousBest = Math.multiplyExact(finish.bestCorrectBefore(), round.pointsPerCorrect());
        var extra = new LinkedHashMap<String, Object>();
        extra.put("points", points);
        extra.put("maxPoints", Math.multiplyExact(served, round.pointsPerCorrect()));
        extra.put("correct", finish.correct());
        extra.put("wrong", finish.wrong());
        extra.put("missed", served - finish.correct() - finish.wrong());
        long answered = finish.correct() + finish.wrong();
        extra.put("accuracy", answered == 0 ? null : Math.toIntExact(finish.correct() * 100 / answered));
        extra.put("durationMs", Math.toIntExact(Duration.between(round.servedAt(), finish.submittedAt()).toMillis()));
        extra.put("skill", definition(round).skill().name());
        extra.put("bestPoints", finish.suspicious() ? previousBest : Math.max(previousBest, points));
        extra.put("newBest", !finish.suspicious() && points > previousBest);
        extra.put("suspicious", finish.suspicious());
        return extra;
    }

    private GameDefinition definition(Round round) {
        return registry.find(round.gameCode()).orElseThrow(() -> new IllegalStateException("Stored game definition is unavailable."));
    }
}
