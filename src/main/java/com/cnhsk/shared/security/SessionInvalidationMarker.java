package com.cnhsk.shared.security;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Redis payload used to invalidate access tokens without querying the database. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionInvalidationMarker(long invalidBeforeMs, Reason reason, String banReason) {

    public SessionInvalidationMarker {
        if (invalidBeforeMs < 0 || reason == null) {
            throw new IllegalArgumentException("Invalid session invalidation marker.");
        }
        if (reason == Reason.BANNED && (banReason == null || banReason.isBlank())) {
            throw new IllegalArgumentException("A banned marker requires a reason.");
        }
        if (reason == Reason.REVOKED && banReason != null) {
            throw new IllegalArgumentException("A revoked marker must not include a ban reason.");
        }
    }

    public static SessionInvalidationMarker banned(long invalidBeforeMs, String banReason) {
        return new SessionInvalidationMarker(invalidBeforeMs, Reason.BANNED, banReason);
    }

    public static SessionInvalidationMarker revoked(long invalidBeforeMs) {
        return new SessionInvalidationMarker(invalidBeforeMs, Reason.REVOKED, null);
    }

    public enum Reason {
        BANNED,
        REVOKED
    }
}
