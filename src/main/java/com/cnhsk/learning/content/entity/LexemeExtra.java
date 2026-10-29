package com.cnhsk.learning.content.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LexemeExtra(
        List<Example> examples,
        List<String> tags,
        String structure,
        String explanation,
        String commonMistake,
        List<Exercise> exercises) {

    public LexemeExtra {
        examples = examples == null ? null : List.copyOf(examples);
        tags = tags == null ? null : List.copyOf(tags);
        exercises = exercises == null ? null : List.copyOf(exercises);
    }

    public static LexemeExtra empty() {
        return new LexemeExtra(null, null, null, null, null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Example(String cn, String pinyin, String vi) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Exercise(int no, String stem, List<Option> options, String answerKey, String explanation) {
        public Exercise {
            options = options == null ? null : List.copyOf(options);
        }
    }

    public record Option(String key, String text) {
    }
}
