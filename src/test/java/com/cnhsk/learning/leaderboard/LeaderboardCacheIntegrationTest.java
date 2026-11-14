package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.*;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.leaderboard.LeaderboardRepository.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class LeaderboardCacheIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test void FR016_realRedisTtlPartitionAndExpiry() throws Exception {
        String key = "lb:topic:" + UUID.randomUUID() + ":all:all";
        try (var client = client()) {
            var cache = new LeaderboardCache(client, new ObjectMapper().findAndRegisterModules());
            var row = new Row(1, 7, 80, null, Instant.parse("2026-11-16T00:00:00Z"));
            assertThat(cache.read(key).payload()).isEmpty();
            cache.write(key, List.of(row), 1, Set.of(99L));
            assertThat(cache.read(key).payload().orElseThrow().top()).containsExactly(row);
            assertThat(cache.read(key + ":other").payload()).isEmpty();
            long ttl = Long.parseLong(REDIS.execInContainer("redis-cli", "PTTL", key).getStdout().trim());
            assertThat(ttl).isBetween(1L, 60_000L);
            REDIS.execInContainer("redis-cli", "PEXPIREAT", key, "1");
            assertThat(cache.read(key).payload()).isEmpty();
        } finally { REDIS.execInContainer("redis-cli", "DEL", key); }
    }

    @Test void FR017_refusedConnectionAndStalledHandshakeBecomeCacheMiss() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var properties = properties(port);
        try (var client = new LeaderboardRedisClient(properties)) {
            var cache = new LeaderboardCache(client, new ObjectMapper());
            assertThat(cache.read("lb:game:CHAR_RAIN:all:all").writable()).isFalse();
        }
        try (var listener = new ServerSocket(0);
                var client = new LeaderboardRedisClient(properties(listener.getLocalPort()))) {
            var cache = new LeaderboardCache(client, new ObjectMapper());
            assertThat(cache.read("lb:game:CHAR_RAIN:all:all").payload()).isEmpty();
            assertThat(LeaderboardRedisClient.TIMEOUT).isEqualTo(Duration.ofMillis(200));
        }
    }

    @Test void FR017_cacheOnlyClientClosesAndHonorsRedisUrl() {
        var properties = new RedisProperties();
        properties.setUrl("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/1");
        var client = new LeaderboardRedisClient(properties);
        assertThat(client.get("lb:absent:" + UUID.randomUUID())).isNull();
        client.close();
        client.close();
        assertThatIllegalStateException().isThrownBy(() -> client.get("lb:absent"));
    }

    private static LeaderboardRedisClient client() { return new LeaderboardRedisClient(properties(REDIS.getMappedPort(6379))); }
    private static RedisProperties properties(int port) {
        var properties = new RedisProperties();
        properties.setHost(REDIS.getHost());
        properties.setPort(port);
        return properties;
    }
}
