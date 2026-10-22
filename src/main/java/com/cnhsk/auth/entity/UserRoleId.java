package com.cnhsk.auth.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private RoleCode roleCode;

    public UserRoleId() {
    }

    public UserRoleId(Long userId, RoleCode roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public Long getUserId() {
        return userId;
    }

    public RoleCode getRoleCode() {
        return roleCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && roleCode == that.roleCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleCode);
    }
}
