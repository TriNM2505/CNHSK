package com.cnhsk.learning.aigen;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Advisory evidence of a reviewer opening a pending question; never a source of permission. */
@Component
public class AiReviewViewedStore {
    private static final Logger LOG = LoggerFactory.getLogger(AiReviewViewedStore.class);
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    public AiReviewViewedStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void markViewed(long userId, long questionId) {
        try {
            redis.opsForValue().set(key(userId, questionId), "1", TTL);
        } catch (RuntimeException exception) {
            LOG.warn("Review viewed marker unavailable");
        }
    }

    public boolean hasViewed(long userId, long questionId) {
        return viewed(userId, List.of(questionId)).contains(questionId);
    }

    public Set<Long> viewed(long userId, List<Long> questionIds) {
        if (questionIds.isEmpty()) return Set.of();
        try {
            List<String> values = redis.opsForValue().multiGet(questionIds.stream()
                    .map(id -> key(userId, id)).toList());
            if (values == null) return Set.of();
            Set<Long> result = new HashSet<>();
            for (int index = 0; index < Math.min(values.size(), questionIds.size()); index++) {
                if ("1".equals(values.get(index))) result.add(questionIds.get(index));
            }
            return Set.copyOf(result);
        } catch (RuntimeException exception) {
            LOG.warn("Review viewed markers unavailable");
            return Set.of();
        }
    }

    private static String key(long userId, long questionId) {
        return "ai-review:viewed:" + userId + ":" + questionId;
    }
}
