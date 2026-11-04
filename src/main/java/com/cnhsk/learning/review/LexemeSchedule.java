package com.cnhsk.learning.review;

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

/** Maps FSRS columns; repositories restrict reads to LEXEME rows. */
@Entity
@Table(name = "user_progress", schema = "learning")
@IdClass(LexemeSchedule.Key.class)
public class LexemeSchedule {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Id
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private String targetType = "LEXEME";

    @Id
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal mastery = BigDecimal.ZERO;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal stability = new BigDecimal("0.1");

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal difficulty = new BigDecimal("5");

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "next_review_at", columnDefinition = "TIMESTAMPTZ")
    private Instant nextReviewAt;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "lapse_count", nullable = false)
    private Integer lapseCount = 0;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant lastSeenAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected LexemeSchedule() {
    }

    public LexemeSchedule(Long userId, Long targetId, Instant lastSeenAt) {
        this.userId = userId;
        this.targetId = targetId;
        this.lastSeenAt = lastSeenAt;
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

    public BigDecimal getStability() {
        return stability;
    }

    public void setStability(BigDecimal stability) {
        this.stability = stability;
    }

    public BigDecimal getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(BigDecimal difficulty) {
        this.difficulty = difficulty;
    }

    public Instant getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(Instant nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Integer getLapseCount() {
        return lapseCount;
    }

    public void setLapseCount(Integer lapseCount) {
        this.lapseCount = lapseCount;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
