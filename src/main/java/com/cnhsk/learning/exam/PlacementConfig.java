package com.cnhsk.learning.exam;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.placement")
public class PlacementConfig {

    @Min(1)
    private int blockSize = 5;

    @Min(1)
    private int minItems = 25;

    @Min(1)
    private int maxItems = 35;

    @Min(0)
    private int upAt = 4;

    @Min(0)
    private int downAt = 2;

    @NotNull
    private Map<Integer, Integer> thresholds = Map.of(1, 60, 2, 65, 3, 75);

    @Min(1)
    private int sureMinItems = 10;

    @Min(1)
    private int startLevel = 2;

    @Min(1)
    private int minBankPerLevel = 10;

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getMinItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public int getUpAt() {
        return upAt;
    }

    public void setUpAt(int upAt) {
        this.upAt = upAt;
    }

    public int getDownAt() {
        return downAt;
    }

    public void setDownAt(int downAt) {
        this.downAt = downAt;
    }

    public Map<Integer, Integer> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<Integer, Integer> thresholds) {
        this.thresholds = thresholds;
    }

    public int getSureMinItems() {
        return sureMinItems;
    }

    public void setSureMinItems(int sureMinItems) {
        this.sureMinItems = sureMinItems;
    }

    public int getStartLevel() {
        return startLevel;
    }

    public void setStartLevel(int startLevel) {
        this.startLevel = startLevel;
    }

    public int getMinBankPerLevel() {
        return minBankPerLevel;
    }

    public void setMinBankPerLevel(int minBankPerLevel) {
        this.minBankPerLevel = minBankPerLevel;
    }

    @AssertTrue(message = "Placement configuration is invalid")
    public boolean isPlacementConfigurationValid() {
        return minItems <= maxItems
                && downAt < upAt
                && upAt <= blockSize
                && startLevel >= 1
                && startLevel <= 3
                && thresholds != null
                && thresholds.keySet().equals(Set.of(1, 2, 3))
                && thresholds.values().stream().allMatch(value -> value != null && value >= 0 && value <= 100);
    }
}
