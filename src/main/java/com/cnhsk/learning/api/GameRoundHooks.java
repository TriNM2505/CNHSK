package com.cnhsk.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Server-only extension contract. These records must never be serialized as HTTP responses. */
public interface GameRoundHooks {
    /** Runs while the round is locked, so {@code totals} cannot change before this answer is recorded. */
    void validateAnswer(Round round, AnswerTotals totals, PracticeApi.ServedItem item, PracticeApi.Answer answer,
            Instant now);

    Map<String, Object> onFinish(Round round, Finish finish);

    record Round(long id, long userId, GameCode gameCode, String status, Instant servedAt, Instant expiresAt,
                 int timeLimitSeconds, int pointsPerCorrect, int gradableCount, Map<String, Object> uiConfig,
                 List<PracticeApi.ServedItem> items) {
        public Round {
            uiConfig = Map.copyOf(uiConfig);
            items = List.copyOf(items);
        }

        public Instant deadline() { return servedAt.plusSeconds(3L + timeLimitSeconds); }
    }

    record Finish(Long score, Long maxScore, boolean suspicious, Instant submittedAt,
                  long correct, long wrong, long bestCorrectBefore) { }

    record AnswerTotals(long correct, long answeredCount) {
        public long wrong() { return answeredCount - correct; }
    }
}
