package com.cnhsk.learning.progress;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Maps only the identity and accuracy columns needed by pronunciation queries. */
@Entity
@Table(name = "user_progress", schema = "learning")
@IdClass(UserProgress.Key.class)
public class UserProgress {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Id
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    protected UserProgress() {
    }

    public UserProgress(Long userId, String targetType, Long targetId, BigDecimal percent) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.percent = percent;
    }

    public Long getTargetId() {
        return targetId;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long userId;
        private String targetType;
        private Long targetId;

        public Key() {
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
