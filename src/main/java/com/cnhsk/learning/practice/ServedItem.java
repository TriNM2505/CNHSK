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
        Rules rules,
        Long questionId,
        /** Knowledge node the server labelled this item with; absent from snapshots of unlabelled items. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String targetKid) {

    public ServedItem(int no, ItemType type, Skill skill, Long lexemeId, Prompt prompt,
            List<Option> options, String answer, List<String> accept, String explanation, Rules rules) {
        this(no, type, skill, lexemeId, prompt, options, answer, accept, explanation, rules, null, null);
    }

    public ServedItem(int no, ItemType type, Skill skill, Long lexemeId, Prompt prompt,
            List<Option> options, String answer, List<String> accept, String explanation, Rules rules, Long questionId) {
        this(no, type, skill, lexemeId, prompt, options, answer, accept, explanation, rules, questionId, null);
    }

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
            String kind,
            Integer strokeCount,
            String speakText,
            /** Picture the question is about, shown above the options. */
            String imageUrl) {

        public Prompt(String text, String pinyin, String sinoViet, String meaning, String audioUrl,
                String sentence, String hint, List<Example> examples, String kind) {
            this(text, pinyin, sinoViet, meaning, audioUrl, sentence, hint, examples, kind, null, null, null);
        }

        public Prompt(String text, String pinyin, String sinoViet, String meaning, String audioUrl,
                String sentence, String hint, List<Example> examples, String kind, Integer strokeCount) {
            this(text, pinyin, sinoViet, meaning, audioUrl, sentence, hint, examples, kind, strokeCount, null, null);
        }

        public Prompt(String text, String pinyin, String sinoViet, String meaning, String audioUrl,
                String sentence, String hint, List<Example> examples, String kind, Integer strokeCount,
                String speakText) {
            this(text, pinyin, sinoViet, meaning, audioUrl, sentence, hint, examples, kind, strokeCount, speakText, null);
        }

        public Prompt {
            examples = examples == null ? null : List.copyOf(examples);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Example(String cn, String pinyin, String vi) {
    }

    /** A choice; picture options carry an image and may have no text of their own. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Option(String key, String text, String imageUrl) {
        public Option(String key, String text) {
            this(key, text, null);
        }
    }

    public record Rules(int minDurationMs, int maxReplays, boolean hasAudio) {
    }
}
