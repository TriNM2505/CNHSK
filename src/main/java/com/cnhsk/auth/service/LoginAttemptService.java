package com.cnhsk.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Applies the login source lockout policy using Redis without blocking login on Redis outages. */
@Service
public final class LoginAttemptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration REGISTRY_TTL = Duration.ofMinutes(30);
    private static final long FAILURE_THRESHOLD = 5;
    private static final int GENERATION_RETRIES = 5;
    private static final String LOGIN_KEY_PREFIX = "auth:login:{";

    private final RateLimiter rateLimiter;
    private final TokenHasher tokenHasher = new TokenHasher();

    public LoginAttemptService(RateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null.");
    }

    /** Throws 423 for a live block; a Redis outage is logged and treated as not blocked. */
    public void ensureNotBlocked(String email, String ipAddress) {
        String emailHash = normalizedEmailHash(email);
        String ipHash = tokenHasher.hash(requireValue(ipAddress, "IP address"));
        String prefix = LOGIN_KEY_PREFIX + emailHash + "}:";
        try {
            for (int attempt = 0; attempt < GENERATION_RETRIES; attempt++) {
                long generation = rateLimiter.loginGeneration(prefix + "generation");
                LoginAttemptKeys keys = keysFor(prefix, generation, ipHash);
                long expiryTimeMillis = rateLimiter.loginBlockExpiryTimeMillis(
                        prefix + "generation", generation, keys.blockKey());
                if (expiryTimeMillis == -3) {
                    continue;
                }
                if (expiryTimeMillis == -2) {
                    return;
                }
                if (expiryTimeMillis < 0) {
                    throw new IllegalStateException("Login block key has no expiry.");
                }
                throw new ApiException(ErrorCode.LOGIN_SOURCE_BLOCKED, HttpStatus.LOCKED,
                        "Nguồn đăng nhập tạm thời bị khóa.",
                        Map.of("blockedUntil", Instant.ofEpochMilli(expiryTimeMillis)));
            }
        } catch (DataAccessException exception) {
            warnRedisUnavailable(exception);
            return;
        }
        throw new IllegalStateException("Login-attempt generation changed too frequently.");
    }

    /** Records a failed credential attempt; the fifth failure atomically creates a 15-minute block. */
    public void recordFailure(String email, String ipAddress) {
        String emailHash = normalizedEmailHash(email);
        String ipHash = tokenHasher.hash(requireValue(ipAddress, "IP address"));
        String prefix = LOGIN_KEY_PREFIX + emailHash + "}:";
        try {
            for (int attempt = 0; attempt < GENERATION_RETRIES; attempt++) {
                long generation = rateLimiter.loginGeneration(prefix + "generation");
                LoginAttemptKeys keys = keysFor(prefix, generation, ipHash);
                long count = rateLimiter.recordLoginFailure(prefix + "generation", generation,
                        keys.failureKey(), keys.blockKey(), prefix + "registry", FAILURE_THRESHOLD,
                        FAILURE_WINDOW, BLOCK_DURATION, REGISTRY_TTL);
                if (count >= 0) {
                    return;
                }
            }
        } catch (DataAccessException exception) {
            warnRedisUnavailable(exception);
            return;
        }
        throw new IllegalStateException("Login-attempt generation changed too frequently.");
    }

    /** Clears both keys after successful authentication; Redis outages do not interrupt login. */
    public void clearAfterSuccess(String email, String ipAddress) {
        String emailHash = normalizedEmailHash(email);
        String ipHash = tokenHasher.hash(requireValue(ipAddress, "IP address"));
        String prefix = LOGIN_KEY_PREFIX + emailHash + "}:";
        try {
            for (int attempt = 0; attempt < GENERATION_RETRIES; attempt++) {
                long generation = rateLimiter.loginGeneration(prefix + "generation");
                LoginAttemptKeys keys = keysFor(prefix, generation, ipHash);
                if (rateLimiter.clearLoginPair(prefix + "generation", generation,
                        keys.failureKey(), keys.blockKey(), prefix + "registry")) {
                    return;
                }
            }
        } catch (DataAccessException exception) {
            warnRedisUnavailable(exception);
            return;
        }
        throw new IllegalStateException("Login-attempt generation changed too frequently.");
    }

    /** Strict reset cleanup: unlike login fail-open operations, a Redis failure is propagated. */
    public void clearAllAfterPasswordReset(String email) {
        rateLimiter.clearLoginAttemptsForEmail(normalizedEmailHash(email));
    }

    private String normalizedEmailHash(String email) {
        return tokenHasher.hash(requireValue(email, "Email").strip().toLowerCase(Locale.ROOT));
    }

    private String requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return value;
    }

    private LoginAttemptKeys keysFor(String prefix, long generation, String ipHash) {
        String generationPrefix = prefix + "g" + generation + ":";
        return new LoginAttemptKeys(generationPrefix + "fail:" + ipHash,
                generationPrefix + "block:" + ipHash);
    }

    private void warnRedisUnavailable(DataAccessException exception) {
        LOGGER.warn("Login attempt rate limit unavailable; continuing fail-open, cause_type={}",
                exception.getClass().getSimpleName());
    }

    private record LoginAttemptKeys(String failureKey, String blockKey) {
    }
}
