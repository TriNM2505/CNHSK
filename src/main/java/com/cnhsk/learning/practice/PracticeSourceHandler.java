package com.cnhsk.learning.practice;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source hooks run synchronously inside the practice engine's transaction. */
public interface PracticeSourceHandler {

    PracticeSource source();

    Map<String, Object> onFinish(FinishContext context);

    default boolean isSuspicious(FinishContext context) {
        return false;
    }

    default void validateAnswer(ServedItem item, Answer answer) {
    }

    default Map<String, Object> enrichMeta(ServedItem item, Answer answer) {
        return Map.of();
    }

    /** Client evidence is validated by the source before it is stored. */
    record Answer(int no, String answer, int durationMs, int replayCount,
                  boolean skipped, boolean audioFailed, Map<String, Object> clientMeta) {

        public Answer {
            clientMeta = clientMeta == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(clientMeta));
        }
    }

    record GradedItem(ServedItem item, Answer answer, Boolean correct,
                      boolean counted, Map<String, Object> meta) {

        public GradedItem {
            meta = meta == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
        }
    }

    /** Scores and timestamps are server-derived facts, not values supplied by a client. */
    record FinishContext(long userId, long sessionId, PracticeSource source, Long refId,
                         Long score, Long maxScore, boolean suspicious,
                         Instant servedAt, Instant submittedAt, List<GradedItem> items) {

        public FinishContext {
            items = List.copyOf(items);
        }
    }
}
