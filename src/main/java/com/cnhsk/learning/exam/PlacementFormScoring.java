package com.cnhsk.learning.exam;

import java.util.List;
import java.util.Objects;

/**
 * Places a learner from one mixed form of HSK 1–3 questions with a Rasch model that allows for guessing:
 * the chance to answer an item correctly is {@code c + (1 − c) · σ(θ − b)}, where {@code θ} is the learner's
 * ability, {@code b} the item's difficulty and {@code c} the chance of guessing it (½ for true/false, one over
 * the number of options otherwise). The ability is the most likely {@code θ} given every answer, with a wide
 * normal prior so a perfect or empty sheet still gives a finite value; its standard error tells how sure the
 * result is. Unanswered items count as wrong.
 *
 * <p>Difficulties live on one scale where HSK 2 is 0 and each HSK level is one unit: an item starts at its HSK
 * level shifted by its own difficulty (1–3), and once enough learners answered it, the difficulty observed in
 * their answers replaces that guess (see {@link #calibrated}).
 */
public final class PlacementFormScoring {
    private static final double STEP = 0.01;
    private static final double RANGE = 5.0;

    private PlacementFormScoring() {}

    /**
     * Difficulty an item starts with before learners answered it: its HSK level, shifted by its own 1–3 difficulty
     * so a hard item of one level overlaps an easy item of the next.
     */
    public static double priorDifficulty(int hskLevel, Integer difficulty) {
        int within = difficulty == null ? 2 : Math.max(1, Math.min(3, difficulty));
        return (hskLevel - 2) + (within - 2) * 0.6;
    }

    /** Chance of answering an item right by guessing alone. */
    public static double guessing(boolean trueFalse, int options) {
        if (trueFalse) return 0.5;
        return options >= 2 ? 1.0 / options : 0.25;
    }

    /**
     * Difficulty seen in real answers: learners of mean ability {@code meanAbility} answered correctly
     * {@code correctShare} of the time; removing the guessing share and inverting the model gives the item's b.
     */
    public static double calibrated(double meanAbility, double correctShare, double guess) {
        double known = (correctShare - guess) / (1 - guess);
        double clamped = Math.max(0.02, Math.min(0.98, known));
        return meanAbility - Math.log(clamped / (1 - clamped));
    }

    public static Estimate estimate(List<Item> items, double priorDeviation) {
        Objects.requireNonNull(items, "items must not be null");
        double best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (double theta = -RANGE; theta <= RANGE + 1e-9; theta += STEP) {
            double value = -theta * theta / (2 * priorDeviation * priorDeviation);
            for (Item item : items) {
                double p = probability(theta, item);
                value += item.correct() ? Math.log(p) : Math.log(1 - p);
            }
            if (value > bestValue) {
                bestValue = value;
                best = theta;
            }
        }
        double information = 1 / (priorDeviation * priorDeviation);
        for (Item item : items) {
            double logistic = 1 / (1 + Math.exp(-(best - item.difficulty())));
            double p = probability(best, item);
            double slope = (1 - item.guess()) * logistic * (1 - logistic);
            information += slope * slope / (p * (1 - p));
        }
        return new Estimate(round(best), round(1 / Math.sqrt(information)));
    }

    public static Conclusion decide(List<Item> items, PlacementConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        // Without a single answer the prior alone would place the learner; report no level instead.
        if (items.stream().noneMatch(Item::answered)) {
            return new Conclusion(Level.BELOW_1, Confidence.TENTATIVE, Double.NaN, Double.NaN);
        }
        Estimate estimate = estimate(items, config.getPriorDeviation());
        Level level = Level.BELOW_1;
        for (int hsk = 1; hsk <= 3; hsk++) {
            if (estimate.ability() >= config.getCuts().get(hsk - 1)) level = Level.forHsk(hsk);
        }
        if (estimate.ability() >= config.getAboveAbility()) level = Level.ABOVE_3;
        long unanswered = items.stream().filter(item -> !item.answered()).count();
        Confidence confidence = unanswered > config.getTentativeUnanswered()
                || estimate.standardError() > config.getMaxStandardError() ? Confidence.TENTATIVE : Confidence.SURE;
        return new Conclusion(level, confidence, estimate.ability(), estimate.standardError());
    }

    /** The HSK level stored on the profile: below HSK 1 starts at 1, above HSK 3 is recorded as 3. */
    public static int profileLevel(Level level) {
        return switch (level) {
            case BELOW_1, HSK1 -> 1;
            case HSK2 -> 2;
            case HSK3, ABOVE_3 -> 3;
        };
    }

    private static double probability(double theta, Item item) {
        double p = item.guess() + (1 - item.guess()) / (1 + Math.exp(-(theta - item.difficulty())));
        return Math.max(1e-6, Math.min(1 - 1e-6, p));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** One answered (or skipped) item of the form. */
    public record Item(double difficulty, double guess, boolean correct, boolean answered) {
        public Item {
            if (guess < 0 || guess >= 1) throw new IllegalArgumentException("Guessing chance is invalid");
        }
    }

    public record Estimate(double ability, double standardError) {}

    public record Conclusion(Level level, Confidence confidence, double ability, double standardError) {
        public Conclusion {
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(confidence, "confidence must not be null");
        }
    }

    public enum Level {
        BELOW_1,
        HSK1,
        HSK2,
        HSK3,
        ABOVE_3;

        static Level forHsk(int hsk) {
            return switch (hsk) {
                case 1 -> HSK1;
                case 2 -> HSK2;
                case 3 -> HSK3;
                default -> throw new IllegalArgumentException("Unsupported placement level: " + hsk);
            };
        }
    }

    public enum Confidence {
        SURE,
        TENTATIVE
    }
}
