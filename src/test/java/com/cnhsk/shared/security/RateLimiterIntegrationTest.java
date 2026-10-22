package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String key;

    @AfterEach
    void cleanCounter() {
        if (key != null) {
            rateLimiter.clear(key);
        }
    }

    @Test
    void atomicallyIncrementsAndSetsExpiryOnlyOnFirstHit() throws Exception {
        key = "test:rate-limit:" + UUID.randomUUID();

        assertEquals(1, rateLimiter.increment(key, Duration.ofSeconds(30)));
        Long initialTtl = redisTemplate.getExpire(key);
        assertTrue(initialTtl != null && initialTtl > 0 && initialTtl <= 30);

        Thread.sleep(1_100);
        assertEquals(2, rateLimiter.increment(key, Duration.ofSeconds(30)));
        Long ttlAfterSecondHit = redisTemplate.getExpire(key);
        assertTrue(ttlAfterSecondHit != null && ttlAfterSecondHit < initialTtl);
    }

    @Test
    void concurrentIncrementsReturnUniqueSequentialCounts() throws Exception {
        key = "test:rate-limit:" + UUID.randomUUID();
        int workerCount = 8;
        int incrementsPerWorker = 25;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<List<Long>>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workerCount; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    List<Long> counts = new ArrayList<>();
                    for (int index = 0; index < incrementsPerWorker; index++) {
                        counts.add(rateLimiter.increment(key, Duration.ofMinutes(1)));
                    }
                    return counts;
                }));
            }

            ready.await();
            start.countDown();
            List<Long> counts = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                counts.addAll(future.get());
            }

            assertEquals(workerCount * incrementsPerWorker, counts.size());
            assertEquals(workerCount * incrementsPerWorker,
                    counts.stream().distinct().count());
            assertEquals(workerCount * incrementsPerWorker + 1,
                    rateLimiter.increment(key, Duration.ofMinutes(1)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void clearsExistingCounter() {
        key = "test:rate-limit:" + UUID.randomUUID();
        rateLimiter.increment(key, Duration.ofMinutes(1));

        rateLimiter.clear(key);

        assertEquals(1, rateLimiter.increment(key, Duration.ofMinutes(1)));
    }
}
