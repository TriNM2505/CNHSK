package com.cnhsk.learning.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_knowledge_points", schema = "learning")
@IdClass(QuestionKnowledgePointId.class)
public class QuestionKnowledgePoint {

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Id
    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private KnowledgeLabelRole role = KnowledgeLabelRole.PRIMARY;

    protected QuestionKnowledgePoint() {
    }

    public QuestionKnowledgePoint(Long questionId, Long knowledgePointId, KnowledgeLabelRole role) {
        this.questionId = questionId;
        this.knowledgePointId = knowledgePointId;
        this.role = role;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public KnowledgeLabelRole getRole() {
        return role;
    }

    public void setRole(KnowledgeLabelRole role) {
        this.role = role;
    }
}
