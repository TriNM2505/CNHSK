package com.cnhsk.learning.aigen;

import com.cnhsk.learning.aigen.AiReviewControllerIntegrationTest.Fixture;
import com.cnhsk.learning.knowledge.QuestionAdminService;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdate;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdateOption;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.cnhsk.learning.aigen.AiReviewControllerIntegrationTest.NOW;
import static com.cnhsk.learning.aigen.AiReviewControllerIntegrationTest.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AiReviewControllerIntegrationTest.FixedTimeConfiguration.class)
class AiReviewConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired AiReviewService service;
    @Autowired QuestionAdminService questions;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean AuditService audit;
    private Fixture fixture;
    private long actor;

    @BeforeEach
    void setup() { fixture = new Fixture(jdbc); actor = fixture.user(); }

    @AfterEach
    void cleanup() {
        reset(audit);
        SecurityContextHolder.clearContext();
        fixture.cleanup(redis);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void competingApprovalsAndApproveRejectHaveOneTransitionOneConflictAndOneAudit(boolean bothApprove) throws Exception {
        long id = pending();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> run(ready, release, () -> service.approve(id)));
            var second = pool.submit(() -> run(ready, release,
                    () -> bothApprove ? service.approve(id) : service.reject(id, "Lý do tự soạn")));
            await(ready); release.countDown();
            List<Object> results = List.of(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
            assertThat(results).filteredOn(value -> value instanceof AiReviewService.ReviewResult).hasSize(1);
            assertThat(results).filteredOn(value -> value instanceof ErrorCode).containsExactly(ErrorCode.QUESTION_NOT_PENDING);
            assertThat(fixture.status(id)).isIn("APPROVED", "REJECTED");
            assertThat(fixture.audits(id, "AI_QUESTION_APPROVE") + fixture.audits(id, "AI_QUESTION_REJECT")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT reviewed_by FROM learning.questions WHERE id=?", Long.class, id)).isEqualTo(actor);
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editorEditAndReviewSerializeWithoutOverwritingReviewOrLosingTheEditedContent(boolean editFirst) throws Exception {
        long id = pending();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasWritten = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        QuestionUpdate update = new QuestionUpdate("他是老师。", List.of(
                new QuestionUpdateOption("A", "我", true, null, null), new QuestionUpdateOption("B", "你", false, null, null)),
                null, "Lời giải sau sửa", 1, 2, null, null, fixture.kid, List.of());
        Supplier<Object> edit = () -> questions.update(id, update);
        Supplier<Object> approve = () -> service.approve(id);
        try {
            var first = pool.submit(() -> asEditor(() -> new TransactionTemplate(manager).execute(tx -> {
                Object result = (editFirst ? edit : approve).get();
                firstHasWritten.countDown(); await(releaseFirst); return result;
            })));
            await(firstHasWritten);
            var second = pool.submit(() -> asEditor(() -> {
                secondReady.countDown();
                try { return (editFirst ? approve : edit).get(); }
                catch (ApiException error) { return error.getCode(); }
            }));
            await(secondReady); releaseFirst.countDown();
            first.get(25, TimeUnit.SECONDS);
            Object secondResult = second.get(25, TimeUnit.SECONDS);
            if (editFirst) {
                assertThat(secondResult).isInstanceOf(AiReviewService.ReviewResult.class);
                assertThat(jdbc.queryForObject("SELECT explanation FROM learning.questions WHERE id=?", String.class, id)).isEqualTo("Lời giải sau sửa");
                assertThat(fixture.audits(id, "QUESTION_UPDATE")).isEqualTo(1);
            } else {
                assertThat(secondResult).isEqualTo(ErrorCode.FORBIDDEN);
                assertThat(jdbc.queryForObject("SELECT explanation FROM learning.questions WHERE id=?", String.class, id)).isEqualTo("Lời giải tự soạn");
                assertThat(fixture.audits(id, "QUESTION_UPDATE")).isZero();
            }
            assertThat(fixture.status(id)).isEqualTo("APPROVED");
            assertThat(fixture.audits(id, "AI_QUESTION_APPROVE")).isEqualTo(1);
        } finally {
            releaseFirst.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void actualAuditInsertConstraintFailureRollsBackTheQuestionUpdate() {
        long id = pending(); failAuditInsertFor(id);
        assertThatThrownBy(() -> asEditor(() -> service.approve(id)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(fixture.status(id)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForMap("SELECT reviewed_by,reviewed_at FROM learning.questions WHERE id=?", id).values()).containsOnlyNulls();
        assertThat(fixture.audits(id, "AI_QUESTION_APPROVE")).isZero();
    }

    @Test
    void bulkMixedResultsKeepInputOrderAndCommitEachApprovedQuestionIndependently() {
        long approved = pending(); long unseen = pending(); long malformed = fixture.question("AI", "PENDING_REVIEW", 1, NOW, false);
        long already = fixture.question("AI", "APPROVED", 1, NOW, true);
        long missing = Long.MAX_VALUE;
        for (long id : List.of(approved, malformed, already, missing)) viewed(actor, id);
        long otherActor = fixture.user(); viewed(otherActor, unseen);
        var result = asEditor(() -> service.bulkApprove(List.of(unseen, approved, malformed, already, missing)));
        assertThat(result.results()).extracting(AiReviewService.BulkItem::id).containsExactly(unseen, approved, malformed, already, missing);
        assertThat(result.results()).extracting(AiReviewService.BulkItem::outcome)
                .containsExactly("NOT_VIEWED", "APPROVED", "MALFORMED", "NOT_PENDING", "NOT_PENDING");
        assertThat(fixture.status(approved)).isEqualTo("APPROVED");
        assertThat(fixture.status(unseen)).isEqualTo("PENDING_REVIEW");
        assertThat(fixture.status(malformed)).isEqualTo("PENDING_REVIEW");
        assertThat(fixture.audits(approved, "AI_QUESTION_APPROVE")).isEqualTo(1);
        assertThat(fixture.audits(malformed, "AI_QUESTION_APPROVE")).isZero();
        redis.delete(Fixture.key(actor, missing));
    }

    @Test
    void unexpectedAuditDatabaseFailureReturns500StopsBulkAndPreservesEarlierCommit() throws Exception {
        long first = pending(); long failing = pending(); long untouched = pending();
        for (long id : List.of(first, failing, untouched)) viewed(actor, id);
        failAuditInsertFor(failing);
        String token = jwt.issueAccessToken(actor, List.of("CONTENT_EDITOR"), true, UUID.randomUUID());
        mvc.perform(post(ROOT + "/bulk-approve").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("ids", List.of(first, failing, untouched)))))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andExpect(jsonPath("$.stackTrace").doesNotExist());
        assertThat(fixture.status(first)).isEqualTo("APPROVED");
        assertThat(fixture.audits(first, "AI_QUESTION_APPROVE")).isEqualTo(1);
        for (long id : List.of(failing, untouched)) {
            assertThat(fixture.status(id)).isEqualTo("PENDING_REVIEW");
            assertThat(fixture.audits(id, "AI_QUESTION_APPROVE")).isZero();
            assertThat(jdbc.queryForObject("SELECT reviewed_by FROM learning.questions WHERE id=?", Long.class, id)).isNull();
        }
    }

    private long pending() { return fixture.question("AI", "PENDING_REVIEW", 1, NOW, true); }
    private void viewed(long user, long id) { redis.opsForValue().set(Fixture.key(user, id), "1", Duration.ofHours(24)); }
    private void failAuditInsertFor(long id) {
        doAnswer(invocation -> {
            long auditActor = invocation.getArgument(0);
            if (auditActor != Long.MAX_VALUE && invocation.<String>getArgument(2).equals("question:" + id)) {
                audit.record(Long.MAX_VALUE, invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
                return null;
            }
            return invocation.callRealMethod();
        }).when(audit).record(anyLong(), anyString(), anyString(), any(), any());
    }
    private Object run(CountDownLatch ready, CountDownLatch release, Supplier<Object> action) {
        return asEditor(() -> {
            ready.countDown(); await(release);
            try { return action.get(); } catch (ApiException error) { return error.getCode(); }
        });
    }
    private <T> T asEditor(Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(actor, Set.of("CONTENT_EDITOR"), true, UUID.randomUUID().toString(), UUID.randomUUID(), Instant.now()), null, List.of()));
        try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
