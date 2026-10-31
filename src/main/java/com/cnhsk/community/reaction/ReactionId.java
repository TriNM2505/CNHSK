package com.cnhsk.community.reaction;

import java.io.Serializable;
import java.util.Objects;

import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class ReactionId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 10)
    private ReactionKind kind;

    protected ReactionId() {
    }

    public ReactionId(Long userId, TargetType targetType, Long targetId, ReactionKind kind) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.kind = kind;
    }

    public Long getUserId() {
        return userId;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReactionKind getKind() {
        return kind;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReactionId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && targetType == that.targetType
                && Objects.equals(targetId, that.targetId)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, targetType, targetId, kind);
    }
}
