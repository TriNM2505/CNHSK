package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class TopicPrerequisiteConcurrencyIntegrationTest extends TopicPrerequisiteFixture {
    @Test void concurrentOppositeEdgesRecheckTheGraphAfterTheAdvisoryWait() throws Exception {
        long first = topic(1, 0);
        long second = topic(1, 0);
        assertThat(serialized(() -> service.setPrerequisites(first, owner, List.of(second)),
                () -> service.setPrerequisites(second, owner, List.of(first))))
                .containsExactly("OK", "PREREQUISITE_CYCLE");
        assertThat(prerequisites(first)).containsExactly(second);
        assertThat(prerequisites(second)).isEmpty();
        assertThat(audits()).isEqualTo(1);
        // A rejected transaction releases its locks so a later valid edit can commit.
        service.setPrerequisites(first, owner, List.of());
        service.setPrerequisites(second, owner, List.of(first));
        assertThat(audits()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void realPrerequisitePutAndNewStudyStartSerializeInBothOrders(boolean writerFirst) throws Exception {
        long prerequisite = topic(1, 0);
        long target = topic(1, 1);
        Callable<?> writer = () -> service.setPrerequisites(target, owner, List.of(prerequisite));
        Callable<?> starter = () -> study.start(owner, target);
        assertThat(serialized(writerFirst ? writer : starter, writerFirst ? starter : writer))
                .containsExactly("OK", writerFirst ? "TOPIC_LOCKED" : "OK");
        assertThat(prerequisites(target)).containsExactly(prerequisite);
        assertThat(audits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id=? AND ref_id=?", Long.class, owner, target))
                .isEqualTo(writerFirst ? 0 : 1);
        if (!writerFirst) assertThat(study.start(owner, target).created()).isFalse();
    }

    private List<String> serialized(Callable<?> first, Callable<?> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try {
            var one = pool.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                invoke(first);
                written.countDown();
                await(release);
                return null;
            })));
            assertThat(written.await(15, TimeUnit.SECONDS)).isTrue();
            var two = pool.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                entered.countDown();
                return invoke(second);
            })));
            assertThat(entered.await(15, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean blocked;
            do {
                blocked = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND NOT granted", Integer.class, pid.get()) > 0;
                if (!blocked) Thread.sleep(10);
            } while (!blocked && System.nanoTime() < deadline);
            assertThat(blocked).as("Second transaction waits for a real PostgreSQL lock").isTrue();
            release.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Object invoke(Callable<?> action) {
        try { return action.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private String outcome(Callable<?> action) {
        try { action.call(); return "OK"; }
        catch (ApiException exception) { return exception.getCode().name(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private void await(CountDownLatch signal) {
        try { assertThat(signal.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
