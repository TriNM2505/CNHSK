package com.cnhsk.auth.api;

import java.util.Map;
import java.util.Set;

/** Public Java API for other modules that need authentication-owned data or checks. */
public interface AuthApi {

    CurrentUser currentUser();

    void requireVerifiedEmail(long userId);

    boolean hasRole(long userId, RoleCode role);

    Map<Long, String> displayNames(Set<Long> userIds);
}
