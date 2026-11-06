package com.cnhsk.learning.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AssistantDailyLimit {
    private static final Logger log = LoggerFactory.getLogger(AssistantDailyLimit.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            "local n=tonumber(redis.call('GET',KEYS[1]) or '0') " +
            "if n >= tonumber(ARGV[1]) then return 0 end " +
            "n=redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],ARGV[2]); return n", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "local n=tonumber(redis.call('GET',KEYS[1]) or '0') " +
            "if n > 1 then return redis.call('DECR',KEYS[1]) end if n == 1 then return redis.call('DEL',KEYS[1]) end return 0", Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int limit;

    public AssistantDailyLimit(StringRedisTemplate redis, Clock clock,
            @Value("${cnhsk.assistant.daily-limit:200}") int limit) {
        this.redis = redis;
        this.clock = clock;
        this.limit = limit;
    }

    public Reservation reserve(long userId) {
        Instant now = clock.instant();
        LocalDate date = now.atZone(ZONE).toLocalDate();
        Instant midnight = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        Duration untilMidnight = Duration.between(now, midnight);
        long retrySeconds = untilMidnight.getSeconds() + (untilMidnight.getNano() == 0 ? 0 : 1);
        String key = "assistant:ask:" + userId + ":" + date.format(DAY);
        try {
            Long result = redis.execute(RESERVE, List.of(key), Integer.toString(limit), Long.toString(retrySeconds));
            if (result == null) {
                log.warn("Assistant daily limit unavailable userId={} errorCode=REDIS_EMPTY_RESULT", userId);
                return Reservation.bypass(this, key);
            }
            if (result == 0) {
                throw new RateLimitExceededException(retrySeconds);
            }
            return Reservation.reserved(this, key);
        } catch (DataAccessException exception) {
            log.warn("Assistant daily limit unavailable userId={} errorCode=REDIS_UNAVAILABLE", userId);
            return Reservation.bypass(this, key);
        }
    }

    private void release(String key) {
        try {
            redis.execute(RELEASE, List.of(key));
        } catch (DataAccessException exception) {
            log.warn("Assistant daily limit release unavailable errorCode=REDIS_UNAVAILABLE");
        }
    }

    public static final class Reservation implements AutoCloseable {
        private final AssistantDailyLimit owner;
        private final String key;
        private final boolean reserved;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Reservation(AssistantDailyLimit owner, String key, boolean reserved) {
            this.owner = owner;
            this.key = key;
            this.reserved = reserved;
        }

        private static Reservation reserved(AssistantDailyLimit owner, String key) {
            return new Reservation(owner, key, true);
        }

        private static Reservation bypass(AssistantDailyLimit owner, String key) {
            return new Reservation(owner, key, false);
        }

        public void commit() {
            finished.set(true);
        }

        @Override
        public void close() {
            if (reserved && finished.compareAndSet(false, true)) {
                owner.release(key);
            }
        }
    }

    public static final class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        private RateLimitExceededException(long retryAfterSeconds) {
            super("Daily assistant question limit reached");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
