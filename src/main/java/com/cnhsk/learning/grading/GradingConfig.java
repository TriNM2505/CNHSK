package com.cnhsk.learning.grading;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.grading")
public class GradingConfig {
    @NotNull @Positive private Integer claimHours;
    @NotNull @Positive private Integer gradingHours;
    @NotNull @PositiveOrZero private Integer teacherPayoutCredits;
    @NotNull @Positive private Integer minHanzi;
    @NotNull @Positive private Integer maxHanzi;
    @NotNull @Positive @Max(3000) private Integer maxChars;
    @NotNull @Positive private Integer maxActiveClaims;
    @NotBlank private String expiryJobCron;

    public Integer getClaimHours() { return claimHours; }
    public void setClaimHours(Integer claimHours) { this.claimHours = claimHours; }
    public Integer getGradingHours() { return gradingHours; }
    public void setGradingHours(Integer gradingHours) { this.gradingHours = gradingHours; }
    public Integer getTeacherPayoutCredits() { return teacherPayoutCredits; }
    public void setTeacherPayoutCredits(Integer teacherPayoutCredits) { this.teacherPayoutCredits = teacherPayoutCredits; }
    public Integer getMinHanzi() { return minHanzi; }
    public void setMinHanzi(Integer minHanzi) { this.minHanzi = minHanzi; }
    public Integer getMaxHanzi() { return maxHanzi; }
    public void setMaxHanzi(Integer maxHanzi) { this.maxHanzi = maxHanzi; }
    public Integer getMaxChars() { return maxChars; }
    public void setMaxChars(Integer maxChars) { this.maxChars = maxChars; }
    public Integer getMaxActiveClaims() { return maxActiveClaims; }
    public void setMaxActiveClaims(Integer maxActiveClaims) { this.maxActiveClaims = maxActiveClaims; }
    public String getExpiryJobCron() { return expiryJobCron; }
    public void setExpiryJobCron(String expiryJobCron) { this.expiryJobCron = expiryJobCron; }

    @AssertTrue(message = "Grading lengths must satisfy minHanzi <= maxHanzi <= maxChars <= 3000")
    public boolean isLengthRangeValid() {
        return minHanzi != null && maxHanzi != null && maxChars != null
                && minHanzi <= maxHanzi && maxHanzi <= maxChars && maxChars <= 3000;
    }

    @AssertTrue(message = "Grading expiry cron must be a valid Spring cron expression")
    public boolean isExpiryCronValid() {
        return expiryJobCron != null && CronExpression.isValidExpression(expiryJobCron);
    }
}
