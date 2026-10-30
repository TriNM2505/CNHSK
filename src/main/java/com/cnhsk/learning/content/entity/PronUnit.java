package com.cnhsk.learning.content.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PronUnit(
        String code,
        String audioUrl,
        String prompt,
        List<Option> options,
        String answerKey,
        String explanation) {

    public PronUnit {
        options = options == null ? null : List.copyOf(options);
    }

    public record Option(String key, String text) {
    }
}
