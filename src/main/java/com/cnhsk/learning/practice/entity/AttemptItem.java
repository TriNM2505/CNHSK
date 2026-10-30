package com.cnhsk.learning.practice.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "attempt_items", schema = "learning")
public class AttemptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "lexeme_id")
    private Long lexemeId;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "order_index", nullable = false, updatable = false)
    private int orderIndex;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "answered_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant answeredAt;

    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    @Column(nullable = false, length = 20)
    private String skill;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "JSONB")
    private Map<String, Object> meta = Map.of();

    protected AttemptItem() {
    }

    public AttemptItem(Long attemptId, int orderIndex, String itemType, String skill) {
        this.attemptId = attemptId;
        this.orderIndex = orderIndex;
        this.itemType = itemType;
        this.skill = skill;
    }

    public Long getId() {
        return id;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getLexemeId() {
        return lexemeId;
    }

    public void setLexemeId(Long lexemeId) {
        this.lexemeId = lexemeId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public String getItemType() {
        return itemType;
    }

    public String getSkill() {
        return skill;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

}
