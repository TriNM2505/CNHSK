package com.cnhsk.learning.exam;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Rules of the fixed-form placement test: one mixed form of HSK 1–3 questions, scored with a Rasch model that
 * allows for guessing (see {@link PlacementFormScoring}). Abilities are on a scale where HSK 2 items sit at 0
 * and each HSK level is one unit apart.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.placement")
public class PlacementConfig {

    /**
     * Abilities from which a learner reaches HSK 1, 2 and 3. Set from the estimates of typical sheets: about 7/10
     * right on a level's items and most of the levels below (e.g. 7-2-2 → HSK 1, 10-7-3 → HSK 2, 10-10-7 → HSK 3).
     */
    private java.util.List<Double> cuts = java.util.List.of(-1.6, 0.1, 1.7);

    /** Ability from which a learner is above what the form measures (about 9/10 right on HSK 3 items). */
    private double aboveAbility = 2.6;

    /** Spread of the prior on ability; wide enough that answers, not the prior, decide. */
    private double priorDeviation = 2.0;

    /** A standard error above this makes the result tentative. */
    private double maxStandardError = 0.75;

    /** More unanswered questions than this make the result tentative. */
    @Min(0)
    private int tentativeUnanswered = 5;

    /** Answers an item needs before the difficulty seen in them replaces its starting difficulty. */
    @Min(1)
    private int calibrationMinAnswers = 30;

    /** Exam codes that hold placement forms; these exams never appear as mock exams. */
    private String formCodePrefix = "PLACEMENT-";

    public java.util.List<Double> getCuts() { return cuts; }
    public void setCuts(java.util.List<Double> cuts) { this.cuts = cuts; }
    public double getAboveAbility() { return aboveAbility; }
    public void setAboveAbility(double aboveAbility) { this.aboveAbility = aboveAbility; }
    public double getPriorDeviation() { return priorDeviation; }
    public void setPriorDeviation(double priorDeviation) { this.priorDeviation = priorDeviation; }
    public double getMaxStandardError() { return maxStandardError; }
    public void setMaxStandardError(double maxStandardError) { this.maxStandardError = maxStandardError; }
    public int getTentativeUnanswered() { return tentativeUnanswered; }
    public void setTentativeUnanswered(int tentativeUnanswered) { this.tentativeUnanswered = tentativeUnanswered; }
    public int getCalibrationMinAnswers() { return calibrationMinAnswers; }
    public void setCalibrationMinAnswers(int calibrationMinAnswers) { this.calibrationMinAnswers = calibrationMinAnswers; }
    public String getFormCodePrefix() { return formCodePrefix; }
    public void setFormCodePrefix(String formCodePrefix) { this.formCodePrefix = formCodePrefix; }

    @AssertTrue(message = "Placement configuration is invalid")
    public boolean isPlacementConfigurationValid() {
        return priorDeviation > 0 && maxStandardError > 0 && cuts != null && cuts.size() == 3
                && cuts.get(0) < cuts.get(1) && cuts.get(1) < cuts.get(2) && cuts.get(2) < aboveAbility
                && formCodePrefix != null && formCodePrefix.matches("^[A-Z0-9-]{2,40}$");
    }
}
