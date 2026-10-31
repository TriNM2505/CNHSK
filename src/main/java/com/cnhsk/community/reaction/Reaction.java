package com.cnhsk.community.reaction;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reactions", schema = "community")
public class Reaction {

    @EmbeddedId
    private ReactionId id;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected Reaction() {
    }

    public Reaction(ReactionId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public ReactionId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
