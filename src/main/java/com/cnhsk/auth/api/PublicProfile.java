package com.cnhsk.auth.api;

/** Public, non-sensitive profile fields needed by leaderboard entries. */
public record PublicProfile(String displayName, String avatarUrl) {
}
