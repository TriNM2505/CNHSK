package com.cnhsk.learning.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.cnhsk.learning.review.FsrsConfig.Settings;

/** Pure FSRS-5 scheduling; callers supply UTC instants and persist the returned state. */
public final class FsrsScheduler {

    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final BigDecimal NINETEEN = BigDecimal.valueOf(19);
    private static final BigDecimal EIGHTY_ONE = BigDecimal.valueOf(81);

    private final Settings settings;

    public FsrsScheduler(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Result first(Rating g, Instant now) {
        Objects.requireNonNull(g, "rating");
        Objects.requireNonNull(now, "now");
        BigDecimal stability = BigDecimal.valueOf(weight(g.grade() - 1));
        BigDecimal difficulty = difficulty(weight(4) - StrictMath.exp(weight(5) * (g.grade() - 1)) + 1);
        return result(stability, difficulty, 0, now);
    }

    public Result review(Memory prev, Rating g, Instant now) {
        Objects.requireNonNull(prev, "memory");
        Objects.requireNonNull(g, "rating");
        Objects.requireNonNull(now, "now");

        Duration elapsed = Duration.between(prev.lastReviewAt(), now);
        if (elapsed.isNegative()) {
            elapsed = Duration.ZERO;
        }
        double days = elapsed.getSeconds() / 86_400.0 + elapsed.getNano() / 86_400_000_000_000.0;
        double stability = prev.stability().doubleValue();
        double difficulty = prev.difficulty().doubleValue();
        double nextStability;
        if (elapsed.compareTo(ONE_DAY) < 0) {
            nextStability = stability * StrictMath.exp(weight(17) * (g.grade() - 3 + weight(18)));
        } else {
            double retention = StrictMath.pow(1 + 19.0 / 81.0 * days / stability, -0.5);
            if (g == Rating.AGAIN) {
                nextStability = StrictMath.min(stability, weight(11) * StrictMath.pow(difficulty, -weight(12))
                        * (StrictMath.pow(stability + 1, weight(13)) - 1)
                        * StrictMath.exp(weight(14) * (1 - retention)));
            } else {
                double hard = g == Rating.HARD ? weight(15) : 1;
                double easy = g == Rating.EASY ? weight(16) : 1;
                nextStability = stability * (1 + StrictMath.exp(weight(8)) * (11 - difficulty)
                        * StrictMath.pow(stability, -weight(9))
                        * (StrictMath.exp(weight(10) * (1 - retention)) - 1) * hard * easy);
            }
        }
        if (!Double.isFinite(nextStability) || nextStability <= 0) {
            throw new IllegalStateException("FSRS calculated stability must be positive and finite");
        }

        // D0(4) is deliberately not clamped before mean reversion.
        double initialEasyDifficulty = weight(4) - StrictMath.exp(3 * weight(5)) + 1;
        BigDecimal nextDifficulty = difficulty(weight(7) * initialEasyDifficulty + (1 - weight(7))
                * (difficulty - weight(6) * (g.grade() - 3) * (10 - difficulty) / 9));
        int lapses = g == Rating.AGAIN ? Math.incrementExact(prev.lapseCount()) : prev.lapseCount();
        return result(BigDecimal.valueOf(nextStability), nextDifficulty, lapses, now);
    }

    private double weight(int index) {
        return settings.weights().get(index);
    }

    private BigDecimal difficulty(double value) {
        double bounded = StrictMath.max(1, StrictMath.min(10, value));
        if (!Double.isFinite(bounded)) {
            throw new IllegalStateException("FSRS calculated difficulty must be finite");
        }
        return BigDecimal.valueOf(bounded);
    }

    private Result result(BigDecimal stability, BigDecimal difficulty, int lapseCount, Instant now) {
        BigDecimal retentionSquared = settings.desiredRetention().multiply(settings.desiredRetention());
        // Equivalent to S * (r^-2 - 1) * 81/19; round once, including exact r=0.9 ties.
        BigDecimal interval = stability.multiply(BigDecimal.ONE.subtract(retentionSquared)).multiply(EIGHTY_ONE)
                .divide(retentionSquared.multiply(NINETEEN), 0, RoundingMode.HALF_UP);
        int days = interval.max(BigDecimal.valueOf(settings.minIntervalDays()))
                .min(BigDecimal.valueOf(settings.maxIntervalDays())).intValueExact();
        return new Result(stability, difficulty, lapseCount, days, now.plus(days, ChronoUnit.DAYS));
    }

    private static void validateMemory(BigDecimal stability, BigDecimal difficulty, int lapseCount) {
        if (stability == null || !Double.isFinite(stability.doubleValue()) || stability.doubleValue() <= 0
                || difficulty == null || difficulty.compareTo(BigDecimal.ONE) < 0
                || difficulty.compareTo(BigDecimal.TEN) > 0 || lapseCount < 0) {
            throw new IllegalArgumentException("FSRS memory requires positive finite stability, difficulty 1-10 and nonnegative lapses");
        }
    }

    public enum Rating {
        AGAIN(1), HARD(2), GOOD(3), EASY(4);

        private final int grade;

        Rating(int grade) {
            this.grade = grade;
        }

        public int grade() {
            return grade;
        }
    }

    public record Memory(BigDecimal stability, BigDecimal difficulty, Instant lastReviewAt, int lapseCount) {

        public Memory {
            validateMemory(stability, difficulty, lapseCount);
            if (lastReviewAt == null) {
                throw new IllegalArgumentException("FSRS last review instant is required");
            }
        }
    }

    public record Result(BigDecimal stability, BigDecimal difficulty, int lapseCount,
            int intervalDays, Instant nextReviewAt) {

        public Result {
            validateMemory(stability, difficulty, lapseCount);
            if (intervalDays < 1 || intervalDays > 365 || nextReviewAt == null) {
                throw new IllegalArgumentException("FSRS result requires an interval of 1-365 days and a review instant");
            }
        }
    }
}
