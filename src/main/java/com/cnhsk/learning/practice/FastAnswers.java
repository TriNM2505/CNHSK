package com.cnhsk.learning.practice;

import java.util.Collection;
import java.util.Map;

import com.cnhsk.learning.practice.entity.AttemptItem;

/**
 * An attempt flagged because too many answers came faster than an item's minimum time still keeps its
 * other answers: only the fast answers are dropped from progress and review schedules. An attempt flagged
 * by a whole-session rule, with no fast answer to blame, is dropped as a whole.
 */
public final class FastAnswers {

    private FastAnswers() {
    }

    public static boolean excludesWholeAttempt(boolean attemptSuspicious, Collection<AttemptItem> items) {
        return attemptSuspicious && items.stream().noneMatch(FastAnswers::isFast);
    }

    public static boolean isFast(AttemptItem item) {
        Map<String, Object> meta = item.getMeta();
        return meta != null && Boolean.TRUE.equals(meta.get("suspicious"));
    }
}
