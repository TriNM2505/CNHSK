package com.cnhsk.auth.api;

/** Stable role vocabulary exposed to other modules without exposing auth entities. */
public enum RoleCode {
    LEARNER,
    TEACHER,
    MODERATOR,
    CONTENT_EDITOR,
    ACCOUNTANT,
    ADMIN
}
