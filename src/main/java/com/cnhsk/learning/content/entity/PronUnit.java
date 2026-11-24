package com.cnhsk.learning.content.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One listening item of a pronunciation stage. {@code kid} is the phonetics node (NA) the item trains, so a
 * listening answer becomes evidence on that node; units stored before it existed simply have none.
 * {@code speakText} is Chinese text the browser voice reads when there is no recorded {@code audioUrl}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PronUnit(
        String code,
        String audioUrl,
        String prompt,
        List<Option> options,
        String answerKey,
        String explanation,
        String kid,
        String speakText) {

    public PronUnit {
        options = options == null ? null : List.copyOf(options);
    }

    public PronUnit(String code, String audioUrl, String prompt, List<Option> options, String answerKey, String explanation) {
        this(code, audioUrl, prompt, options, answerKey, explanation, null, null);
    }

    public PronUnit(String code, String audioUrl, String prompt, List<Option> options, String answerKey,
            String explanation, String kid) {
        this(code, audioUrl, prompt, options, answerKey, explanation, kid, null);
    }

    /** A unit is playable with a recorded file or, without one, with Chinese text the browser voice reads. */
    public boolean playable() {
        return (audioUrl != null && !audioUrl.isBlank()) || (speakText != null && !speakText.isBlank());
    }

    public record Option(String key, String text) {
    }
}
