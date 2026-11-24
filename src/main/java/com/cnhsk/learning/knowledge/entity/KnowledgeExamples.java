package com.cnhsk.learning.knowledge.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Model sentences of a node: at least three correct ones and typical wrong ones tied to an error code. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeExamples(List<Correct> ok, List<Wrong> err) {

    public KnowledgeExamples {
        ok = ok == null ? List.of() : List.copyOf(ok);
        err = err == null ? List.of() : List.copyOf(err);
    }

    public static KnowledgeExamples empty() {
        return new KnowledgeExamples(List.of(), List.of());
    }

    public record Correct(String cn, String pinyin, String vi) { }

    public record Wrong(String cn, String errorCode, String note) { }
}
