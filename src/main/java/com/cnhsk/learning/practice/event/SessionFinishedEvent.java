package com.cnhsk.learning.practice.event;

import com.cnhsk.learning.practice.PracticeSource;

/** Published after the source finish hook, inside the same transaction as scores and study evidence. */
public record SessionFinishedEvent(long userId, long sessionId, PracticeSource source, Long refId,
        Long score, Long maxScore, boolean suspicious) {
}
