package com.cnhsk.auth.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "hsk_level")
    private Short hskLevel;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "email_verified_at", columnDefinition = "TIMESTAMPTZ")
    private Instant emailVerifiedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "suspended_at", columnDefinition = "TIMESTAMPTZ")
    private Instant suspendedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "suspended_until", columnDefinition = "TIMESTAMPTZ")
    private Instant suspendedUntil;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "banned_at", columnDefinition = "TIMESTAMPTZ")
    private Instant bannedAt;

    @Column(name = "banned_by")
    private Long bannedBy;

    @Column(name = "ban_reason")
    private String banReason;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "reminder_channels", nullable = false, length = 50)
    private String reminderChannels = "WEB";

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode = "FREE";

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "plan_expires_at", columnDefinition = "TIMESTAMPTZ")
    private Instant planExpiresAt;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @UpdateTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Short getHskLevel() {
        return hskLevel;
    }

    public void setHskLevel(Short hskLevel) {
        this.hskLevel = hskLevel;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public Instant getSuspendedUntil() {
        return suspendedUntil;
    }

    public void setSuspendedUntil(Instant suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    public Instant getBannedAt() {
        return bannedAt;
    }

    public void setBannedAt(Instant bannedAt) {
        this.bannedAt = bannedAt;
    }

    public Long getBannedBy() {
        return bannedBy;
    }

    public void setBannedBy(Long bannedBy) {
        this.bannedBy = bannedBy;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public LocalTime getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(LocalTime reminderTime) {
        this.reminderTime = reminderTime;
    }

    public String getReminderChannels() {
        return reminderChannels;
    }

    public void setReminderChannels(String reminderChannels) {
        this.reminderChannels = reminderChannels;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public Instant getPlanExpiresAt() {
        return planExpiresAt;
    }

    public void setPlanExpiresAt(Instant planExpiresAt) {
        this.planExpiresAt = planExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UserStatus status() {
        if (bannedAt != null) {
            return UserStatus.BANNED;
        }
        return emailVerifiedAt == null ? UserStatus.UNVERIFIED : UserStatus.ACTIVE;
    }
}
