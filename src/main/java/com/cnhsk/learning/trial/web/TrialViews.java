package com.cnhsk.learning.trial.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public final class TrialViews {
    private TrialViews() { }

    /**
     * One multiple-choice question. {@code prompt} is what is shown (or, when {@code pinyin} is null in a listening
     * question, what is played); {@code answer} is the index of the right option.
     */
    @Schema(name = "TrialQuestion")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TrialQuestion(String prompt, String pinyin, String audioUrl, List<String> options, int answer,
            String explanation) {
        public TrialQuestion { options = List.copyOf(options); }
    }

    @Schema(name = "TrialSet")
    public record TrialSet(@Schema(allowableValues = {"VOCAB", "GRAMMAR", "LISTENING", "HANZI"}) String feature,
            int hskLevel, List<TrialQuestion> questions) {
        public TrialSet { questions = List.copyOf(questions); }
    }
}
