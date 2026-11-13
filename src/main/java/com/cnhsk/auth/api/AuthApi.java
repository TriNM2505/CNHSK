package com.cnhsk.auth.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Public Java API for other modules that need authentication-owned data or checks. */
public interface AuthApi {

    CurrentUser currentUser();

    void requireVerifiedEmail(long userId);

    boolean hasRole(long userId, RoleCode role);

    Map<Long, String> displayNames(Set<Long> userIds);

    Set<Long> bannedUserIds();

    Map<Long, PublicProfile> publicProfiles(Set<Long> userIds);

    /** Reads the user's plan snapshot without locking or changing it. */
    Optional<PlanState> readPlan(long userId);

    /** Locks the user's plan row until the caller's transaction completes. */
    PlanState lockPlan(long userId);

    /** Caller must hold lockPlan(userId) in the current transaction. */
    void updatePlan(long userId, String planCode, Instant expiresAt);

    /** Caller must hold lockPlan(userId) in the current transaction. */
    void updateUsage(long userId, Map<String, Integer> usage, Instant resetAt);

    int downgradeExpiredPlans(Instant now);

    Map<Long, String> maskedEmails(Set<Long> userIds);

    long countActivePlan(String planCode, Instant now);
}
