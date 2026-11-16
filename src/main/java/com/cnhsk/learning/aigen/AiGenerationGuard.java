package com.cnhsk.learning.aigen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

/** Owns one PostgreSQL session across short transactions and external calls. */
@Component
public class AiGenerationGuard {
    private final DataSource dataSource;
    private final AtomicBoolean occupied = new AtomicBoolean();

    public AiGenerationGuard(DataSource dataSource) { this.dataSource = dataSource; }

    public Optional<Lease> tryAcquire() {
        if (!occupied.compareAndSet(false, true)) return Optional.empty();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (!connection.getAutoCommit()) throw new SQLException("Unexpected transaction on lock session");
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT pg_backend_pid(), hashtextextended('ai-generate:job', 0),
                           pg_try_advisory_lock(hashtextextended('ai-generate:job', 0))
                    """)) {
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getBoolean(3)) return Optional.of(new Lease(connection, rows.getInt(1), rows.getLong(2)));
                }
            }
            connection.close();
            occupied.set(false);
            return Optional.empty();
        } catch (SQLException | RuntimeException exception) {
            if (connection != null) discard(connection);
            occupied.set(false);
            throw new GuardException();
        }
    }

    private void discard(Connection connection) {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).evictConnection(connection);
            } else connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException ignored) {
            try { connection.abort(Runnable::run); } catch (SQLException | RuntimeException unavailable) { /* Backend may already be gone. */ }
        } finally {
            try { connection.close(); } catch (SQLException ignored) { /* Evicted sessions cannot reenter the pool. */ }
        }
    }

    public class Lease implements AutoCloseable {
        private final Connection connection;
        private final int backend;
        private final long key;
        private boolean closed;

        private Lease(Connection connection, int backend, long key) {
            this.connection = connection; this.backend = backend; this.key = key;
        }

        public synchronized void assertOwned() {
            if (closed) throw new GuardException();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT pg_backend_pid() = ? AND EXISTS (
                        SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()
                          AND classid = CAST(? AS oid) AND objid = CAST(? AS oid) AND objsubid = 1 AND granted)
                    """)) {
                statement.setInt(1, backend);
                statement.setLong(2, key >>> 32);
                statement.setLong(3, key & 0xffffffffL);
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getBoolean(1)) throw new GuardException();
                }
            } catch (SQLException exception) { throw new GuardException(); }
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, key);
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getBoolean(1)) throw new GuardException();
                }
                connection.close();
            } catch (SQLException | RuntimeException exception) {
                discard(connection);
                throw new GuardException();
            } finally { occupied.set(false); }
        }
    }

    public static class GuardException extends RuntimeException {
        public GuardException() { super("GENERATION_LOCK_UNAVAILABLE"); }
    }
}
