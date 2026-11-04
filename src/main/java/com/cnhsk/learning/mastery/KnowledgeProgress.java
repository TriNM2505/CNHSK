package com.cnhsk.learning.mastery;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Maps MP-1.0 columns; repositories restrict reads to KNOWLEDGE_POINT rows. */
@Entity
@Table(name = "user_progress", schema = "learning")
@IdClass(KnowledgeProgress.Key.class)
public class KnowledgeProgress {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Id
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private String targetType = "KNOWLEDGE_POINT";

    @Id
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal mastery = BigDecimal.ZERO;

    @Column(name = "mp_state", length = 12)
    private String mpState = "notyet";

    @Column(precision = 6, scale = 3)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "evidence_item_ids", nullable = false, columnDefinition = "BIGINT[]")
    private Long[] evidenceItemIds = new Long[0];

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "next_review_at", columnDefinition = "TIMESTAMPTZ")
    private Instant nextReviewAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected KnowledgeProgress() {
    }

    public KnowledgeProgress(Long userId, Long targetId) {
        this.userId = userId;
        this.targetId = targetId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public BigDecimal getMastery() {
        return mastery;
    }

    public void setMastery(BigDecimal mastery) {
        this.mastery = mastery;
    }

    public String getMpState() {
        return mpState;
    }

    public void setMpState(String mpState) {
        this.mpState = mpState;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Long[] getEvidenceItemIds() {
        return evidenceItemIds;
    }

    public void setEvidenceItemIds(Long[] evidenceItemIds) {
        this.evidenceItemIds = evidenceItemIds;
    }

    public Instant getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(Instant nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long userId;
        private String targetType;
        private Long targetId;

        public Key() {
        }

        public Key(Long userId, String targetType, Long targetId) {
            this.userId = userId;
            this.targetType = targetType;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(targetType, key.targetType)
                    && Objects.equals(targetId, key.targetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, targetType, targetId);
        }
    }
}
