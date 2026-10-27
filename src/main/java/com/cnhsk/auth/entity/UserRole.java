package com.cnhsk.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_roles", schema = "auth")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private RoleCode roleCode;

    @Column(name = "granted_by")
    private Long grantedBy;

    @CreationTimestamp
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "granted_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant grantedAt;

    protected UserRole() {
    }

    public UserRole(Long userId, RoleCode roleCode, Long grantedBy) {
        this.userId = userId;
        this.roleCode = roleCode;
        this.grantedBy = grantedBy;
    }

    public Long getUserId() {
        return userId;
    }

    public RoleCode getRoleCode() {
        return roleCode;
    }

    public Long getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(Long grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
