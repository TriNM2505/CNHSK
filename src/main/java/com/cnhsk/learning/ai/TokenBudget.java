package com.cnhsk.learning.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class TokenBudget {
    private static final Logger log = LoggerFactory.getLogger(TokenBudget.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private static final DefaultRedisScript<Long> ADD_USAGE = new DefaultRedisScript<>(
            "local count = redis.call('INCRBY', KEYS[1], ARGV[1]) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[2]) return count", Long.class);

    private final StringRedisTemplate redis;
    private final SettingsApi settings;
    private final Clock clock;

    public TokenBudget(StringRedisTemplate redis, SettingsApi settings, Clock clock) {
        this.redis = redis;
        this.settings = settings;
        this.clock = clock;
    }

    public ClaudeClient.Completion execute(Supplier<ClaudeClient.Completion> operation) {
        YearMonth month = YearMonth.from(clock.instant().atZone(ZONE));
        String key = "ai:tokens:" + month.format(MONTH);
        Instant expiry = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).plusDays(1).toInstant();
        check(key);
        ClaudeClient.Completion result = operation.get();
        long total;
        try {
            total = Math.addExact(result.inputTokens(), result.outputTokens());
        } catch (ArithmeticException exception) {
            throw new AiUnavailableException(AiUnavailableException.Reason.UPSTREAM_ERROR);
        }
        add(key, total, expiry);
        return result;
    }

    private void check(String key) {
        int budget = settings.getInt(SettingKey.AI_MONTHLY_TOKEN_BUDGET);
        try {
            String value = redis.opsForValue().get(key);
            long used = value == null ? 0 : Long.parseLong(value);
            if (used >= budget) {
                throw new AiUnavailableException(AiUnavailableException.Reason.BUDGET_EXCEEDED);
            }
        } catch (DataAccessException | NumberFormatException exception) {
            log.warn("AI token budget check unavailable");
        }
    }

    private void add(String key, long tokens, Instant expiry) {
        try {
            long seconds = Math.max(1, Duration.between(clock.instant(), expiry).getSeconds() + 1);
            redis.execute(ADD_USAGE, List.of(key), Long.toString(tokens), Long.toString(seconds));
        } catch (DataAccessException exception) {
            log.warn("AI token budget update unavailable");
        }
    }
}
