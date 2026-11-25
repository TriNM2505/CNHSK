package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.mastery.MasteryReader;
import com.cnhsk.support.AbstractIntegrationTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(WeakKnowledgeSelectorIntegrationTest.SqlConfiguration.class)
class WeakKnowledgeSelectorIntegrationTest extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-15T06:00:00Z");
    @Autowired WeakKnowledgeSelector selector;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StubClaudeClient stub;
    @Autowired SqlCapture sql;
    @MockitoSpyBean WeakKnowledgeQueryRepository repository;
    @MockitoSpyBean AiGenerateConfig config;
    @MockitoBean MasteryReader mastery;
    @MockitoBean Clock clock;
    Fixtures fixtures;
    int providerCalls;

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        doReturn(14).when(config).windowDays();
        doReturn(20).when(config).minWrong();
        doReturn(10).when(config).minBank();
        doReturn(3).when(config).maxBatchesPerRun();
        when(mastery.valuesOf(anyLong(), any())).thenReturn(Map.of());
        fixtures = new Fixtures(jdbc);
        providerCalls = stub.callCount();
    }

    @AfterEach
    void removeOnlyOwnedRows() {
        if (fixtures != null) fixtures.close();
        assertThat(stub.callCount()).isEqualTo(providerCalls);
        assertThat(config.getCron()).isEqualTo("-");
    }

    @Test
    void filtersRawThresholdApprovedPrimaryBankPendingAnyRoleAndContent() {
        long user = fixtures.user();
        long a = fixtures.node(), b = fixtures.node(), c = fixtures.node(), d = fixtures.node();
        fixtures.wrong(user, a, 25); fixtures.wrong(user, b, 25);
        fixtures.wrong(user, c, 5); fixtures.wrong(user, d, 30);
        fixtures.bank(a, 3); fixtures.bank(b, 11);
        long pending = fixtures.question(a, "AI", "PENDING_REVIEW", null);
        fixtures.label(pending, d, "SECONDARY");
        // The pending PRIMARY also blocks A; move it to an unrelated node to isolate D's SECONDARY rule.
        jdbc.update("UPDATE learning.question_knowledge_points SET knowledge_point_id=? WHERE question_id=? AND role='PRIMARY'",
                fixtures.node(), pending);
        long noCanDo = fixtures.node(), blankCanDo = fixtures.node(), noLexemes = fixtures.node();
        fixtures.wrong(user, noCanDo, 40); fixtures.wrong(user, blankCanDo, 40); fixtures.wrong(user, noLexemes, 40);
        jdbc.update("UPDATE learning.knowledge_points SET can_do=NULL WHERE id=?", noCanDo);
        jdbc.update("UPDATE learning.knowledge_points SET can_do='  ' WHERE id=?", blankCanDo);
        jdbc.update("DELETE FROM learning.lexemes WHERE knowledge_point_id=?", noLexemes);
        // Non-AI pending and archived AI questions do not suppress an otherwise eligible node.
        fixtures.question(a, "MANUAL", "PENDING_REVIEW", null);
        fixtures.question(a, "AI", "ARCHIVED", null);

        sql.clear();
        var result = selector.select();

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.knowledgePointId()).isEqualTo(a);
            assertThat(candidate.wrongCount()).isEqualTo(25);
            assertThat(candidate.bankCount()).isEqualTo(4);
            assertThat(candidate.weakUsers()).isZero();
        });
        assertOwnBulkReads();
        verify(mastery, never()).valuesOf(anyLong(), any());
    }

    @Test
    void groupsDuplicatesWithoutSecondaryMultiplicationAndUsesExactSelectedJsonKey() {
        doReturn(1).when(config).minWrong();
        doReturn(10).when(config).maxBatchesPerRun();
        long user = fixtures.user(), primary = fixtures.node(), error = fixtures.node(), unused = fixtures.node();
        long question = fixtures.question(primary, "IMPORT", "APPROVED", fixtures.kid(error));
        fixtures.label(question, error, "SECONDARY"); fixtures.label(question, unused, "SECONDARY");
        fixtures.items(user, question, "B", false, NOW, 7);
        fixtures.items(user, question, "b", false, NOW, 2);
        fixtures.items(user, question, "A", true, NOW, 30);
        fixtures.items(user, question, "B", null, NOW, 30);
        fixtures.items(user, question, "B", false, NOW.minus(14, ChronoUnit.DAYS).minusMillis(1), 30);
        fixtures.items(user, question, "B", false, NOW.plusMillis(1), 30);
        fixtures.items(user, question, "B", false, NOW.minus(14, ChronoUnit.DAYS), 1);
        long same = fixtures.node();
        fixtures.items(user, fixtures.question(same, "IMPORT", "APPROVED", fixtures.kid(same)), "B", false, NOW, 3);
        long missingPrimary = fixtures.question(null, "MANUAL", "APPROVED", fixtures.kid(error));
        fixtures.items(user, missingPrimary, "B", false, NOW, 4);
        // A wrong vocabulary item counts for the node of its word, the way the tree links words to nodes.
        long lexemeAttempt = fixtures.attempt(user);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,is_correct,order_index,answered_at) "
                + "SELECT ?,id,'MEANING_CHOICE','VOCAB',false,1,? FROM learning.lexemes WHERE knowledge_point_id=?",
                lexemeAttempt, Timestamp.from(NOW), unused);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,is_correct,order_index,answered_at) "
                + "SELECT ?,id,'MEANING_CHOICE','VOCAB',true,2,? FROM learning.lexemes WHERE knowledge_point_id=?",
                lexemeAttempt, Timestamp.from(NOW), unused);
        // A word that belongs to no node reaches no candidate.
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,is_correct,order_index,answered_at) "
                + "SELECT ?,id,'MEANING_CHOICE','VOCAB',false,3,? FROM learning.lexemes WHERE knowledge_point_id IS NULL LIMIT 1",
                lexemeAttempt, Timestamp.from(NOW));

        var result = selector.select();

        assertThat(result).extracting(WeakKnowledgeSelector.Candidate::kid)
                .containsExactly(fixtures.kid(error), fixtures.kid(primary), fixtures.kid(same), fixtures.kid(unused));
        assertThat(result).extracting(WeakKnowledgeSelector.Candidate::wrongCount).containsExactly(12L, 10L, 6L, 1L);
        // Raw selection includes these IN_PROGRESS and suspicious attempts.
        assertThat(jdbc.queryForObject("SELECT bool_and(suspicious) FROM learning.attempts WHERE user_id=?", Boolean.class, user)).isTrue();
    }

    @Test
    void runningCanonicalPayloadBlocksOnlyItsKidAndPendingDoesNotBlock() {
        long user = fixtures.user(), a = fixtures.node(), b = fixtures.node();
        fixtures.wrong(user, a, 25); fixtures.wrong(user, b, 24);
        fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", fixtures.payload(a), NOW);
        fixtures.run(fixtures.user(), "AI_GENERATE", "PENDING", fixtures.payload(b), NOW);
        fixtures.run(fixtures.user(), "TRANSLATE", "RUNNING", fixtures.payload(b), NOW);
        assertThat(selector.select()).extracting(WeakKnowledgeSelector.Candidate::knowledgePointId).containsExactly(b);
    }

    @Test
    void sharedPrerequisiteReadFromTheTreeIsSelectedFirstBelowTheWrongThreshold() {
        long user = fixtures.user(), ba = fixtures.node(), bei = fixtures.node(), root = fixtures.node();
        fixtures.wrong(user, ba, 30); fixtures.wrong(user, bei, 25); fixtures.wrong(user, root, 5);
        jdbc.update("UPDATE learning.knowledge_points SET prereq_ids = ARRAY[?]::bigint[] WHERE id IN (?, ?)",
                root, ba, bei);

        assertThat(selector.select()).extracting(WeakKnowledgeSelector.Candidate::knowledgePointId)
                .containsExactly(root, ba, bei);
        verify(repository, times(1)).prerequisiteLinks();

        jdbc.update("UPDATE learning.knowledge_points SET prereq_ids = '{}' WHERE id IN (?, ?)", ba, bei);
        assertThat(selector.select()).extracting(WeakKnowledgeSelector.Candidate::knowledgePointId)
                .containsExactly(ba, bei);
    }

    @Test
    void invalidActivePayloadFailsClosedAfterStaleCleanupHasCommitted() {
        long node = fixtures.node(); fixtures.wrong(fixtures.user(), node, 25);
        long stale = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", fixtures.payload(node), NOW.minusSeconds(601));
        fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{\"kid\":null}", NOW);
        assertThatThrownBy(selector::select).isInstanceOf(IllegalStateException.class);
        assertThat(request(stale).get("status")).isEqualTo("FAILED");
    }

    @Test
    void zeroMaximumStillCleansStaleWithoutMasteryOrReadQueries() {
        doReturn(0).when(config).maxBatchesPerRun();
        long stale = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(601));
        sql.clear(); clearInvocations(repository);
        assertThat(selector.select()).isEmpty();
        assertThat(request(stale).get("status")).isEqualTo("FAILED");
        verify(mastery, never()).valuesOf(anyLong(), any());
        verify(repository, never()).wrongAnswers(any(), any());
        verify(repository, never()).nodes(any());
        verify(repository, never()).activeRuns();
    }

    @Test
    void boundaryTieUsesFullGroupAndCountsOnlyEachKidsActualReviewContributors() {
        doReturn(1).when(config).maxBatchesPerRun();
        long first = fixtures.user(), second = fixtures.user();
        long a = fixtures.node(), b = fixtures.node(), c = fixtures.node();
        fixtures.wrong(first, a, 20); fixtures.wrong(first, b, 20); fixtures.wrong(second, c, 20);
        Set<String> tied = Set.of(fixtures.kid(a), fixtures.kid(b), fixtures.kid(c));
        when(mastery.valuesOf(first, tied)).thenReturn(Map.of(fixtures.kid(c), new MasteryReader.MasteryView(10, "review"),
                fixtures.kid(a), new MasteryReader.MasteryView(10, "insufficient")));
        when(mastery.valuesOf(second, tied)).thenReturn(Map.of(fixtures.kid(c), new MasteryReader.MasteryView(10, "review")));
        var selected = selector.select();
        assertThat(selected).singleElement().satisfies(candidate -> {
            assertThat(candidate.knowledgePointId()).isEqualTo(c);
            assertThat(candidate.weakUsers()).isEqualTo(1);
            assertThat(candidate.wrongCount()).isEqualTo(20);
        });
        verify(mastery).valuesOf(first, tied); verify(mastery).valuesOf(second, tied);
    }

    @Test
    void masteryScansTop100UsersByWrongItemCountThenIdWithoutDoubleCountingSameKid() {
        doReturn(1).when(config).maxBatchesPerRun();
        long a = fixtures.node(), b = fixtures.node();
        long same = fixtures.question(a, "IMPORT", "APPROVED", fixtures.kid(a));
        long other = fixtures.question(b, "IMPORT", "APPROVED", null);
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            long user = fixtures.user(); users.add(user);
            fixtures.items(user, same, "B", false, NOW, 1);
            fixtures.items(user, other, "B", false, NOW, 2);
        }
        long highestItemCount = fixtures.user(), doubledContribution = fixtures.user();
        fixtures.items(highestItemCount, other, "B", false, NOW, 4);
        fixtures.items(doubledContribution, same, "B", false, NOW, 2);
        Set<String> tied = Set.of(fixtures.kid(a), fixtures.kid(b));
        when(mastery.valuesOf(anyLong(), any())).thenReturn(Map.of(fixtures.kid(b), new MasteryReader.MasteryView(0, "review")));
        assertThat(selector.select()).singleElement().satisfies(candidate -> {
            assertThat(candidate.knowledgePointId()).isEqualTo(b);
            assertThat(candidate.wrongCount()).isEqualTo(406);
            assertThat(candidate.weakUsers()).isEqualTo(100);
        });
        verify(mastery).valuesOf(highestItemCount, tied);
        for (long user : users.subList(0, 99)) verify(mastery).valuesOf(user, tied);
        verify(mastery, never()).valuesOf(users.get(99), tied);
        verify(mastery, never()).valuesOf(users.get(100), tied);
        verify(mastery, never()).valuesOf(users.get(200), tied);
        verify(mastery, never()).valuesOf(doubledContribution, tied);
        assertThat(org.mockito.Mockito.mockingDetails(mastery).getInvocations()).hasSize(100);
    }

    @Test
    void globalCleanupHasStrictCutoffPreservesFieldsAndIsIdempotent() {
        long node = fixtures.node();
        long stale = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", fixtures.payload(node), NOW.minusSeconds(601));
        long exact = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", fixtures.payload(node), NOW.minusSeconds(600));
        long young = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", fixtures.payload(node), NOW.minusSeconds(599));
        long pending = fixtures.run(fixtures.user(), "AI_GENERATE", "PENDING", "{}", NOW.minusSeconds(900));
        long other = fixtures.run(fixtures.user(), "TRANSLATE", "RUNNING", "{}", NOW.minusSeconds(900));
        jdbc.update("UPDATE learning.service_requests SET retry_count=2,result='{\"kept\":true}',feedback='preserved' WHERE id=?", stale);
        var before = request(stale);
        selector.select();
        var after = request(stale);
        assertThat(after.get("status")).isEqualTo("FAILED");
        assertThat(after.get("error_message")).isEqualTo("STALE");
        assertThat(((Timestamp) after.get("completed_at")).toInstant()).isEqualTo(NOW);
        for (String field : List.of("user_id", "kind", "payload", "result", "retry_count", "feedback", "created_at"))
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        for (long id : List.of(exact, young, other)) assertThat(request(id).get("status")).isEqualTo("RUNNING");
        assertThat(request(pending).get("status")).isEqualTo("PENDING");
        selector.select(); assertThat(request(stale)).isEqualTo(after);
    }

    @Test
    void cleanupFailureRollsBackEveryChangedRowButLaterReadFailureCannotUndoCleanup() {
        long first = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(601));
        long second = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(700));
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("cleanup fixture failure"); })
                .when(repository).failStale(any());
        assertThatThrownBy(selector::select).isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cleanup fixture failure");
        for (long id : List.of(first, second)) assertThat(request(id).get("status")).isEqualTo("RUNNING");
        org.mockito.Mockito.doCallRealMethod().when(repository).failStale(any());
        doThrow(new IllegalStateException("read fixture failure")).when(repository).wrongAnswers(any(), any());
        assertThatThrownBy(selector::select).isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("read fixture failure");
        for (long id : List.of(first, second)) assertThat(request(id).get("status")).isEqualTo("FAILED");
    }

    @Test
    void actualUniqueConstraintIsPerUserAndSelectionDoesNotClaimAcrossUsers() {
        long user = fixtures.user(), node = fixtures.node(); fixtures.wrong(user, node, 20);
        fixtures.run(user, "AI_GENERATE", "PENDING", fixtures.payload(node), NOW);
        assertThatThrownBy(() -> fixtures.run(user, "AI_GENERATE", "RUNNING", fixtures.payload(node), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        long other = fixtures.run(fixtures.user(), "AI_GENERATE", "PENDING", fixtures.payload(node), NOW);
        assertThat(other).isPositive();
        long before = jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, user);
        var first = selector.select(); var second = selector.select();
        assertThat(first).isEqualTo(second).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, user)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cleanupAndConditionalCompletionSerializeInBothOrders(boolean cleanupFirst) throws Exception {
        long run = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(601));
        Supplier<Integer> cleanup = () -> { repository.failStale(NOW); return 0; };
        Supplier<Integer> complete = () -> jdbc.update("UPDATE learning.service_requests SET status='COMPLETED',completed_at=? "
                + "WHERE id=? AND status='RUNNING'", Timestamp.from(NOW), run);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), secondEntered = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> transaction(() -> {
                int value = (cleanupFirst ? cleanup : complete).get(); locked.countDown(); await(release); return value;
            }));
            await(locked);
            var second = pool.submit(() -> transaction(() -> {
                secondEntered.countDown(); return (cleanupFirst ? complete : cleanup).get();
            }));
            await(secondEntered);
            release.countDown(); first.get(20, TimeUnit.SECONDS);
            int secondResult = second.get(20, TimeUnit.SECONDS);
            if (cleanupFirst) assertThat(secondResult).isZero();
            assertThat(request(run).get("status")).isEqualTo(cleanupFirst ? "FAILED" : "COMPLETED");
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentCleanupsLockRowsInIdOrderAndOnlyTransitionEachRunOnce() throws Exception {
        long firstRun = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(601));
        long secondRun = fixtures.run(fixtures.user(), "AI_GENERATE", "RUNNING", "{}", NOW.minusSeconds(700));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        sql.clear();
        try {
            Supplier<Integer> action = () -> transaction(() -> {
                ready.countDown(); await(start); return repository.failStale(NOW);
            });
            var first = pool.submit(action::get); var second = pool.submit(action::get);
            await(ready); start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(2);
            for (long id : List.of(firstRun, secondRun)) assertThat(request(id).get("status")).isEqualTo("FAILED");
            assertThat(sql.statements.stream().map(statement -> statement.toLowerCase().replaceAll("\\s+", " "))
                    .filter(statement -> statement.contains("for update")))
                    .hasSize(2).allSatisfy(statement -> assertThat(statement).contains("order by id for update"));
        } finally {
            start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void parallelSelectorsReturnSameAdvisoryCandidateWithoutInsertingRequests() throws Exception {
        long user = fixtures.user(), node = fixtures.node(); fixtures.wrong(user, node, 20);
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Supplier<List<WeakKnowledgeSelector.Candidate>> action = () -> {
                ready.countDown(); await(start); return selector.select();
            };
            var first = pool.submit(action::get); var second = pool.submit(action::get);
            await(ready); start.countDown();
            var result = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(result);
            assertThat(result).singleElement().extracting(WeakKnowledgeSelector.Candidate::knowledgePointId).isEqualTo(node);
            for (long ownedUser : fixtures.users)
                assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, ownedUser)).isZero();
        } finally {
            start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void legacyOptionsRemoveEveryContributionAndPreserveValidQuestions() {
        doReturn(1).when(config).minWrong();
        long primary = fixtures.node(), error = fixtures.node(), sibling = fixtures.node();
        long malformed = fixtures.question(primary, "IMPORT", "APPROVED", fixtures.kid(error));
        jdbc.update("UPDATE learning.questions SET options='[null,null]'::jsonb WHERE id=?", malformed);
        fixtures.items(fixtures.user(), malformed, "B", false, NOW, 40);
        fixtures.items(fixtures.user(), malformed, "B", false, NOW, 40);
        fixtures.wrong(fixtures.user(), sibling, 3);

        assertThat(selector.selectForGeneration()).extracting(WeakKnowledgeSelector.Candidate::knowledgePointId)
                .containsExactly(sibling);
    }

    @Test
    void contestAndMockExamWrongAnswersContributeAlongsidePractice() {
        doReturn(1).when(config).minWrong();
        long node = fixtures.node(), user = fixtures.user();
        long question = fixtures.question(node, "IMPORT", "APPROVED", null);
        for (String kind : List.of("CONTEST", "EXAM")) {
            fixtures.items(user, question, "B", false, NOW, 2);
            long attempt = fixtures.attempts.get(fixtures.attempts.size() - 1);
            jdbc.update("UPDATE learning.attempts SET kind=?,ref_type=? WHERE id=?", kind,
                    kind.equals("EXAM") ? "MOCK_EXAM" : "CONTEST", attempt);
        }

        assertThat(selector.select()).singleElement().satisfies(candidate -> {
            assertThat(candidate.knowledgePointId()).isEqualTo(node);
            assertThat(candidate.wrongCount()).isEqualTo(4);
        });
    }

    private void assertOwnBulkReads() {
        verify(repository).wrongAnswers(any(), any()); verify(repository).nodes(any()); verify(repository).activeRuns();
        assertThat(sql.statements.stream().filter(statement -> statement.toLowerCase().contains("learning.attempt_items"))).hasSize(1);
    }
    private Map<String, Object> request(long id) { return jdbc.queryForMap("SELECT * FROM learning.service_requests WHERE id=?", id); }
    private <T> T transaction(Supplier<T> action) { return new TransactionTemplate(transactions).execute(status -> action.get()); }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(25, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    static class Fixtures implements AutoCloseable {
        private static final AtomicInteger CODES = new AtomicInteger(100);
        private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
        final JdbcTemplate jdbc;
        final List<Long> users = new ArrayList<>(), nodes = new ArrayList<>(), questions = new ArrayList<>(), attempts = new ArrayList<>();
        Fixtures(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        long user() {
            long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Selector fixture') RETURNING id",
                    Long.class, "selector-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
            users.add(id); return id;
        }
        long node() {
            long id = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level,can_do) "
                    + "VALUES (?,'Self-authored node','NP','示例',1,'Understand a self-authored example') RETURNING id",
                    Long.class, "NP1-" + String.format("%03d", CODES.incrementAndGet()));
            nodes.add(id);
            jdbc.update("INSERT INTO learning.lexemes(kind,text,meaning_vi,knowledge_point_id) VALUES ('WORD',?,'Self-authored meaning',?)",
                    "selector-" + UUID.randomUUID(), id);
            return id;
        }
        String kid(long node) { return jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, node); }
        String payload(long node) { return "{\"kid\":\"" + kid(node) + "\",\"knowledgePointId\":" + node
                + ",\"model\":\"fixture\",\"wrongCount\":20,\"bankCount\":1}"; }
        long question(Long primary, String source, String status, String errorKid) {
            String options = "[{\"key\":\"A\",\"text\":\"Correct\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"Wrong\",\"is_correct\":false"
                    + (errorKid == null ? "" : ",\"error_kid\":\"" + errorKid + "\",\"error_note\":\"Self-authored explanation\"") + "}]";
            long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,source,status) "
                    + "VALUES (?,'MULTIPLE_CHOICE',1,'Self-authored selector question',?::jsonb,'A',?,?) RETURNING id",
                    Long.class, "SELECTOR-" + UUID.randomUUID(), options, source, status);
            questions.add(id); if (primary != null) label(id, primary, "PRIMARY"); return id;
        }
        void label(long question, long node, String role) {
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,?)", question, node, role);
        }
        void bank(long node, int count) { for (int i = 0; i < count; i++) question(node, "IMPORT", "APPROVED", null); }
        void wrong(long user, long node, int count) { items(user, question(node, "IMPORT", "APPROVED", null), "B", false, NOW, count); }
        long attempt(long user) {
            long id = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,suspicious) "
                    + "VALUES (?,'PRACTICE','TOPIC_TEST',true) RETURNING id", Long.class, user);
            attempts.add(id); return id;
        }
        void items(long user, long question, String answer, Boolean correct, Instant at, int count) {
            long attempt = attempt(user);
            List<Object[]> rows = new ArrayList<>();
            for (int i = 1; i <= count; i++) rows.add(new Object[] {attempt, question, answer, correct, i, Timestamp.from(at)});
            jdbc.batchUpdate("INSERT INTO learning.attempt_items(attempt_id,question_id,answer,is_correct,order_index,answered_at,item_type,skill) "
                    + "VALUES (?,?,?,?,?,?,'QUESTION_CHOICE','READING')", rows);
        }
        long run(long user, String kind, String status, String payload, Instant created) {
            return jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,created_at) VALUES (?,?,?,?::jsonb,?) RETURNING id",
                    Long.class, user, kind, status, payload, Timestamp.from(created));
        }
        @Override public void close() {
            for (long user : users) {
                jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", user);
                jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", user);
            }
            for (long attempt : attempts) {
                jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id=?", attempt);
                jdbc.update("DELETE FROM learning.attempts WHERE id=?", attempt);
            }
            for (long question : questions) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
            }
            for (long node : nodes) {
                jdbc.update("DELETE FROM learning.lexemes WHERE knowledge_point_id=?", node);
                jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
            }
            for (long user : users) jdbc.update("DELETE FROM auth.users WHERE id=?", user);
        }
    }

    @TestConfiguration
    static class SqlConfiguration {
        @Bean SqlCapture selectorSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer selectorInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        final List<String> statements = new CopyOnWriteArrayList<>();
        @Override public String inspect(String statement) { statements.add(statement); return statement; }
        void clear() { statements.clear(); }
        int count() { return statements.size(); }
    }
}
