package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

import com.cnhsk.learning.mastery.MasteryCalculator.Result;
import com.cnhsk.learning.mastery.MasteryConfig.Settings;

/** Pure state transition logic for MP-1.0. */
public final class MasteryStateMachine {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public Decision transition(MasteryState previous, Result result, Instant updatedAt, Settings settings) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(settings, "settings");

        if (result.evidenceCount() == 0) {
            return new Decision(MasteryState.NOT_YET, null);
        }

        boolean enough = result.evidenceCount() >= settings.minEvidence()
                && result.distinctDays() >= settings.minDistinctDays()
                && result.confidence().compareTo(settings.minConfidence()) >= 0;
        if (!enough) {
            return new Decision(MasteryState.INSUFFICIENT, null);
        }

        BigDecimal value = Objects.requireNonNull(result.value(), "value");
        MasteryState state = stateFor(value, previous, settings);
        int dueDays = switch (state) {
            case REVIEW -> settings.reviewDueDays();
            case CONSOLIDATE -> settings.consolidateDueDays();
            case EXTEND -> settings.extendDueDays();
            case NOT_YET, INSUFFICIENT, INFERRED -> throw new IllegalStateException("Unexpected calculated state");
        };
        Instant nextReviewAt = updatedAt.atZone(VIETNAM_ZONE).plusDays(dueDays).toInstant();
        return new Decision(state, nextReviewAt);
    }

    private MasteryState stateFor(BigDecimal value, MasteryState previous, Settings settings) {
        BigDecimal reviewThreshold = settings.reviewBelow();
        BigDecimal extendThreshold = settings.extendFrom();
        BigDecimal lowerReviewThreshold = reviewThreshold.subtract(settings.hysteresis());
        BigDecimal lowerExtendThreshold = extendThreshold.subtract(settings.hysteresis());

        if (previous == MasteryState.EXTEND) {
            if (value.compareTo(lowerExtendThreshold) >= 0) {
                return MasteryState.EXTEND;
            }
            return value.compareTo(reviewThreshold) >= 0 ? MasteryState.CONSOLIDATE : MasteryState.REVIEW;
        }
        if (previous == MasteryState.CONSOLIDATE) {
            if (value.compareTo(extendThreshold) >= 0) {
                return MasteryState.EXTEND;
            }
            return value.compareTo(lowerReviewThreshold) < 0 ? MasteryState.REVIEW : MasteryState.CONSOLIDATE;
        }
        if (value.compareTo(extendThreshold) >= 0) {
            return MasteryState.EXTEND;
        }
        return value.compareTo(reviewThreshold) >= 0 ? MasteryState.CONSOLIDATE : MasteryState.REVIEW;
    }

    public record Decision(MasteryState state, Instant nextReviewAt) {
        public Decision {
            Objects.requireNonNull(state, "state");
            if ((state == MasteryState.NOT_YET || state == MasteryState.INSUFFICIENT) && nextReviewAt != null) {
                throw new IllegalArgumentException("notyet and insufficient states have no due date");
            }
        }
    }
}
