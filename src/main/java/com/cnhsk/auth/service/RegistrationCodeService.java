package com.cnhsk.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.cnhsk.auth.dto.RegistrationCodeResponse;
import com.cnhsk.auth.event.RegistrationCodeRequestedEvent;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Proves that whoever registers can read the mailbox, before an account exists. Redis holds only hashes, and
 * a code is gone once it is used, guessed wrong too often, replaced or expired.
 */
@Service
public class RegistrationCodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationCodeService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final long MAX_SENDS_PER_WINDOW = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern CODE = Pattern.compile("[0-9]{6}");
    private static final String KEY_PREFIX = "auth:register-code:";
    private static final TokenHasher HASHER = new TokenHasher();

    private static final DefaultRedisScript<Long> STORE = new DefaultRedisScript<>(
            "redis.call('DEL', KEYS[1]) "
                    + "redis.call('HSET', KEYS[1], 'code', ARGV[1], 'wrong', 0) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[2]) "
                    + "return 1",
            Long.class);

    /** Returns -1 when the code is accepted, otherwise the attempts left. */
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "local stored = redis.call('HGET', KEYS[1], 'code') "
                    + "if not stored then return 0 end "
                    + "if stored == ARGV[1] then redis.call('DEL', KEYS[1]) return -1 end "
                    + "local left = tonumber(ARGV[2]) - redis.call('HINCRBY', KEYS[1], 'wrong', 1) "
                    + "if left <= 0 then redis.call('DEL', KEYS[1]) return 0 end "
                    + "return left",
            Long.class);

    /** Gives back one send, leaving the window of the remaining ones as it was. */
    private static final DefaultRedisScript<Long> REFUND_SEND = new DefaultRedisScript<>(
            "redis.call('DEL', KEYS[1]) "
                    + "if redis.call('EXISTS', KEYS[2]) == 1 then return redis.call('DECR', KEYS[2]) end "
                    + "return 0",
            Long.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public RegistrationCodeService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
            RateLimiter rateLimiter, ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    /** Emails a new code, which replaces any earlier one for the same address once it has been sent. */
    public RegistrationCodeResponse send(String email) {
        Boolean taken = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM auth.users WHERE email = ?)", Boolean.class, email);
        if (Boolean.TRUE.equals(taken)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT,
                    "Email đã được sử dụng.", Map.of());
        }

        String emailHash = HASHER.hash(email);
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        try {
            enforceSendLimits(emailHash);
            eventPublisher.publishEvent(new RegistrationCodeRequestedEvent(email, code));
            redisTemplate.execute(STORE, List.of(KEY_PREFIX + emailHash), hash(email, code),
                    Long.toString(CODE_TTL.toSeconds()));
        } catch (ApiException exception) {
            throw exception;
        } catch (MailException exception) {
            refundSend(emailHash);
            throw new ApiException(ErrorCode.EMAIL_NOT_SENT, HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa gửi được mã tới email này. Vui lòng thử lại.", Map.of());
        } catch (RuntimeException exception) {
            throw redisUnavailable(exception);
        }
        return new RegistrationCodeResponse(email, CODE_TTL.toSeconds(), RESEND_DELAY.toSeconds());
    }

    /** Accepts a code once; a wrong one uses up an attempt. */
    public void consume(String email, String code) {
        long result;
        try {
            Long reply = code != null && CODE.matcher(code).matches()
                    ? redisTemplate.execute(CONSUME, List.of(KEY_PREFIX + HASHER.hash(email)),
                            hash(email, code), Integer.toString(MAX_ATTEMPTS))
                    : remainingAttempts(email);
            result = reply == null ? 0 : reply;
        } catch (RuntimeException exception) {
            throw redisUnavailable(exception);
        }
        if (result != -1) {
            throw new ApiException(ErrorCode.INVALID_VERIFY_TOKEN, HttpStatus.BAD_REQUEST,
                    result > 0 ? "Mã xác minh chưa đúng." : "Mã xác minh đã hết hiệu lực. Vui lòng gửi mã mới.",
                    Map.of("remainingAttempts", result));
        }
    }

    private Long remainingAttempts(String email) {
        Object wrong = redisTemplate.opsForHash().get(KEY_PREFIX + HASHER.hash(email), "wrong");
        return wrong == null ? 0L : MAX_ATTEMPTS - Long.parseLong(wrong.toString());
    }

    private void enforceSendLimits(String emailHash) {
        String delayKey = delayKey(emailHash);
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(delayKey, "1", RESEND_DELAY))) {
            throw tooManyRequests(delayKey);
        }
        String countKey = countKey(emailHash);
        if (rateLimiter.increment(countKey, SEND_WINDOW) > MAX_SENDS_PER_WINDOW) {
            throw tooManyRequests(countKey);
        }
    }

    private void refundSend(String emailHash) {
        try {
            redisTemplate.execute(REFUND_SEND, List.of(delayKey(emailHash), countKey(emailHash)));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not give back a registration code send, cause_type={}",
                    exception.getClass().getSimpleName());
        }
    }

    private static String delayKey(String emailHash) {
        return KEY_PREFIX + "delay:" + emailHash;
    }

    private static String countKey(String emailHash) {
        return KEY_PREFIX + "count:" + emailHash;
    }

    private ApiException tooManyRequests(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
                "Vui lòng chờ trước khi gửi lại mã.",
                Map.of("retryAfterSeconds", seconds == null ? 1L : Math.max(1L, seconds)));
    }

    /** Bound to the address, so a code never works for another one. */
    private static String hash(String email, String code) {
        return HASHER.hash(email + '\n' + code);
    }

    private static ApiException redisUnavailable(RuntimeException exception) {
        LOGGER.warn("Registration code store unavailable; failing closed, cause_type={}",
                exception.getClass().getSimpleName());
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể xử lý yêu cầu lúc này.", Map.of());
    }
}
