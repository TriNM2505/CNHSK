package com.cnhsk.learning.knowledge.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionOption(
        String key,
        String text,
        @JsonProperty("is_correct") boolean isCorrect,
        @JsonProperty("error_kid") String errorKid,
        @JsonProperty("error_note") String errorNote) {
}
