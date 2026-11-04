package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.mastery")
public class MasteryConfig {

    @NotNull
    @Min(1)
    @Max(8)
    private Integer window;

    @NotNull
    @Min(0)
    private Integer maxPracticeInWindow;

    @NotNull
    @Min(1)
    private Integer minEvidence;

    @NotNull
    @Min(1)
    private Integer minDistinctDays;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal minConfidence;

    @NotNull
    @Positive
    private Integer halfLifeDays;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private BigDecimal reviewBelow;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private BigDecimal extendFrom;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal hysteresis;

    @NotNull
    @Valid
    private DueDays dueDays;

    @AssertTrue(message = "Mastery configuration relationships are invalid")
    public boolean isMasteryConfigurationValid() {
        return window != null
                && maxPracticeInWindow != null
                && maxPracticeInWindow <= window
                && minEvidence != null
                && minEvidence <= window
                && minDistinctDays != null
                && minDistinctDays <= window
                && reviewBelow != null
                && extendFrom != null
                && reviewBelow.compareTo(extendFrom) < 0
                && extendFrom.compareTo(BigDecimal.valueOf(100)) <= 0
                && hysteresis != null
                && hysteresis.compareTo(reviewBelow) < 0;
    }

    public Settings settings() {
        return new Settings(window, maxPracticeInWindow, minEvidence, minDistinctDays, minConfidence, halfLifeDays,
                reviewBelow, extendFrom, hysteresis, dueDays.getReview(), dueDays.getConsolidate(), dueDays.getExtend());
    }

    public Integer getWindow() {
        return window;
    }

    public void setWindow(Integer window) {
        this.window = window;
    }

    public Integer getMaxPracticeInWindow() {
        return maxPracticeInWindow;
    }

    public void setMaxPracticeInWindow(Integer maxPracticeInWindow) {
        this.maxPracticeInWindow = maxPracticeInWindow;
    }

    public Integer getMinEvidence() {
        return minEvidence;
    }

    public void setMinEvidence(Integer minEvidence) {
        this.minEvidence = minEvidence;
    }

    public Integer getMinDistinctDays() {
        return minDistinctDays;
    }

    public void setMinDistinctDays(Integer minDistinctDays) {
        this.minDistinctDays = minDistinctDays;
    }

    public BigDecimal getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(BigDecimal minConfidence) {
        this.minConfidence = minConfidence;
    }

    public Integer getHalfLifeDays() {
        return halfLifeDays;
    }

    public void setHalfLifeDays(Integer halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public BigDecimal getReviewBelow() {
        return reviewBelow;
    }

    public void setReviewBelow(BigDecimal reviewBelow) {
        this.reviewBelow = reviewBelow;
    }

    public BigDecimal getExtendFrom() {
        return extendFrom;
    }

    public void setExtendFrom(BigDecimal extendFrom) {
        this.extendFrom = extendFrom;
    }

    public BigDecimal getHysteresis() {
        return hysteresis;
    }

    public void setHysteresis(BigDecimal hysteresis) {
        this.hysteresis = hysteresis;
    }

    public DueDays getDueDays() {
        return dueDays;
    }

    public void setDueDays(DueDays dueDays) {
        this.dueDays = dueDays;
    }

    public static class DueDays {

        @NotNull
        @Positive
        private Integer review;

        @NotNull
        @Positive
        private Integer consolidate;

        @NotNull
        @Positive
        private Integer extend;

        public Integer getReview() {
            return review;
        }

        public void setReview(Integer review) {
            this.review = review;
        }

        public Integer getConsolidate() {
            return consolidate;
        }

        public void setConsolidate(Integer consolidate) {
            this.consolidate = consolidate;
        }

        public Integer getExtend() {
            return extend;
        }

        public void setExtend(Integer extend) {
            this.extend = extend;
        }
    }

    public record Settings(
            int window,
            int maxPracticeInWindow,
            int minEvidence,
            int minDistinctDays,
            BigDecimal minConfidence,
            int halfLifeDays,
            BigDecimal reviewBelow,
            BigDecimal extendFrom,
            BigDecimal hysteresis,
            int reviewDueDays,
            int consolidateDueDays,
            int extendDueDays) {

        public Settings {
            Objects.requireNonNull(minConfidence);
            Objects.requireNonNull(reviewBelow);
            Objects.requireNonNull(extendFrom);
            Objects.requireNonNull(hysteresis);
        }
    }
}
