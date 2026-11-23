package com.cnhsk.learning.practice;

import java.time.Duration;

import com.cnhsk.learning.api.PaidFeature;

public enum PracticeSource {
    TOPIC_STUDY(AttemptKind.PRACTICE, FeedbackMode.RECORD, Duration.ofHours(2), true, false),
    TOPIC_PRACTICE(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    TOPIC_TEST(AttemptKind.TOPIC_TEST, FeedbackMode.DEFERRED, Duration.ofHours(24), true, false),
    GRAMMAR_LESSON(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    PRON_STAGE(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofDays(7), false, false),
    CHAR_RECOGNITION(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    CHAR_WRITING(AttemptKind.PRACTICE, FeedbackMode.RECORD, Duration.ofMinutes(30), true, false),
    WRITING_CHALLENGE(AttemptKind.WRITING_CHALLENGE, FeedbackMode.DEFERRED, Duration.ofSeconds(210), true, false),
    GAME_ROUND(AttemptKind.GAME, FeedbackMode.IMMEDIATE, null, false, true),
    MOCK_EXAM(AttemptKind.EXAM, FeedbackMode.DEFERRED, Duration.ofHours(24), true, false),
    PLACEMENT(AttemptKind.EXAM, FeedbackMode.DEFERRED, Duration.ofHours(2), true, false),
    TARGETED_PRACTICE(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    DUE_REVIEW(AttemptKind.PRACTICE, FeedbackMode.IMMEDIATE, Duration.ofHours(2), true, false),
    QUIZ(AttemptKind.QUIZ, FeedbackMode.DEFERRED, Duration.ofMinutes(30), true, false),
    QUIZ_RETRY(AttemptKind.PRACTICE, FeedbackMode.DEFERRED, Duration.ofMinutes(30), true, false),
    CONTEST(AttemptKind.CONTEST, FeedbackMode.DEFERRED, null, true, false);

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
     * Returns the fixed lifetime, or null for GAME_ROUND and CONTEST, which require a positive
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

    /** The learning allowance a new session of this source spends, or null when it is not limited. */
    public PaidFeature quotaFeature() {
        return switch (this) {
            case TOPIC_STUDY, TOPIC_PRACTICE, TOPIC_TEST -> PaidFeature.VOCAB;
            case GRAMMAR_LESSON -> PaidFeature.GRAMMAR;
            case PRON_STAGE -> PaidFeature.PRONUNCIATION;
            case CHAR_RECOGNITION, CHAR_WRITING, WRITING_CHALLENGE -> PaidFeature.HANZI;
            default -> null;
        };
    }
}
