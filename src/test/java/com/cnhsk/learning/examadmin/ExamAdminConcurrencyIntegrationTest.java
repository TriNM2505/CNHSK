package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.api.ExamUsageGuard;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ExamAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ExamAdminService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private ExamUsageGuard guard;
    private ExamAdminFixture fixture;

    @BeforeEach void setup() { fixture = new ExamAdminFixture(jdbc); }
    @AfterEach void cleanup() { fixture.close(); }

    @Test
    void twoEditorsUsingTheSameEtagCannotOverwriteEachOther() throws Exception {
        var input = fixture.input(2, 1);
        var original = service.create(fixture.actor, input);
        var first = new ExamWrite(input.code(), "Người sửa một", 3, 2, input.sections(), input.questions());
        var second = new ExamWrite(input.code(), "Người sửa hai", 3, 2, input.sections(), List.of(input.questions().get(1), input.questions().get(0)));
        assertThat(race(() -> service.update(original.id(), () -> ExamAdminServiceTest.update(first, original.etag())),
                () -> service.update(original.id(), () -> ExamAdminServiceTest.update(second, original.etag()))))
                .containsExactlyInAnyOrder("OK", "EDIT_CONFLICT");
        var saved = service.detail(original.id());
        assertThat(saved.etag()).isNotEqualTo(original.etag());
        assertThat(saved.title()).isIn(first.title(), second.title());
        List<Long> expected = (saved.title().equals(first.title()) ? first : second).questions().stream().map(QuestionWrite::questionId).toList();
        assertThat(saved.questions()).extracting(QuestionView::questionId).containsExactlyElementsOf(expected);
    }

    @Test
    void simultaneousCreatesWithTheSameCodeProduceOneCompleteDraftAndOneConflict() throws Exception {
        var input = fixture.input(2, 1);
        long other = fixture.user();
        assertThat(race(() -> service.create(fixture.actor, input), () -> service.create(other, input)))
                .containsExactlyInAnyOrder("OK", "EXAM_CODE_TAKEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.exams WHERE code=?", Integer.class, input.code())).isEqualTo(1);
        long id = jdbc.queryForObject("SELECT id FROM learning.exams WHERE code=?", Long.class, input.code());
        assertThat(service.detail(id).questions()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publicationAndArchiveSerializeInEitherOrderWithoutDeadlocking(boolean publishFirst) throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        Callable<?> publish = () -> service.publish(draft.id(), fixture.actor, () -> new EtagBody(draft.etag()));
        Callable<?> archive = () -> service.archive(draft.id(), fixture.actor);
        assertThat(serialized(publishFirst ? publish : archive, publishFirst ? archive : publish, null))
                .containsExactly("OK", publishFirst ? "OK" : "EXAM_NOT_EDITABLE");
        var saved = service.detail(draft.id());
        assertThat(saved.status()).isEqualTo(ExamStatus.ARCHIVED);
        assertThat(saved.publishedAt() != null).isEqualTo(publishFirst);
        assertThat(audits(draft.id(), "EXAM_PUBLISH")).isEqualTo(publishFirst ? 1 : 0);
        assertThat(audits(draft.id(), "EXAM_ARCHIVE")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publicationAndDraftEditsObserveTheFirstCommittedChange(boolean publishFirst) throws Exception {
        var input = fixture.input(1, 1);
        var draft = service.create(fixture.actor, input);
        var changed = new ExamWrite(input.code(), "Đề đã sửa", 3, 1, input.sections(), input.questions());
        Callable<?> publish = () -> service.publish(draft.id(), fixture.actor, () -> new EtagBody(draft.etag()));
        Callable<?> edit = () -> service.update(draft.id(), () -> ExamAdminServiceTest.update(changed, draft.etag()));
        assertThat(serialized(publishFirst ? publish : edit, publishFirst ? edit : publish, null))
                .containsExactly("OK", publishFirst ? "EXAM_NOT_EDITABLE" : "EDIT_CONFLICT");
        assertThat(service.detail(draft.id()).status()).isEqualTo(publishFirst ? ExamStatus.PUBLISHED : ExamStatus.DRAFT);
        assertThat(audits(draft.id(), "EXAM_PUBLISH")).isEqualTo(publishFirst ? 1 : 0);
    }

    @Test
    void competingPublicationsOnlyProduceOnePublishAudit() throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        Callable<?> publish = () -> service.publish(draft.id(), fixture.actor, () -> new EtagBody(draft.etag()));
        assertThat(serialized(publish, publish, null)).containsExactly("OK", "EXAM_NOT_EDITABLE");
        assertThat(audits(draft.id(), "EXAM_PUBLISH")).isEqualTo(1);
    }

    @Test
    void competingArchivesAreIdempotentAndOnlyProduceOneAudit() throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        Callable<?> archive = () -> service.archive(draft.id(), fixture.actor);
        assertThat(serialized(archive, archive, "advisory")).containsExactly("OK", "OK");
        assertThat(audits(draft.id(), "EXAM_ARCHIVE")).isEqualTo(1);
        org.mockito.Mockito.verify(guard).isInUse(draft.id());
    }

    @Test
    void archiveWaitsForTheContestWriterUsageLockBeforeConsultingTheGuard() throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        var published = service.publish(draft.id(), fixture.actor, () -> new EtagBody(draft.etag()));
        org.mockito.Mockito.when(guard.isInUse(draft.id())).thenReturn(true);
        Callable<?> writer = () -> {
            lockUsage(draft.id());
            assertThat(jdbc.queryForObject("SELECT status FROM learning.exams WHERE id=? FOR UPDATE", String.class, draft.id())).isEqualTo("PUBLISHED");
            return null;
        };
        assertThat(serialized(writer, () -> service.archive(draft.id(), fixture.actor), "advisory"))
                .containsExactly("OK", "EXAM_IN_USE");
        assertThat(service.detail(draft.id())).isEqualTo(published);
        assertThat(audits(draft.id(), "EXAM_ARCHIVE")).isZero();
        org.mockito.Mockito.verify(guard).isInUse(draft.id());
    }

    @Test
    void contestWriterReadsTheArchivedStateAfterWaitingForTheSameUsageLock() throws Exception {
        var draft = service.create(fixture.actor, fixture.input(1, 1));
        Callable<?> writer = () -> {
            lockUsage(draft.id());
            return assertThat(jdbc.queryForObject("SELECT status FROM learning.exams WHERE id=? FOR UPDATE", String.class, draft.id()))
                    .isEqualTo("ARCHIVED");
        };
        assertThat(serialized(() -> service.archive(draft.id(), fixture.actor), writer, "advisory"))
                .containsExactly("OK", "OK");
        assertThat(audits(draft.id(), "EXAM_ARCHIVE")).isEqualTo(1);
    }

    private long audits(long id, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_type='EXAM' AND target_id=? AND action=?", Long.class, id, action);
    }

    private void lockUsage(long id) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('exam:usage:' || CAST(? AS text),0))", id);
    }

    private List<String> serialized(Callable<?> first, Callable<?> second, String lockType) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstWritten = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try {
            var firstResult = executor.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                invoke(first);
                firstWritten.countDown();
                await(release);
                return null;
            })));
            assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
            var secondResult = executor.submit(() -> outcome(() -> new TransactionTemplate(transactions).execute(status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondEntered.countDown();
                return invoke(second);
            })));
            assertThat(secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean blocked;
            do {
                blocked = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND NOT granted "
                        + "AND (CAST(? AS text) IS NULL OR locktype=?)", Integer.class, pid.get(), lockType, lockType) > 0;
                if (!blocked) Thread.sleep(10);
            } while (!blocked && System.nanoTime() < deadline);
            assertThat(blocked).as("second transaction waits on the first transaction's lock").isTrue();
            release.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String outcome(Callable<?> action) throws Exception {
        try { action.call(); return "OK"; }
        catch (ApiException exception) { return exception.getCode().name(); }
    }

    private Object invoke(Callable<?> action) {
        try { return action.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private void await(CountDownLatch signal) {
        try { assertThat(signal.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = java.util.stream.Stream.of(first, second).map(action -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try { action.call(); return "OK"; }
                catch (ApiException exception) { return exception.getCode().name(); }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
