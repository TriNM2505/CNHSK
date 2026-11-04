package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.cnhsk.learning.mastery.MasteryConfig.Settings;

/** Pure MP-1.0 calculation over a caller-provided evidence window. */
public final class MasteryCalculator {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    public Result calculate(List<Evidence> evidence, Instant today, Settings settings) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(settings, "settings");

        List<Evidence> ordered = new ArrayList<>(evidence);
        ordered.sort(Comparator.comparing(Evidence::answeredAt).reversed()
                .thenComparing(Evidence::itemId, Comparator.reverseOrder()));

        List<Evidence> window = new ArrayList<>(settings.window());
        int practiceCount = 0;
        for (Evidence item : ordered) {
            if (item.practice() && practiceCount >= settings.maxPracticeInWindow()) {
                continue;
            }
            window.add(item);
            if (item.practice()) {
                practiceCount++;
            }
            if (window.size() == settings.window()) {
                break;
            }
        }

        if (window.isEmpty()) {
            return new Result(null, BigDecimal.ZERO, 0, 0, List.of());
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        Set<java.time.LocalDate> evidenceDays = new HashSet<>();
        for (Evidence item : window) {
            BigDecimal weight = item.staticWeight().multiply(decay(item.answeredAt(), today, settings.halfLifeDays()),
                    PRECISION);
            totalWeight = totalWeight.add(weight, PRECISION);
            weightedScore = weightedScore.add(weight.multiply(BigDecimal.valueOf(item.score()), PRECISION), PRECISION);
            evidenceDays.add(item.answeredAt().atZone(VIETNAM_ZONE).toLocalDate());
        }

        BigDecimal value = weightedScore.divide(totalWeight, PRECISION).setScale(1, RoundingMode.HALF_UP);
        return new Result(value, totalWeight, window.size(), evidenceDays.size(), window);
    }

    private BigDecimal decay(Instant answeredAt, Instant today, int halfLifeDays) {
        Duration age = Duration.between(answeredAt, today);
        double ageDays = age.isNegative() ? 0.0 : age.getSeconds() / 86_400.0 + age.getNano() / 86_400_000_000_000.0;
        return BigDecimal.valueOf(Math.pow(HALF.doubleValue(), ageDays / halfLifeDays));
    }

    public record Evidence(Long itemId, Instant answeredAt, BigDecimal staticWeight, int score, boolean practice) {

        public Evidence {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(answeredAt, "answeredAt");
            Objects.requireNonNull(staticWeight, "staticWeight");
            if (staticWeight.signum() <= 0) {
                throw new IllegalArgumentException("staticWeight must be positive");
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be between 0 and 100");
            }
        }
    }

    public record Result(BigDecimal value, BigDecimal confidence, int evidenceCount, int distinctDays,
            List<Evidence> evidence) {

        public Result {
            Objects.requireNonNull(confidence, "confidence");
            evidence = List.copyOf(evidence);
            if (evidenceCount < 0 || distinctDays < 0) {
                throw new IllegalArgumentException("evidence counts must not be negative");
            }
            if ((value == null) != (evidenceCount == 0) || evidence.size() != evidenceCount) {
                throw new IllegalArgumentException("an empty evidence window has a null value");
            }
        }
    }
}
