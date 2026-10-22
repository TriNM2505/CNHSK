package com.cnhsk.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_tokens", schema = "auth")
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 20)
    private TokenType tokenType;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "revoked_at", columnDefinition = "TIMESTAMPTZ")
    private Instant revokedAt;

    @Column(name = "session_id")
    private UUID sessionId;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    protected AuthToken() {
    }

    public AuthToken(Long userId, TokenType tokenType, String tokenHash, Instant expiresAt) {
        this(userId, tokenType, tokenHash, expiresAt, null);
    }

    public AuthToken(Long userId, TokenType tokenType, String tokenHash, Instant expiresAt, UUID sessionId) {
        if (tokenType == TokenType.REFRESH && sessionId == null) {
            throw new IllegalArgumentException("REFRESH tokens require a sessionId.");
        }
        this.userId = userId;
        this.tokenType = tokenType;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.sessionId = sessionId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
