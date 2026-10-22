package com.cnhsk.shared.security;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Provides fixed-window Redis counters with an atomic increment and first-hit expiry. */
@Component
public final class RateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_FIRST_HIT_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private static final DefaultRedisScript<Long> INCREMENT_AND_BLOCK_AT_THRESHOLD = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "if count >= tonumber(ARGV[2]) then "
                    + "redis.call('SET', KEYS[2], '1', 'NX', 'EX', ARGV[3]) end "
                    + "return count",
            Long.class);

    private static final DefaultRedisScript<Long> EXPIRY_TIME_MILLIS = new DefaultRedisScript<>(
            "return redis.call('PEXPIRETIME', KEYS[1])",
            Long.class);

    private static final DefaultRedisScript<Long> LOGIN_FAILURE_WITH_REGISTRY = new DefaultRedisScript<>(
            "local generation = redis.call('GET', KEYS[1]) or '0' "
                    + "if generation ~= ARGV[1] then return -1 end "
                    + "local count = redis.call('INCR', KEYS[2]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end "
                    + "redis.call('SADD', KEYS[4], KEYS[2]) "
                    + "if count >= tonumber(ARGV[3]) then "
                    + "redis.call('SET', KEYS[3], '1', 'NX', 'EX', ARGV[4]) "
                    + "redis.call('SADD', KEYS[4], KEYS[3]) end "
                    + "redis.call('EXPIRE', KEYS[4], ARGV[5]) "
                    + "return count",
            Long.class);

    private static final DefaultRedisScript<Long> LOGIN_BLOCK_EXPIRY = new DefaultRedisScript<>(
            "local generation = redis.call('GET', KEYS[1]) or '0' "
                    + "if generation ~= ARGV[1] then return -3 end "
                    + "return redis.call('PEXPIRETIME', KEYS[2])",
            Long.class);

    private static final DefaultRedisScript<Long> CLEAR_LOGIN_PAIR = new DefaultRedisScript<>(
            "local generation = redis.call('GET', KEYS[1]) or '0' "
                    + "if generation ~= ARGV[1] then return 0 end "
                    + "redis.call('DEL', KEYS[2], KEYS[3]) "
                    + "redis.call('SREM', KEYS[4], KEYS[2], KEYS[3]) "
                    + "return 1",
            Long.class);

    private static final DefaultRedisScript<Long> ADVANCE_LOGIN_GENERATION = new DefaultRedisScript<>(
            "local generation = tonumber(redis.call('GET', KEYS[1]) or '0') + 1 "
                    + "redis.call('SET', KEYS[1], tostring(generation), 'EX', ARGV[1]) "
                    + "return generation",
            Long.class);

    private static final String LOGIN_REGISTRY_PREFIX = "auth:login:{";
    private static final String LOGIN_FAILURE_LEGACY_PREFIX = "auth:login-fail:";
    private static final String LOGIN_BLOCK_LEGACY_PREFIX = "auth:login-block:";

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null.");
    }

    /** Atomically increments a counter and starts its TTL on the first increment only. */
    public long increment(String key, Duration window) {
        validateKey(key);
        long windowSeconds = requireWindowSeconds(window);
        Long count = redisTemplate.execute(INCREMENT_WITH_FIRST_HIT_TTL, List.of(key),
                Long.toString(windowSeconds));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter value.");
        }
        return count;
    }

    /** Atomically increments a counter and creates its block key at the threshold. */
    public long incrementAndBlockAtThreshold(String counterKey, String blockKey, long threshold,
            Duration counterWindow, Duration blockDuration) {
        validateKey(counterKey);
        validateKey(blockKey);
        if (counterKey.equals(blockKey)) {
            throw new IllegalArgumentException("Counter and block keys must be different.");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("Threshold must be positive.");
        }
        long counterWindowSeconds = requireWindowSeconds(counterWindow);
        long blockDurationSeconds = requireWindowSeconds(blockDuration);
        Long count = redisTemplate.execute(INCREMENT_AND_BLOCK_AT_THRESHOLD,
                List.of(counterKey, blockKey), Long.toString(counterWindowSeconds),
                Long.toString(threshold), Long.toString(blockDurationSeconds));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter value.");
        }
        return count;
    }

    /** Returns whether a key exists; null Redis responses are treated as an unexpected failure. */
    public boolean exists(String key) {
        validateKey(key);
        Boolean exists = redisTemplate.hasKey(key);
        if (exists == null) {
            throw new IllegalStateException("Redis did not return a key-existence result.");
        }
        return exists;
    }

    /** Returns Redis' absolute key-expiry time in epoch milliseconds (-2 absent, -1 without expiry). */
    public long expiryTimeMillis(String key) {
        validateKey(key);
        Long expiryTimeMillis = redisTemplate.execute(EXPIRY_TIME_MILLIS, List.of(key));
        if (expiryTimeMillis == null) {
            throw new IllegalStateException("Redis did not return a key-expiry timestamp.");
        }
        return expiryTimeMillis;
    }

    /** Removes a counter, for example after a successful authentication. */
    public void clear(String key) {
        validateKey(key);
        redisTemplate.delete(key);
    }

    /** Reads the current generation; a missing key represents the initial generation zero. */
    public long loginGeneration(String generationKey) {
        validateKey(generationKey);
        String value = redisTemplate.opsForValue().get(generationKey);
        if (value == null) {
            return 0L;
        }
        try {
            long generation = Long.parseLong(value);
            if (generation < 0) {
                throw new NumberFormatException("negative generation");
            }
            return generation;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Login-attempt generation is malformed.", exception);
        }
    }

    /** Atomically increments a login-failure counter and records its keys in the email registry. */
    public long recordLoginFailure(String generationKey, long generation, String counterKey, String blockKey,
            String registryKey, long threshold, Duration counterWindow, Duration blockDuration,
            Duration registryTtl) {
        validateKey(generationKey);
        validateKey(counterKey);
        validateKey(blockKey);
        validateKey(registryKey);
        if (threshold < 1 || counterKey.equals(blockKey)) {
            throw new IllegalArgumentException("Login-attempt threshold or keys are invalid.");
        }
        Long count = redisTemplate.execute(LOGIN_FAILURE_WITH_REGISTRY,
                List.of(generationKey, counterKey, blockKey, registryKey), Long.toString(generation),
                Long.toString(requireWindowSeconds(counterWindow)), Long.toString(threshold),
                Long.toString(requireWindowSeconds(blockDuration)), Long.toString(requireWindowSeconds(registryTtl)));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a login-attempt counter value.");
        }
        return count;
    }

    /** Returns PEXPIRETIME, -2 for absent, -1 for no expiry, or -3 when generation changed. */
    public long loginBlockExpiryTimeMillis(String generationKey, long generation, String blockKey) {
        validateKey(generationKey);
        validateKey(blockKey);
        Long expiry = redisTemplate.execute(LOGIN_BLOCK_EXPIRY, List.of(generationKey, blockKey),
                Long.toString(generation));
        if (expiry == null) {
            throw new IllegalStateException("Redis did not return the login-block expiry.");
        }
        return expiry;
    }

    /** Atomically clears the current email/IP pair; returns false if reset advanced its generation. */
    public boolean clearLoginPair(String generationKey, long generation, String counterKey, String blockKey,
            String registryKey) {
        validateKey(generationKey);
        validateKey(counterKey);
        validateKey(blockKey);
        validateKey(registryKey);
        Long result = redisTemplate.execute(CLEAR_LOGIN_PAIR,
                List.of(generationKey, counterKey, blockKey, registryKey), Long.toString(generation));
        if (result == null) {
            throw new IllegalStateException("Redis did not return the login-attempt cleanup result.");
        }
        return result == 1L;
    }

    /** Advances the email generation, then removes old and legacy keys without touching new-generation keys. */
    public void clearLoginAttemptsForEmail(String emailHash) {
        if (emailHash == null || emailHash.isBlank()) {
            throw new IllegalArgumentException("Email hash must not be blank.");
        }
        String prefix = LOGIN_REGISTRY_PREFIX + emailHash + "}:";
        String generationKey = prefix + "generation";
        String registryKey = prefix + "registry";
        Long generation = redisTemplate.execute(ADVANCE_LOGIN_GENERATION, List.of(generationKey), "86400");
        if (generation == null) {
            throw new IllegalStateException("Redis did not return the new login-attempt generation.");
        }

        removeStaleRegistryMembers(registryKey, prefix, generation);
        deleteLegacyKeys(LOGIN_FAILURE_LEGACY_PREFIX + emailHash + ":*");
        deleteLegacyKeys(LOGIN_BLOCK_LEGACY_PREFIX + emailHash + ":*");
    }

    private void removeStaleRegistryMembers(String registryKey, String emailPrefix, long currentGeneration) {
        // SSCAN is cursor-based and bounded; old-generation members cannot be re-added after the atomic bump.
        ScanOptions options = ScanOptions.scanOptions().count(200).build();
        List<String> staleBatch = new ArrayList<>(200);
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(registryKey, options)) {
            while (cursor.hasNext()) {
                String member = cursor.next();
                if (isStaleLoginKey(member, emailPrefix, currentGeneration)) {
                    staleBatch.add(member);
                    if (staleBatch.size() == 200) {
                        deleteRegistryBatch(registryKey, staleBatch);
                    }
                }
            }
        }
        deleteRegistryBatch(registryKey, staleBatch);
        redisTemplate.expire(registryKey, Duration.ofMinutes(30));
    }

    private void deleteRegistryBatch(String registryKey, List<String> staleBatch) {
        if (staleBatch.isEmpty()) {
            return;
        }
        redisTemplate.delete(List.copyOf(staleBatch));
        redisTemplate.opsForSet().remove(registryKey, staleBatch.toArray());
        staleBatch.clear();
    }

    private boolean isStaleLoginKey(String key, String emailPrefix, long currentGeneration) {
        if (!key.startsWith(emailPrefix)) {
            return false;
        }
        String remainder = key.substring(emailPrefix.length());
        if (!remainder.startsWith("g")) {
            return false;
        }
        int delimiter = remainder.indexOf(':');
        if (delimiter < 0) {
            return false;
        }
        try {
            return Long.parseLong(remainder.substring(1, delimiter)) != currentGeneration;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private void deleteLegacyKeys(String pattern) {
        List<String> batch = new ArrayList<>(200);
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    if (batch.size() == 200) {
                        batch.forEach(redisTemplate::delete);
                        batch.clear();
                    }
                }
            }
            return null;
        });
        batch.forEach(redisTemplate::delete);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate-limit key must not be null or blank.");
        }
    }

    private static long requireWindowSeconds(Duration window) {
        if (window == null || window.isNegative() || window.isZero() || window.getSeconds() < 1) {
            throw new IllegalArgumentException("Rate-limit window must be at least one second.");
        }
        return window.getSeconds();
    }
}
