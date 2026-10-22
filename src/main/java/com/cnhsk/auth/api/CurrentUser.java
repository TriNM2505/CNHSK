package com.cnhsk.auth.api;

import java.util.Set;

/** Authenticated identity exposed to other modules without exposing auth entities. */
public record CurrentUser(long userId, Set<RoleCode> roles, boolean emailVerified) {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }
}
