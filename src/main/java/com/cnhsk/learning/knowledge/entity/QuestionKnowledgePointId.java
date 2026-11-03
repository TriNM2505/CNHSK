package com.cnhsk.learning.knowledge.entity;

import java.io.Serializable;
import java.util.Objects;

public class QuestionKnowledgePointId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;
    private Long knowledgePointId;

    public QuestionKnowledgePointId() {
    }

    public QuestionKnowledgePointId(Long questionId, Long knowledgePointId) {
        this.questionId = questionId;
        this.knowledgePointId = knowledgePointId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionKnowledgePointId that)) {
            return false;
        }
        return Objects.equals(questionId, that.questionId)
                && Objects.equals(knowledgePointId, that.knowledgePointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, knowledgePointId);
    }
}
