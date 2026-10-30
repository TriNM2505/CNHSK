package com.cnhsk.learning.practice;

import java.time.Duration;

public enum PracticeSource {
    TOPIC_STUDY(AttemptKind.PRACTICE, FeedbackMode.RECORD, Duration.ofHours(2), true, false),
    TOPIC_PRACTICE(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    TOPIC_TEST(AttemptKind.TOPIC_TEST, FeedbackMode.DEFERRED, Duration.ofHours(24), true, false),
    GRAMMAR_LESSON(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    PRON_STAGE(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofDays(7), false, false),
    CHAR_RECOGNITION(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    CHAR_WRITING(AttemptKind.PRACTICE, FeedbackMode.RECORD, Duration.ofMinutes(30), true, false),
    WRITING_CHALLENGE(AttemptKind.WRITING_CHALLENGE, FeedbackMode.DEFERRED, Duration.ofSeconds(210), true, false),
    GAME_ROUND(AttemptKind.GAME, FeedbackMode.IMMEDIATE, null, false, true);

    private final AttemptKind kind;
    private final FeedbackMode feedback;
    private final Duration ttl;
    private final boolean allowSkip;
    private final boolean maxScoreFromServed;

    PracticeSource(AttemptKind kind, FeedbackMode feedback, Duration ttl,
                   boolean allowSkip, boolean maxScoreFromServed) {
        this.kind = kind;
        this.feedback = feedback;
        this.ttl = ttl;
        this.allowSkip = allowSkip;
        this.maxScoreFromServed = maxScoreFromServed;
    }

    public AttemptKind kind() {
        return kind;
    }

    public FeedbackMode feedback() {
        return feedback;
    }

    /**
     * Returns the fixed lifetime, or null for GAME_ROUND, which requires a positive
     * server-supplied ttlOverride. Fixed-lifetime sources must reject overrides at start.
     */
    public Duration ttl() {
        return ttl;
    }

    public boolean allowSkip() {
        return allowSkip;
    }

    public boolean maxScoreFromServed() {
        return maxScoreFromServed;
    }
}
