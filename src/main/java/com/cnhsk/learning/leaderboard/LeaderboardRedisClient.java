package com.cnhsk.learning.leaderboard;

import java.time.Duration;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

/** A cache-only connection; it does not replace Spring's shared RedisConnectionFactory. */
@Component
public class LeaderboardRedisClient implements AutoCloseable {
    static final Duration TIMEOUT = Duration.ofMillis(200);
    private final RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private boolean closed;

    public LeaderboardRedisClient(RedisProperties properties) {
        RedisURI uri;
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
            uri = RedisURI.create(properties.getUrl());
        } else {
            uri = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                    .withDatabase(properties.getDatabase()).withSsl(properties.getSsl().isEnabled()).build();
            if (properties.getPassword() != null) uri.setPassword(properties.getPassword().toCharArray());
            if (properties.getUsername() != null) uri.setUsername(properties.getUsername());
        }
        uri.setTimeout(TIMEOUT);
        client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(TIMEOUT).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
    }

    private synchronized StatefulRedisConnection<String, String> connection() {
        if (closed) throw new IllegalStateException("Leaderboard cache client is closed.");
        if (connection == null) {
            connection = client.connect();
            connection.setTimeout(TIMEOUT);
        }
        return connection;
    }

    public String get(String key) { return connection().sync().get(key); }

    public void set(String key, String value, Duration ttl) {
        connection().sync().psetex(key, ttl.toMillis(), value);
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (connection != null) connection.close();
        client.shutdown(Duration.ZERO, TIMEOUT);
    }
}
