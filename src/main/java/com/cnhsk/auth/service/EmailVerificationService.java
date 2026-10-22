package com.cnhsk.auth.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.shared.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns email verification token state and resend policy for the auth module. */
@Service
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration RESEND_WINDOW = Duration.ofHours(1);
    private static final long MAX_RESENDS_PER_WINDOW = 3;
    private static final Duration VERIFY_TOKEN_TTL = Duration.ofHours(24);
    private static final String RESEND_KEY_PREFIX = "auth:verify-email:resend:";
    private static final String INVALID_TOKEN_MESSAGE = "Token xác thực không hợp lệ hoặc đã hết hạn.";
    private static final String REDIS_FAILURE_MESSAGE = "Không thể xử lý yêu cầu lúc này.";

    private final JdbcTemplate jdbcTemplate;
    private final RateLimiter rateLimiter;
    private final StringRedisTemplate redisTemplate;
    private final TokenHasher tokenHasher = new TokenHasher();
    private final SecureTokenGenerator tokenGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public EmailVerificationService(JdbcTemplate jdbcTemplate, RateLimiter rateLimiter,
            StringRedisTemplate redisTemplate, SecureTokenGenerator tokenGenerator,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null.");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null.");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null.");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null.");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Transactional
    public boolean verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        String hash = tokenHasher.hash(rawToken);
        Long userId = jdbcTemplate.query(
                "SELECT user_id FROM auth.auth_tokens WHERE token_type = 'EMAIL_VERIFY' AND token_hash = ?",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                hash);
        if (userId == null) {
            throw invalidToken();
        }

        Boolean verified = jdbcTemplate.query(
                "SELECT email_verified_at IS NOT NULL FROM auth.users WHERE id = ? FOR UPDATE",
                resultSet -> resultSet.next() ? resultSet.getBoolean(1) : null,
                userId);
        if (verified == null) {
            throw invalidToken();
        }

        TokenState token = jdbcTemplate.query(
                "SELECT expires_at, revoked_at FROM auth.auth_tokens "
                        + "WHERE user_id = ? AND token_type = 'EMAIL_VERIFY' AND token_hash = ? FOR UPDATE",
                resultSet -> resultSet.next()
                        ? new TokenState(resultSet.getTimestamp("expires_at").toInstant(),
                                resultSet.getTimestamp("revoked_at") == null
                                        ? null : resultSet.getTimestamp("revoked_at").toInstant())
                        : null,
                userId, hash);
        if (token == null) {
            throw invalidToken();
        }
        if (token.revokedAt() != null) {
            if (verified) {
                return true;
            }
            throw invalidToken();
        }
        if (!token.expiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }

        jdbcTemplate.update(
                "UPDATE auth.users SET email_verified_at = COALESCE(email_verified_at, now()) WHERE id = ?",
                userId);
        jdbcTemplate.update(
                "UPDATE auth.auth_tokens SET revoked_at = COALESCE(revoked_at, now()) "
                        + "WHERE user_id = ? AND token_type = 'EMAIL_VERIFY' AND token_hash = ?",
                userId, hash);
        return true;
    }

    @Transactional
    public void resend(long userId) {
        ResendAccount account = jdbcTemplate.query(
                "SELECT email, email_verified_at IS NOT NULL AS verified "
                        + "FROM auth.users WHERE id = ? FOR UPDATE",
                resultSet -> resultSet.next()
                        ? new ResendAccount(resultSet.getString("email"), resultSet.getBoolean("verified"))
                        : null,
                userId);
        if (account == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                    "Phiên đăng nhập không hợp lệ.", Map.of());
        }
        if (account.verified()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_VERIFIED, HttpStatus.CONFLICT,
                    "Email đã được xác thực.", Map.of());
        }

        enforceResendLimit(userId);

        Instant now = clock.instant();
        jdbcTemplate.update(
                "UPDATE auth.auth_tokens SET revoked_at = COALESCE(revoked_at, now()) "
                        + "WHERE user_id = ? AND token_type = 'EMAIL_VERIFY' AND revoked_at IS NULL",
                userId);
        String rawToken = tokenGenerator.generate();
        jdbcTemplate.update(
                "INSERT INTO auth.auth_tokens (user_id, token_type, token_hash, expires_at) "
                        + "VALUES (?, 'EMAIL_VERIFY', ?, ?)",
                userId, tokenHasher.hash(rawToken), Timestamp.from(now.plus(VERIFY_TOKEN_TTL)));
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(account.email(), rawToken));
    }

    private void enforceResendLimit(long userId) {
        String key = RESEND_KEY_PREFIX + userId;
        final long count;
        try {
            count = rateLimiter.increment(key, RESEND_WINDOW);
        } catch (RuntimeException exception) {
            throw redisUnavailable(exception);
        }
        if (count <= MAX_RESENDS_PER_WINDOW) {
            return;
        }

        final Long retryAfterSeconds;
        try {
            retryAfterSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            throw redisUnavailable(exception);
        }
        if (retryAfterSeconds == null || retryAfterSeconds <= 0) {
            throw redisUnavailable(new IllegalStateException("Rate limit expiry is unavailable."));
        }
        throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
                "Đã vượt quá số lần gửi lại cho phép.", Map.of("retryAfterSeconds", retryAfterSeconds));
    }

    private ApiException redisUnavailable(RuntimeException exception) {
        LOGGER.warn("Email verification resend rate limit unavailable; failing closed, cause_type={}",
                exception.getClass().getSimpleName());
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                REDIS_FAILURE_MESSAGE, Map.of());
    }

    private static ApiException invalidToken() {
        return new ApiException(ErrorCode.INVALID_VERIFY_TOKEN, HttpStatus.BAD_REQUEST,
                INVALID_TOKEN_MESSAGE, Map.of());
    }

    private record TokenState(Instant expiresAt, Instant revokedAt) {
    }

    private record ResendAccount(String email, boolean verified) {
    }
}
