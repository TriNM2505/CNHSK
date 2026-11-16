package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cnhsk.support.AbstractIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=3")
class AiGenerationGuardIntegrationTest extends AbstractIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void twoIndependentInstancesCannotOwnTheSameDatabaseSessionLock() {
        var first = new AiGenerationGuard(dataSource);
        var second = new AiGenerationGuard(dataSource);
        try (var lease = first.tryAcquire().orElseThrow()) {
            lease.assertOwned();
            assertThat(second.tryAcquire()).isEmpty();
            assertThat(lockCount()).isEqualTo(1);
        }
        assertThat(lockCount()).isZero();
        try (var next = second.tryAcquire().orElseThrow()) { next.assertOwned(); }
    }

    @Test
    void localContendersNeverBorrowAdditionalConnections() throws Exception {
        DataSource observed = spy(dataSource);
        var guard = new AiGenerationGuard(observed);
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        try (var lease = guard.tryAcquire().orElseThrow()) {
            List<java.util.concurrent.Future<Boolean>> contenders = new ArrayList<>();
            for (int i = 0; i < 8; i++) contenders.add(executor.submit(() -> {
                ready.countDown(); await(start); return guard.tryAcquire().isEmpty();
            }));
            await(ready); start.countDown();
            for (var contender : contenders) assertThat(contender.get(20, TimeUnit.SECONDS)).isTrue();
            verify(observed, times(1)).getConnection();
            lease.assertOwned();
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        } finally {
            start.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(lockCount()).isZero();
    }

    @Test
    void unrelatedTransactionRollbackDoesNotReleaseTheSessionLeaseAndCloseIsIdempotent() {
        var guard = new AiGenerationGuard(dataSource);
        var other = new AiGenerationGuard(dataSource);
        var lease = guard.tryAcquire().orElseThrow();
        try {
            new TransactionTemplate(transactions).execute(status -> {
                assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
                status.setRollbackOnly(); return null;
            });
            lease.assertOwned();
            assertThat(other.tryAcquire()).isEmpty();
        } finally { lease.close(); }
        lease.close();
        assertThatThrownBy(lease::assertOwned).isInstanceOf(AiGenerationGuard.GuardException.class);
        try (var next = guard.tryAcquire().orElseThrow()) { next.assertOwned(); }
        assertThat(lockCount()).isZero();
    }

    @Test
    void terminatedOwnedSessionCannotResumeAndFailedUnlockEvictsOnlyThatConnection() throws Exception {
        var guard = new AiGenerationGuard(dataSource);
        var lease = guard.tryAcquire().orElseThrow();
        int backend = jdbc.queryForObject("SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted "
                + "AND classid=CAST((hashtextextended('ai-generate:job',0) >> 32) & 4294967295 AS oid) "
                + "AND objid=CAST(hashtextextended('ai-generate:job',0) & 4294967295 AS oid) AND objsubid=1", Integer.class);
        try {
            assertThat(jdbc.queryForObject("SELECT pg_terminate_backend(?)", Boolean.class, backend)).isTrue();
            assertThatThrownBy(lease::assertOwned).isInstanceOf(AiGenerationGuard.GuardException.class);
        } finally {
            assertThatThrownBy(lease::close).isInstanceOf(AiGenerationGuard.GuardException.class);
        }
        assertThat(dataSource.unwrap(HikariDataSource.class).isClosed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(lockCount()).isZero();
        try (var next = guard.tryAcquire().orElseThrow()) { next.assertOwned(); }
    }

    private long lockCount() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND granted "
                + "AND classid=CAST((hashtextextended('ai-generate:job',0) >> 32) & 4294967295 AS oid) "
                + "AND objid=CAST(hashtextextended('ai-generate:job',0) & 4294967295 AS oid) AND objsubid=1", Long.class);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
