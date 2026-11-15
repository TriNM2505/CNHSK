package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

class ContestAdminConcurrencyIntegrationTest extends ContestFixtures {
    private static final String ADMIN = "/api/admin/contests";
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void FR021_cancelAndEnterSerializeOnTheSameContestLock(boolean cancelFirst) throws Exception {
        Callable<String> cancel = () -> call(editor(post(ADMIN + "/{id}/cancel", contest)));
        Callable<String> enter = () -> call(enter(learner));
        assertThat(serialized(cancelFirst ? cancel : enter, cancelFirst ? enter : cancel, "advisory"))
                .containsExactly("OK", cancelFirst ? "CONTEST_NOT_FOUND" : "CONTEST_HAS_ATTEMPTS");
        assertThat(attempts.countAttempts(contest)).isEqualTo(cancelFirst ? 0 : 1);
        assertThat(jdbc.queryForObject("SELECT status FROM community.contests WHERE id=?", String.class, contest))
                .isEqualTo(cancelFirst ? "CANCELLED" : "PUBLISHED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void AT12_AT17_decisionAndPublicationSerializeBeforeChangingTheRanking(boolean publishFirst) throws Exception {
        jdbc.update("UPDATE community.contests SET ends_at=? WHERE id=?", Timestamp.from(NOW), contest);
        attempt(learner, contest, "SUBMITTED", 1L, 1L, 2000, false);
        Callable<String> publish = () -> call(editor(post(ADMIN + "/{id}/ranking/publish", contest)));
        Callable<String> decide = () -> call(editor(patch(ADMIN + "/{id}/participants/{userId}", contest, learner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"excluded\":true,\"note\":\"Quyết định tự soạn\"}"));
        assertThat(serialized(publishFirst ? publish : decide, publishFirst ? decide : publish, null))
                .containsExactly("OK", publishFirst ? "CONTEST_LOCKED" : "OK");
        assertThat(attempts.myAttempt(learner, contest).orElseThrow().suspicious()).isEqualTo(!publishFirst);
        assertThat(audits("CONTEST_RANKING_PUBLISH")).isEqualTo(1);
        assertThat(audits("CONTEST_PARTICIPANT_EXCLUDE")).isEqualTo(publishFirst ? 0 : 1);
    }

    @Test
    void AT17_twoRankingPublicationsWriteOneTimestampAndOneAudit() throws Exception {
        jdbc.update("UPDATE community.contests SET ends_at=? WHERE id=?", Timestamp.from(NOW), contest);
        Callable<String> publish = () -> call(editor(post(ADMIN + "/{id}/ranking/publish", contest)));
        assertThat(serialized(publish, publish, null)).containsExactly("OK", "CONTEST_LOCKED");
        assertThat(audits("CONTEST_RANKING_PUBLISH")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ranking_published_at FROM community.contests WHERE id=?", Timestamp.class, contest).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void FR020_competingPublicationsCanOnlyPublishADraftOnce() throws Exception {
        jdbc.update("UPDATE community.contests SET status='DRAFT',starts_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(60)), contest);
        Callable<String> publish = () -> call(editor(post(ADMIN + "/{id}/publish", contest)));
        assertThat(serialized(publish, publish, "advisory")).containsExactly("OK", "CONTEST_LOCKED");
    }

    private String call(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        return response.getStatus() < 300 ? "OK" : mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("error_code").asText();
    }

    private long audits(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE action=? AND target_type='CONTEST' AND target_id=?",
                Long.class, action, contest);
    }

    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(creator, "CONTENT_EDITOR"));
    }

    private List<String> serialized(Callable<String> first, Callable<String> second, String lockType) throws Exception {
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
                var waits = jdbc.queryForList("SELECT wait_event FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock'",
                        String.class, pid.get());
                if (!waits.isEmpty() && (lockType == null || waits.contains(lockType))) { blocked = true; break; }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(blocked).as("second transaction waits on the PostgreSQL lock").isTrue();
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
                catch (Exception exception) { throw new IllegalStateException(exception); }
                return null;
            });
        } catch (UnexpectedRollbackException exception) {
            if (result.get() == null || "OK".equals(result.get())) throw exception;
        }
        return result.get();
    }
}
