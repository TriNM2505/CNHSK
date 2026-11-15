package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import com.cnhsk.learning.examadmin.ExamAdminService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

class ContestExamUsageGuardIntegrationTest extends ContestFixtures {
    private static final String ADMIN = "/api/admin/contests";
    @Autowired ContestExamUsageGuard guard;
    @Autowired ExamAdminService examAdmin;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @CsvSource({"DRAFT,1,false", "CANCELLED,1,false", "PUBLISHED,-1,false", "PUBLISHED,0,false", "PUBLISHED,1,true"})
    void FR018_023_guardOnlyProtectsPublishedContestsWhoseEndIsInTheFuture(String status, int end, boolean expected) {
        jdbc.update("UPDATE community.contests SET status=?,ends_at=? WHERE id=?", status, Timestamp.from(NOW.plusSeconds(end)), contest);
        assertThat(guard.isInUse(exam)).isEqualTo(expected);
        assertThat(guard.isInUse(Long.MAX_VALUE)).isFalse();
    }

    @Test
    void FR018_023_upcomingContestProtectsTheExamAndAnyMatchingContestIsEnough() {
        jdbc.update("UPDATE community.contests SET ends_at=? WHERE id=?", Timestamp.from(NOW), contest);
        long upcoming = contest(60, 3600, null);
        assertThat(guard.isInUse(exam)).isTrue();
        jdbc.update("UPDATE community.contests SET status='CANCELLED' WHERE id=?", upcoming);
        assertThat(guard.isInUse(exam)).isFalse();
    }

    @Test
    void FR018_023_realExamAdminArchiveIsBlockedByTheRealContestGuard() {
        assertThatThrownBy(() -> examAdmin.archive(exam, creator)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.EXAM_IN_USE));
        assertThat(jdbc.queryForObject("SELECT status FROM learning.exams WHERE id=?", String.class, exam)).isEqualTo("DRAFT");
        jdbc.update("UPDATE community.contests SET ends_at=? WHERE id=?", Timestamp.from(NOW), contest);
        assertThat(examAdmin.archive(exam, creator).status().name()).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=? AND action='EXAM_ARCHIVE' AND target_id=?",
                Integer.class, creator, exam)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR020_archiveAndContestPublicationSerializeWithBothRealServices(boolean archiveFirst) throws Exception {
        jdbc.update("UPDATE community.contests SET status='DRAFT',starts_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(60)), contest);
        Callable<String> archive = () -> { examAdmin.archive(exam, creator); return "OK"; };
        Callable<String> publish = () -> call(editor(post(ADMIN + "/{id}/publish", contest)));
        assertThat(serialized(archiveFirst ? archive : publish, archiveFirst ? publish : archive, () -> { }))
                .containsExactly("OK", archiveFirst ? "CONTEST_EXAM_INVALID" : "EXAM_IN_USE");
        assertThat(jdbc.queryForObject("SELECT status FROM community.contests WHERE id=?", String.class, contest))
                .isEqualTo(archiveFirst ? "DRAFT" : "PUBLISHED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR019_archiveAndPublishedExamReplacementSerializeOnTheNewExam(boolean archiveFirst) throws Exception {
        jdbc.update("UPDATE community.contests SET starts_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(60)), contest);
        long replacement = exam(15);
        question(replacement);
        Callable<String> archive = () -> { examAdmin.archive(replacement, creator); return "OK"; };
        Callable<String> update = () -> call(update(replacement));
        assertThat(serialized(archiveFirst ? archive : update, archiveFirst ? update : archive, () -> { }))
                .containsExactly("OK", archiveFirst ? "CONTEST_EXAM_INVALID" : "EXAM_IN_USE");
        assertThat(jdbc.queryForObject("SELECT exam_id FROM community.contests WHERE id=?", Long.class, contest))
                .isEqualTo(archiveFirst ? exam : replacement);
    }

    @Test
    void FR019_replacingAnExamAlsoLocksTheOldExamUntilCommit() throws Exception {
        jdbc.update("UPDATE community.contests SET starts_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(60)), contest);
        long replacement = exam(15);
        question(replacement);
        assertThat(serialized(() -> call(update(replacement)), () -> { examAdmin.archive(exam, creator); return "OK"; }, () -> { }))
                .containsExactly("OK", "OK");
        assertThat(guard.isInUse(exam)).isFalse();
        assertThat(guard.isInUse(replacement)).isTrue();
    }

    @Test
    void FR019_oldAndNewExamLocksAreTakenInIncreasingIdOrder() throws Exception {
        long old = exam(15);
        question(old);
        jdbc.update("UPDATE community.contests SET starts_at=?,exam_id=? WHERE id=?", Timestamp.from(NOW.plusSeconds(60)), old, contest);
        assertThat(old).isGreaterThan(exam);
        Callable<String> holder = () -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('exam:usage:' || CAST(? AS text),0))", exam);
            return "OK";
        };
        assertThat(serialized(holder, () -> call(update(exam)), () -> {
            boolean available = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended('exam:usage:' || CAST(? AS text),0))",
                    Boolean.class, old);
            assertThat(available).as("higher old exam is not locked while waiting for the lower replacement").isTrue();
        })).containsExactly("OK", "OK");
    }

    private MockHttpServletRequestBuilder update(long examId) {
        return editor(put(ADMIN + "/{id}", contest)).contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode()
                .put("name", "Đổi đề tự soạn").put("examId", examId).put("startsAt", NOW.plusSeconds(60).toString())
                .put("endsAt", NOW.plusSeconds(3600).toString()).toString());
    }

    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(creator, "CONTENT_EDITOR"));
    }

    private String call(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        return response.getStatus() < 300 ? "OK" : mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("error_code").asText();
    }

    private List<String> serialized(Callable<String> first, Callable<String> second, Runnable whileBlocked) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstWritten = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var pid = new AtomicInteger();
        try {
            var firstResult = executor.submit(() -> transaction(() -> {
                String result = first.call();
                firstWritten.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                return result;
            }));
            assertThat(firstWritten.await(15, TimeUnit.SECONDS)).isTrue();
            var secondResult = executor.submit(() -> transaction(() -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                secondEntered.countDown();
                return second.call();
            }));
            assertThat(secondEntered.await(15, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND NOT granted AND locktype='advisory'",
                        Integer.class, pid.get()) > 0;
                if (blocked) break;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("second transaction waits on exam usage advisory lock").isTrue();
            whileBlocked.run();
            release.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String transaction(Callable<String> work) {
        var result = new AtomicReference<String>();
        try {
            new TransactionTemplate(transactions).execute(status -> {
                try { result.set(work.call()); }
                catch (ApiException exception) { result.set(exception.getCode().name()); status.setRollbackOnly(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } catch (UnexpectedRollbackException exception) {
            if (result.get() == null || "OK".equals(result.get())) throw exception;
        }
        return result.get();
    }
}
