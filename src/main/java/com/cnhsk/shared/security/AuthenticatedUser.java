package com.cnhsk.shared.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Minimal authenticated identity derived exclusively from verified JWT claims. */
public record AuthenticatedUser(
        long userId,
        Set<String> roles,
        boolean emailVerified,
        String tokenId,
        UUID sessionId,
        Instant issuedAt) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }
}
