package com.cnhsk.learning.knowledge.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One stored option. Picture options keep a short text describing the picture for editors; learners see only the picture. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionOption(
        String key,
        String text,
        @JsonProperty("is_correct") boolean isCorrect,
        @JsonProperty("error_kid") String errorKid,
        @JsonProperty("error_note") String errorNote,
        @JsonProperty("image_url") String imageUrl) {

    public QuestionOption(String key, String text, boolean isCorrect, String errorKid, String errorNote) {
        this(key, text, isCorrect, errorKid, errorNote, null);
    }
}
