package com.cnhsk.learning.review;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.fsrs")
public class FsrsConfig {

    @NotNull
    @Size(min = 19, max = 19)
    private List<Double> weights;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = false)
    private BigDecimal desiredRetention;

    @NotNull
    @Min(1)
    @Max(365)
    private Integer minIntervalDays;

    @NotNull
    @Min(1)
    @Max(365)
    private Integer maxIntervalDays;

    @NotNull
    @Positive
    private Integer sessionMaxItems;

    @AssertTrue(message = "FSRS weights must contain 19 finite numbers with positive initial stability")
    public boolean isWeightsValid() {
        return validWeights(weights);
    }

    @AssertTrue(message = "FSRS minimum interval must not exceed maximum interval")
    public boolean isIntervalRangeValid() {
        return minIntervalDays != null && maxIntervalDays != null && minIntervalDays <= maxIntervalDays;
    }

    public Settings settings() {
        return new Settings(weights, desiredRetention, minIntervalDays, maxIntervalDays);
    }

    public List<Double> getWeights() {
        return weights;
    }

    public void setWeights(List<Double> weights) {
        this.weights = weights;
    }

    public BigDecimal getDesiredRetention() {
        return desiredRetention;
    }

    public void setDesiredRetention(BigDecimal desiredRetention) {
        this.desiredRetention = desiredRetention;
    }

    public Integer getMinIntervalDays() {
        return minIntervalDays;
    }

    public void setMinIntervalDays(Integer minIntervalDays) {
        this.minIntervalDays = minIntervalDays;
    }

    public Integer getMaxIntervalDays() {
        return maxIntervalDays;
    }

    public void setMaxIntervalDays(Integer maxIntervalDays) {
        this.maxIntervalDays = maxIntervalDays;
    }

    public Integer getSessionMaxItems() {
        return sessionMaxItems;
    }

    public void setSessionMaxItems(Integer sessionMaxItems) {
        this.sessionMaxItems = sessionMaxItems;
    }

    private static boolean validWeights(List<Double> values) {
        if (values == null || values.size() != 19) {
            return false;
        }
        for (int index = 0; index < values.size(); index++) {
            Double value = values.get(index);
            if (value == null || !Double.isFinite(value) || index < 4 && value <= 0) {
                return false;
            }
        }
        return true;
    }

    /** Immutable numerical settings, usable without a Spring context. */
    public record Settings(List<Double> weights, BigDecimal desiredRetention,
            int minIntervalDays, int maxIntervalDays) {

        public Settings {
            if (!validWeights(weights) || desiredRetention == null || desiredRetention.signum() <= 0
                    || desiredRetention.compareTo(BigDecimal.ONE) >= 0
                    || minIntervalDays < 1 || maxIntervalDays > 365 || minIntervalDays > maxIntervalDays) {
                throw new IllegalArgumentException("FSRS settings are invalid");
            }
            weights = List.copyOf(weights);
        }
    }
}
