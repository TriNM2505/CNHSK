package com.cnhsk.shared.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Centralizes the Redis key, JSON format, and expiry for invalidation markers. */
@Service
public class SessionInvalidationService {

    public static final Duration MARKER_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:invalid-before:";
    private static final DefaultRedisScript<Long> WRITE_IF_NOT_OLDER = new DefaultRedisScript<>(
            "local incoming = cjson.decode(ARGV[1]) "
                    + "local currentJson = redis.call('GET', KEYS[1]) "
                    + "local current = nil "
                    + "local currentBefore = nil "
                    + "if currentJson then "
                    + "current = cjson.decode(currentJson) "
                    + "currentBefore = tonumber(current.invalidBeforeMs) "
                    + "if not currentBefore and current.invalidBefore then "
                    + "currentBefore = tonumber(current.invalidBefore) * 1000 end "
                    + "local incomingBefore = tonumber(incoming.invalidBeforeMs) "
                    + "if currentBefore and current.reason == 'BANNED' and incoming.reason ~= 'BANNED' "
                    + "and incomingBefore <= currentBefore then return 0 end "
                    + "if currentBefore and incoming.reason ~= 'BANNED' and incomingBefore < currentBefore then return 0 end "
                    + "end "
                    + "local invalidBeforeMs = tonumber(incoming.invalidBeforeMs) "
                    + "if current and currentBefore and currentBefore > invalidBeforeMs then "
                    + "invalidBeforeMs = currentBefore end "
                    + "local merged = incoming "
                    + "if (current and current.reason == 'BANNED') or incoming.reason == 'BANNED' then "
                    + "local banned = incoming "
                    + "if current and current.reason == 'BANNED' then banned = current end "
                    + "merged = {invalidBeforeMs = invalidBeforeMs, reason = 'BANNED', banReason = banned.banReason} "
                    + "else merged = {invalidBeforeMs = invalidBeforeMs, reason = 'REVOKED'} end "
                    + "redis.call('SET', KEYS[1], cjson.encode(merged), 'EX', ARGV[2]) "
                    + "return 1",
            Long.class);
    private static final DefaultRedisScript<Long> MARK_UNBANNED = new DefaultRedisScript<>(
            "local currentJson = redis.call('GET', KEYS[1]) "
                    + "if not currentJson then return 0 end "
                    + "local current = cjson.decode(currentJson) "
                    + "if current.reason ~= 'BANNED' then return 0 end "
                    + "local invalidBeforeMs = tonumber(current.invalidBeforeMs) "
                    + "if not invalidBeforeMs and current.invalidBefore then "
                    + "invalidBeforeMs = tonumber(current.invalidBefore) * 1000 end "
                    + "if not invalidBeforeMs then return redis.error_reply('Invalid invalidation marker cutoff') end "
                    + "local ttl = redis.call('PTTL', KEYS[1]) "
                    + "if ttl == -2 or ttl == 0 then return 0 end "
                    + "local revoked = cjson.encode({invalidBeforeMs = invalidBeforeMs, reason = 'REVOKED'}) "
                    + "if ttl == -1 then redis.call('SET', KEYS[1], revoked) "
                    + "elseif ttl > 0 then redis.call('SET', KEYS[1], revoked, 'PX', ttl) "
                    + "else return 0 end "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SessionInvalidationService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void markBanned(long userId, long invalidBeforeMs, String banReason) {
        write(userId, SessionInvalidationMarker.banned(invalidBeforeMs, banReason));
    }

    public void markRevoked(long userId, long invalidBeforeMs) {
        write(userId, SessionInvalidationMarker.revoked(invalidBeforeMs));
    }

    /** Changes a live ban marker to a revoked marker without extending its cutoff or remaining TTL. */
    public void markUnbanned(long userId) {
        Long updated = redisTemplate.execute(MARK_UNBANNED, List.of(key(userId)));
        if (updated == null) {
            throw new IllegalStateException("Redis did not return the unban marker update result.");
        }
    }

    public Optional<SessionInvalidationMarker> findMarker(long userId) {
        String json = redisTemplate.opsForValue().get(key(userId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionInvalidationMarker.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid session invalidation marker stored in Redis.", exception);
        }
    }

    private void write(long userId, SessionInvalidationMarker marker) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive.");
        }
        try {
            String json = objectMapper.writeValueAsString(marker);
            Long written = redisTemplate.execute(WRITE_IF_NOT_OLDER, List.of(key(userId)),
                    json, Long.toString(MARKER_TTL.toSeconds()));
            if (written == null) {
                throw new IllegalStateException("Redis did not return the marker update result.");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize session invalidation marker.", exception);
        }
    }

    private static String key(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive.");
        }
        return KEY_PREFIX + userId;
    }
}
