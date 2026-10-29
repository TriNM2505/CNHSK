package com.cnhsk.learning.practice.entity;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
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
@Table(name = "attempts", schema = "learning")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 20)
    private String kind;

    @Column(name = "ref_type", updatable = false, length = 20)
    private String refType;

    @Column(name = "ref_id", updatable = false)
    private Long refId;

    @Column(name = "game_code", length = 30)
    private String gameCode;

    @Column(nullable = false, length = 20)
    private String status = "IN_PROGRESS";

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "served_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant servedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "submitted_at", columnDefinition = "TIMESTAMPTZ")
    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
    private Instant expiresAt;

    private Long score;

    @Column(name = "max_score")
    private Long maxScore;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(nullable = false)
    private boolean suspicious;

    // The server-only snapshot is inserted once and never included in ORM updates.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "served_items", updatable = false, columnDefinition = "JSONB")
    private JsonNode servedItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_answers", columnDefinition = "JSONB")
    private JsonNode draftAnswers;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "draft_saved_at", columnDefinition = "TIMESTAMPTZ")
    private Instant draftSavedAt;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Attempt() {
    }

    public Attempt(Long userId, String kind, String refType, Long refId,
                   Instant servedAt, Instant expiresAt, JsonNode servedItems) {
        this.userId = userId;
        this.kind = kind;
        this.refType = refType;
        this.refId = refId;
        this.servedAt = servedAt;
        this.expiresAt = expiresAt;
        this.servedItems = servedItems;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getServedAt() {
        return servedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getScore() {
        return score;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    public Long getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Long maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public void setSuspicious(boolean suspicious) {
        this.suspicious = suspicious;
    }

    public JsonNode getServedItems() {
        return servedItems;
    }

    public JsonNode getDraftAnswers() {
        return draftAnswers;
    }

    public void setDraftAnswers(JsonNode draftAnswers) {
        this.draftAnswers = draftAnswers;
    }

    public Instant getDraftSavedAt() {
        return draftSavedAt;
    }

    public void setDraftSavedAt(Instant draftSavedAt) {
        this.draftSavedAt = draftSavedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
