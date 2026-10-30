package com.cnhsk.learning.practice;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Server-only snapshot. Responses must map it to ServedItemView. */
public record ServedItem(
        int no,
        ItemType type,
        Skill skill,
        Long lexemeId,
        Prompt prompt,
        List<Option> options,
        String answer,
        List<String> accept,
        String explanation,
        Rules rules) {

    public ServedItem {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(rules, "rules");
        options = options == null ? null : List.copyOf(options);
        accept = accept == null ? null : List.copyOf(accept);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prompt(
            String text,
            String pinyin,
            String sinoViet,
            String meaning,
            String audioUrl,
            String sentence,
            String hint,
            List<Example> examples,
            String kind) {

        public Prompt {
            examples = examples == null ? null : List.copyOf(examples);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Example(String cn, String pinyin, String vi) {
    }

    public record Option(String key, String text) {
    }

    public record Rules(int minDurationMs, int maxReplays, boolean hasAudio) {
    }
}
