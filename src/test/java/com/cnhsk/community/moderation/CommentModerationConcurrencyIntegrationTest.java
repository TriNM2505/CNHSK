package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL row locks, native moderation DML, and committed independent HTTP transactions. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommentModerationConcurrencyIntegrationTest.ModerationConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=6",
        "spring.datasource.hikari.minimum-idle=6",
        "spring.datasource.hikari.connection-timeout=20000"
})
class CommentModerationConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant REQUEST_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant CREATED = REQUEST_TIME.minusSeconds(300);
    private static final String CONTENT = "Synthetic parent 中文 😀 <b>plain text</b>";
    private static final String EDITED_CONTENT = "Synthetic complete author edit 中文 😀";
    private static final String FIRST_REASON = "First moderation reason 中文 😀";
    private static final String SECOND_REASON = "Second moderation reason 中文 😀";
    private static final String OLD_REASON = "Existing moderator reason";
    private static final String CASE_NOTE = "Synthetic report decision 中文 😀";
    private static final Set<String> MODERATION_FIELDS = Set.of(
            "status", "moderated_by", "moderation_reason", "row_version");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ModerationSqlCapture capture;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private CommentRepository comments;
    @MockitoSpyBean private AuditService audit;

    @Test
    void competingHidesWaitOnTheCommentAndCommitOneMutationAndOneAudit() throws Exception {
        Fixture fixture = fixture(false, false);
        Map<String, Object> before = commentRow(fixture.comment());
        Hold hold = probeDirectMutation(fixture, fixture.first());
        holdAudit(fixture, fixture.first(), "COMMENT", fixture.comment(), "HIDDEN", hold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = submitDirect(workers, "first-hide", fixture.first(), fixture,
                    "HIDE", FIRST_REASON);
            await(hold.ready, "real first comment UPDATE and real audit INSERT before commit");
            CompletableFuture<MvcResult> second = submitDirect(workers, "second-hide", fixture.second(), fixture,
                    "HIDE", SECOND_REASON);
            awaitBlocked(hold.mutated.get().pid(), "%community.comments%for%update%");
            hold.release.countDown();
            assertDirectResponse(result(first), fixture, "HIDDEN", fixture.first().id(), FIRST_REASON, CONTENT);
            assertDirectResponse(result(second), fixture, "HIDDEN", fixture.first().id(), FIRST_REASON, CONTENT);
            assertModerationOnly(before, commentRow(fixture.comment()), "HIDDEN", fixture.first().id(), FIRST_REASON);
            assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), "HIDE", "VISIBLE", "HIDDEN", FIRST_REASON)));
            assertDirectSql("first-hide", true);
            assertDirectSql("second-hide", false);
            assertThat(hold.audited.get()).isEqualTo(hold.mutated.get());
            System.out.println("COMMENT_MODERATION_SAME_ACTION_EVIDENCE actualRowLockWait=true updates=1 commentAudits=1");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "{0}: {1} then {2} observes {3} then {4}")
    @CsvSource({
            "VISIBLE,HIDE,DELETE,HIDDEN,DELETED",
            "VISIBLE,DELETE,HIDE,DELETED,DELETED",
            "VISIBLE,HIDE,RESTORE,HIDDEN,VISIBLE",
            "VISIBLE,RESTORE,HIDE,VISIBLE,HIDDEN",
            "VISIBLE,DELETE,RESTORE,DELETED,VISIBLE",
            "VISIBLE,RESTORE,DELETE,VISIBLE,DELETED",
            "HIDDEN,DELETE,RESTORE,DELETED,VISIBLE",
            "HIDDEN,RESTORE,DELETE,VISIBLE,DELETED",
            "DELETED,HIDE,RESTORE,DELETED,VISIBLE",
            "DELETED,RESTORE,HIDE,VISIBLE,HIDDEN"
    })
    void differentDirectActionsUseTheLockedCurrentStateInBothOrders(String initial, String firstAction,
            String secondAction, String intermediate, String expected) throws Exception {
        Fixture fixture = fixture(true, false);
        seedState(fixture, initial, initial.equals("VISIBLE") ? null : fixture.second().id(),
                initial.equals("VISIBLE") ? null : OLD_REASON);
        Map<String, Object> before = commentRow(fixture.comment());
        Hold hold = holdDirectLock(fixture);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = submitDirect(workers, "ordered-first", fixture.first(), fixture,
                    firstAction, FIRST_REASON);
            await(hold.ready, "first real SELECT FOR UPDATE, including an initial no-op");
            CompletableFuture<MvcResult> second = submitDirect(workers, "ordered-second", fixture.second(), fixture,
                    secondAction, SECOND_REASON);
            awaitBlocked(hold.locked.get().pid(), "%community.comments%for%update%");
            hold.release.countDown();
            boolean firstChanged = !initial.equals(intermediate);
            boolean secondChanged = !intermediate.equals(expected);
            Long intermediateActor = firstChanged ? Long.valueOf(fixture.first().id()) : numberOrNull(before.get("moderated_by"));
            String intermediateReason = firstChanged ? FIRST_REASON : (String) before.get("moderation_reason");
            long finalActor = secondChanged ? fixture.second().id() : intermediateActor;
            String finalReason = secondChanged ? SECOND_REASON : intermediateReason;
            assertDirectResponse(result(first), fixture, intermediate, intermediateActor, intermediateReason, CONTENT);
            assertDirectResponse(result(second), fixture, expected, finalActor, finalReason, CONTENT);
            assertModerationOnly(before, commentRow(fixture.comment()), expected, finalActor, finalReason);
            List<ExpectedAudit> expectedAudits = new ArrayList<>();
            if (firstChanged) expectedAudits.add(expectedAudit(fixture.first(), firstAction, initial, intermediate, FIRST_REASON));
            if (secondChanged) expectedAudits.add(expectedAudit(fixture.second(), secondAction, intermediate, expected, SECOND_REASON));
            assertCommentAudits(fixture, expectedAudits);
            assertDirectSql("ordered-first", firstChanged);
            assertDirectSql("ordered-second", secondChanged);
            System.out.println("COMMENT_MODERATION_ORDER_EVIDENCE actualRowLockWait=true firstChanged="
                    + firstChanged + " secondChanged=" + secondChanged + " exactAuditChain=true");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "report/direct initial={0}, direct={1}, report={2}, direct first={3}")
    @CsvSource({
            "VISIBLE,HIDE,DELETE,true", "VISIBLE,HIDE,DELETE,false",
            "HIDDEN,RESTORE,DELETE,true", "HIDDEN,RESTORE,DELETE,false"
    })
    void reportCaseActionsAndDirectModerationSerializeWithoutLostUpdatesOrParentLockInversion(String initial,
            String directAction, String reportAction, boolean directFirst) throws Exception {
        Fixture fixture = fixture(true, true);
        seedState(fixture, initial, initial.equals("VISIBLE") ? null : fixture.second().id(),
                initial.equals("VISIBLE") ? null : OLD_REASON);
        Map<String, Object> before = commentRow(fixture.comment());
        Map<String, Object> childBefore = commentRow(fixture.child());
        Map<String, Object> postBefore = postRow(fixture.post());
        Map<String, Object> caseBefore = caseRow(fixture.caseId());
        String intermediate = directFirst ? directAfter(initial, directAction) : reportAfter(initial, reportAction);
        String expected = directFirst ? reportAfter(intermediate, reportAction) : directAfter(intermediate, directAction);
        boolean directChanged = directFirst ? !initial.equals(intermediate) : !intermediate.equals(expected);
        boolean reportChanged = directFirst ? !intermediate.equals(expected) : !initial.equals(intermediate);
        Hold hold = directFirst ? probeDirectMutation(fixture, fixture.first()) : probeReportMutation(fixture, fixture.second());
        holdAudit(fixture, directFirst ? fixture.first() : fixture.second(), directFirst ? "COMMENT" : "REPORT_CASE",
                directFirst ? fixture.comment() : fixture.caseId(), intermediate, hold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = directFirst
                    ? submitDirect(workers, "direct-race", fixture.first(), fixture, directAction, FIRST_REASON)
                    : submitCase(workers, "case-race", fixture.second(), fixture, reportAction);
            await(hold.ready, "first real target operation and audit INSERT before commit");
            CompletableFuture<MvcResult> second = directFirst
                    ? submitCase(workers, "case-race", fixture.second(), fixture, reportAction)
                    : submitDirect(workers, "direct-race", fixture.first(), fixture, directAction, FIRST_REASON);
            awaitBlocked(hold.mutated.get().pid(), directFirst
                    ? "%update%community.comments%" : "%community.comments%for%update%");
            hold.release.countDown();
            MvcResult firstResult = result(first);
            MvcResult secondResult = result(second);
            String directState = directFirst ? intermediate : expected;
            Long directActor = directChanged ? Long.valueOf(fixture.first().id())
                    : reportChanged ? Long.valueOf(fixture.second().id()) : numberOrNull(before.get("moderated_by"));
            String directReason = directChanged ? FIRST_REASON
                    : reportChanged ? CASE_NOTE : (String) before.get("moderation_reason");
            assertDirectResponse(directFirst ? firstResult : secondResult, fixture, directState,
                    directActor, directReason, CONTENT);
            assertCaseResponse(directFirst ? secondResult : firstResult, fixture, fixture.second(),
                    directFirst ? expected : intermediate);
            long finalActor = directFirst ? reportChanged ? fixture.second().id() : fixture.first().id()
                    : directChanged ? fixture.first().id() : fixture.second().id();
            String finalReason = directFirst ? reportChanged ? CASE_NOTE : FIRST_REASON
                    : directChanged ? FIRST_REASON : CASE_NOTE;
            assertModerationOnly(before, commentRow(fixture.comment()), expected, finalActor, finalReason);
            assertThat(commentRow(fixture.child())).isEqualTo(childBefore);
            assertThat(postRow(fixture.post())).isEqualTo(postBefore);
            assertPreserved(caseBefore, caseRow(fixture.caseId()), Set.of("status", "handled_by", "handled_at", "decision_note"));
            assertCommentAudits(fixture, directChanged ? List.of(expectedAudit(fixture.first(), directAction,
                    directFirst ? initial : intermediate, directState, FIRST_REASON)) : List.of());
            assertCaseAudit(fixture, fixture.second(), reportAction);
            assertDirectSql("direct-race", directChanged);
            assertThat(capture.statements("case-race")).noneMatch(CommentModerationConcurrencyIntegrationTest::explicitLock);
            assertThat(commentUpdates("case-race")).hasSize(1);
            assertThat(hold.audited.get()).isEqualTo(hold.mutated.get());
            System.out.println("COMMENT_MODERATION_CASE_RACE_EVIDENCE directFirst=" + directFirst
                    + " actualTransactionIdWait=true directChanged=" + directChanged + " reportChanged="
                    + reportChanged + " reportCaseAudits=1 deadlock=false");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @Test
    void reportActionSeesCommittedDeletedStateAndCommitsWhileDirectRestoreRemainsUncommitted() throws Exception {
        Fixture fixture = fixture(true, true);
        seedState(fixture, "DELETED", fixture.second().id(), OLD_REASON);
        Map<String, Object> before = commentRow(fixture.comment());
        Map<String, Object> childBefore = commentRow(fixture.child());
        Map<String, Object> postBefore = postRow(fixture.post());
        Map<String, Object> caseBefore = caseRow(fixture.caseId());
        JsonNode treeBefore = publicTree(fixture.post());
        Hold restoreHold = probeDirectMutation(fixture, fixture.first());
        Hold report = probeReportMutation(fixture, fixture.second());
        holdAudit(fixture, fixture.first(), "COMMENT", fixture.comment(), "VISIBLE", restoreHold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> restore = submitDirect(workers, "held-deleted-restore", fixture.first(), fixture,
                    "RESTORE", FIRST_REASON);
            await(restoreHold.ready, "real RESTORE UPDATE and flushed COMMENT audit INSERT before commit");
            CompletableFuture<MvcResult> action = submitCase(workers, "case-during-restore", fixture.second(), fixture, "HIDE");
            // The committed DELETED tuple fails the report-decision predicate before PostgreSQL attempts a target row lock.
            assertCaseResponse(result(action), fixture, fixture.second(), "DELETED");
            assertThat(report.affected.get()).isZero();
            assertThat(restoreHold.release.getCount()).isEqualTo(1);
            assertThat(commentRow(fixture.comment())).isEqualTo(before);
            assertThat(publicTree(fixture.post())).isEqualTo(treeBefore);
            assertCommentAudits(fixture, List.of());
            assertCaseAudit(fixture, fixture.second(), "HIDE");
            assertPreserved(caseBefore, caseRow(fixture.caseId()), Set.of("status", "handled_by", "handled_at", "decision_note"));
            restoreHold.release.countDown();
            assertDirectResponse(result(restore), fixture, "VISIBLE", fixture.first().id(), FIRST_REASON, CONTENT);
            assertModerationOnly(before, commentRow(fixture.comment()), "VISIBLE", fixture.first().id(), FIRST_REASON);
            assertThat(commentRow(fixture.child())).isEqualTo(childBefore);
            assertThat(postRow(fixture.post())).isEqualTo(postBefore);
            JsonNode restoredTree = publicTree(fixture.post());
            JsonNode restored = commentIn(restoredTree, fixture.comment());
            assertThat(restored.path("content").asText()).isEqualTo(CONTENT);
            assertThat(Instant.parse(restored.path("editedAt").asText())).isEqualTo(CREATED.plusSeconds(1));
            assertThat(commentIn(restoredTree, fixture.child())).isEqualTo(commentIn(treeBefore, fixture.child()));
            assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), "RESTORE", "DELETED", "VISIBLE", FIRST_REASON)));
            assertCaseAudit(fixture, fixture.second(), "HIDE");
            assertDirectSql("held-deleted-restore", true);
            assertThat(commentUpdates("case-during-restore")).hasSize(1);
            assertThat(capture.statements("case-during-restore")).noneMatch(CommentModerationConcurrencyIntegrationTest::explicitLock);
            assertThat(restoreHold.audited.get()).isEqualTo(restoreHold.mutated.get());
            System.out.println("COMMENT_MODERATION_UNCOMMITTED_RESTORE_EVIDENCE conditionalTargetUpdateRows=0"
                    + " caseCommittedBeforeRestore=true finalVisible=true exactAudits=true contentTimestampPreserved=true");
        } finally {
            restoreHold.release.countDown();
            stop(workers);
        }
    }

    @Test
    void reportActionOnAnAlreadyDeletedCommentKeepsItsCaseAuditWithoutLockingOutDirectRestore() throws Exception {
        Fixture fixture = fixture(true, true);
        seedState(fixture, "DELETED", fixture.second().id(), OLD_REASON);
        Map<String, Object> before = commentRow(fixture.comment());
        Map<String, Object> caseBefore = caseRow(fixture.caseId());
        Hold hold = probeReportMutation(fixture, fixture.second());
        holdAudit(fixture, fixture.second(), "REPORT_CASE", fixture.caseId(), "DELETED", hold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> report = submitCase(workers, "deleted-case-noop", fixture.second(), fixture, "HIDE");
            await(hold.ready, "real no-op target UPDATE and flushed case audit INSERT before commit");
            assertThat(hold.affected.get()).isZero();
            CompletableFuture<MvcResult> restore = submitDirect(workers, "restore-during-case-noop", fixture.first(), fixture,
                    "RESTORE", FIRST_REASON);
            // A zero-row conditional UPDATE has no target row lock: RESTORE commits while the case is still held.
            assertDirectResponse(result(restore), fixture, "VISIBLE", fixture.first().id(), FIRST_REASON, CONTENT);
            assertThat(hold.release.getCount()).isEqualTo(1);
            assertThat(caseRow(fixture.caseId())).isEqualTo(caseBefore);
            assertThat(auditRows("REPORT_CASE", fixture.caseId())).isEmpty();
            assertModerationOnly(before, commentRow(fixture.comment()), "VISIBLE", fixture.first().id(), FIRST_REASON);
            hold.release.countDown();
            assertCaseResponse(result(report), fixture, fixture.second(), "VISIBLE");
            assertCaseAudit(fixture, fixture.second(), "HIDE");
            assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), "RESTORE", "DELETED", "VISIBLE", FIRST_REASON)));
            assertDirectSql("restore-during-case-noop", true);
            assertThat(commentUpdates("deleted-case-noop")).hasSize(1);
            assertThat(hold.audited.get()).isEqualTo(hold.mutated.get());
            System.out.println("COMMENT_MODERATION_CASE_NOOP_EVIDENCE actualTargetUpdates=0"
                    + " restoreCommittedBeforeCaseCommit=true reportCaseAudits=1 commentAudits=1");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "SQL failure after actual UPDATE/audit INSERT, waiting contender={0}")
    @ValueSource(booleans = {false, true})
    void realSqlFailureRollsBackCommentAndAuditTogetherAndReleasesTheWaitingContender(boolean contender)
            throws Exception {
        Fixture fixture = fixture(true, false);
        Map<String, Object> before = commentRow(fixture.comment());
        Hold hold = probeDirectMutation(fixture, fixture.first());
        holdAudit(fixture, fixture.first(), "COMMENT", fixture.comment(), "HIDDEN", hold, true);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> failing = submitDirect(workers, "audit-fail", fixture.first(), fixture,
                    "HIDE", FIRST_REASON);
            await(hold.ready, "actual target UPDATE and flushed audit INSERT share a transaction");
            assertThat(commentRow(fixture.comment())).isEqualTo(before);
            assertThat(auditRows("COMMENT", fixture.comment())).isEmpty();
            CompletableFuture<MvcResult> waiting = null;
            if (contender) {
                waiting = submitDirect(workers, "after-rollback", fixture.second(), fixture, "DELETE", SECOND_REASON);
                awaitBlocked(hold.mutated.get().pid(), "%community.comments%for%update%");
            }
            hold.release.countDown();
            assertError(result(failing), 500, "INTERNAL_ERROR");
            assertThat(hold.audited.get()).isEqualTo(hold.mutated.get());
            assertThat(hold.mutated.get().isolation()).isEqualTo("read committed");
            if (contender) {
                assertDirectResponse(result(waiting), fixture, "DELETED", fixture.second().id(), SECOND_REASON, CONTENT);
                assertModerationOnly(before, commentRow(fixture.comment()), "DELETED", fixture.second().id(), SECOND_REASON);
                assertCommentAudits(fixture, List.of(expectedAudit(fixture.second(), "DELETE", "VISIBLE", "DELETED", SECOND_REASON)));
                assertDirectSql("after-rollback", true);
            } else {
                assertThat(commentRow(fixture.comment())).isEqualTo(before);
                assertCommentAudits(fixture, List.of());
            }
            assertDirectSql("audit-fail", true);
            System.out.println("COMMENT_MODERATION_ROLLBACK_EVIDENCE targetAndAuditSamePidAndXid=true"
                    + " actualAuditInsert=true realSqlFailure=true waitingContender=" + contender);
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "moderation {0} and RESTORE preserve content timestamp, edited={1}")
    @CsvSource({"HIDE,false", "HIDE,true", "DELETE,false", "DELETE,true"})
    void hideDeleteAndRestorePreserveRawContentTimestampsRelationshipsAndPublicEditedAt(String action,
            boolean edited) throws Exception {
        Fixture fixture = fixture(edited, false);
        Map<String, Object> before = commentRow(fixture.comment());
        Map<String, Object> childBefore = commentRow(fixture.child());
        Map<String, Object> postBefore = postRow(fixture.post());
        JsonNode treeBefore = publicTree(fixture.post());
        JsonNode parentBefore = commentIn(treeBefore, fixture.comment());
        if (edited) assertThat(Instant.parse(parentBefore.path("editedAt").asText())).isEqualTo(CREATED.plusSeconds(1));
        else assertThat(parentBefore.path("editedAt").isNull()).isTrue();
        String hiddenState = action.equals("HIDE") ? "HIDDEN" : "DELETED";
        assertDirectResponse(direct("content-hide", fixture.first(), fixture, action, FIRST_REASON), fixture,
                hiddenState, fixture.first().id(), FIRST_REASON, CONTENT);
        assertModerationOnly(before, commentRow(fixture.comment()), hiddenState, fixture.first().id(), FIRST_REASON);
        JsonNode hiddenTree = publicTree(fixture.post());
        JsonNode placeholder = commentIn(hiddenTree, fixture.comment());
        assertThat(placeholder.path("status").asText()).isEqualTo(hiddenState);
        assertThat(placeholder.path("content").isNull()).isTrue();
        assertThat(placeholder.path("author").isNull()).isTrue();
        assertThat(placeholder.path("editedAt").isNull()).isTrue();
        assertThat(commentIn(hiddenTree, fixture.child())).isEqualTo(commentIn(treeBefore, fixture.child()));
        assertDirectResponse(direct("content-restore", fixture.second(), fixture, "RESTORE", SECOND_REASON), fixture,
                "VISIBLE", fixture.second().id(), SECOND_REASON, CONTENT);
        assertModerationOnly(before, commentRow(fixture.comment()), "VISIBLE", fixture.second().id(), SECOND_REASON);
        assertThat(commentRow(fixture.child())).isEqualTo(childBefore);
        assertThat(postRow(fixture.post())).isEqualTo(postBefore);
        assertThat(publicTree(fixture.post())).isEqualTo(treeBefore);
        assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), action, "VISIBLE", hiddenState, FIRST_REASON),
                expectedAudit(fixture.second(), "RESTORE", hiddenState, "VISIBLE", SECOND_REASON)));
        assertDirectSql("content-hide", true);
        assertDirectSql("content-restore", true);
    }

    @ParameterizedTest(name = "no-op {0}/{1} issues no UPDATE and preserves xmin")
    @CsvSource({"VISIBLE,RESTORE", "HIDDEN,HIDE", "DELETED,HIDE", "DELETED,DELETE"})
    void noOpsPreserveTheEntireRowIncludingXminAndIssueNoUpdateOrAudit(String initial, String action)
            throws Exception {
        Fixture fixture = fixture(true, false);
        Long moderator = initial.equals("VISIBLE") ? null : fixture.second().id();
        String reason = initial.equals("VISIBLE") ? null : OLD_REASON;
        seedState(fixture, initial, moderator, reason);
        Map<String, Object> before = commentRow(fixture.comment());
        assertDirectResponse(direct("noop", fixture.first(), fixture, action, FIRST_REASON), fixture,
                initial, moderator, reason, CONTENT);
        assertThat(commentRow(fixture.comment())).isEqualTo(before);
        assertCommentAudits(fixture, List.of());
        assertDirectSql("noop", false);
    }

    @Test
    void realAuthorDeletionCannotBeRewrittenOrRestoredByTheModerationSequence() throws Exception {
        Fixture fixture = fixture(false, false);
        MvcResult deleted = authorMutation("author-delete", fixture, true);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        Map<String, Object> before = commentRow(fixture.comment());
        assertThat(before.get("status")).isEqualTo("DELETED");
        assertThat(numberOrNull(before.get("moderated_by"))).isEqualTo(fixture.author().id());
        assertThat(before.get("moderation_reason")).isEqualTo("AUTHOR_DELETED");
        for (String action : List.of("HIDE", "DELETE", "RESTORE", "HIDE", "DELETE")) {
            MvcResult response = direct("author-marker-" + action, fixture.first(), fixture, action, FIRST_REASON);
            if (action.equals("RESTORE")) assertError(response, 409, "COMMENT_NOT_RESTORABLE");
            else assertDirectResponse(response, fixture, "DELETED", fixture.author().id(), "AUTHOR_DELETED", CONTENT);
            assertThat(commentRow(fixture.comment())).isEqualTo(before);
            assertDirectSql("author-marker-" + action, false);
        }
        assertCommentAudits(fixture, List.of());
    }

    @ParameterizedTest(name = "legacy AUTHOR_DELETED marker with null actor={0} remains restorable")
    @ValueSource(booleans = {false, true})
    void legacyAuthorDeletedReasonWithoutTheMatchingAuthorActorCanBeRestored(boolean nullActor) throws Exception {
        Fixture fixture = fixture(false, false);
        seedState(fixture, "DELETED", nullActor ? null : fixture.second().id(), "AUTHOR_DELETED");
        Map<String, Object> before = commentRow(fixture.comment());
        assertDirectResponse(direct("legacy-restore", fixture.first(), fixture, "RESTORE", FIRST_REASON), fixture,
                "VISIBLE", fixture.first().id(), FIRST_REASON, CONTENT);
        assertModerationOnly(before, commentRow(fixture.comment()), "VISIBLE", fixture.first().id(), FIRST_REASON);
        assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), "RESTORE", "DELETED", "VISIBLE", FIRST_REASON)));
        assertDirectSql("legacy-restore", true);
    }

    @ParameterizedTest(name = "author delete={0}, direct moderation first={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void authorMutationsAndDirectModerationRespectBothLockOrdersWithoutResurrection(boolean authorDelete,
            boolean directFirst) throws Exception {
        Fixture fixture = fixture(false, false);
        Map<String, Object> before = commentRow(fixture.comment());
        Map<String, Object> postBefore = postRow(fixture.post());
        String action = authorDelete ? "RESTORE" : "HIDE";
        Hold hold = directFirst ? holdDirectLock(fixture) : holdAuthorLock(fixture);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = directFirst
                    ? submitDirect(workers, "direct-author-race", fixture.first(), fixture, action, FIRST_REASON)
                    : submitAuthor(workers, "author-race", fixture, authorDelete);
            await(hold.ready, "first actual comment lock before author/direct mutation");
            CompletableFuture<MvcResult> second = directFirst
                    ? submitAuthor(workers, "author-race", fixture, authorDelete)
                    : submitDirect(workers, "direct-author-race", fixture.first(), fixture, action, FIRST_REASON);
            awaitBlocked(hold.locked.get().pid(), "%community.comments%for%update%");
            hold.release.countDown();
            MvcResult firstResult = result(first);
            MvcResult secondResult = result(second);
            MvcResult authorResult = directFirst ? secondResult : firstResult;
            MvcResult directResult = directFirst ? firstResult : secondResult;
            Map<String, Object> saved = commentRow(fixture.comment());
            if (authorDelete) {
                assertThat(authorResult.getResponse().getStatus()).isEqualTo(204);
                if (directFirst) assertDirectResponse(directResult, fixture, "VISIBLE", null, null, CONTENT);
                else assertError(directResult, 409, "COMMENT_NOT_RESTORABLE");
                assertThat(saved.get("status")).isEqualTo("DELETED");
                assertThat(saved.get("moderation_reason")).isEqualTo("AUTHOR_DELETED");
                assertThat(numberOrNull(saved.get("moderated_by"))).isEqualTo(fixture.author().id());
                assertThat(saved.get("content")).isEqualTo(CONTENT);
                assertThat(saved.get("updated_at")).isEqualTo(Timestamp.from(REQUEST_TIME));
                assertCommentAudits(fixture, List.of());
                assertDirectSql("direct-author-race", false);
            } else {
                if (directFirst) assertError(authorResult, 409, "COMMENT_NOT_EDITABLE");
                else {
                    assertThat(authorResult.getResponse().getStatus()).isEqualTo(200);
                    assertThat(body(authorResult).path("content").asText()).isEqualTo(EDITED_CONTENT);
                    assertThat(Instant.parse(body(authorResult).path("editedAt").asText())).isEqualTo(REQUEST_TIME);
                }
                assertDirectResponse(directResult, fixture, "HIDDEN", fixture.first().id(), FIRST_REASON,
                        directFirst ? CONTENT : EDITED_CONTENT);
                assertThat(saved.get("status")).isEqualTo("HIDDEN");
                assertThat(saved.get("content")).isEqualTo(directFirst ? CONTENT : EDITED_CONTENT);
                assertThat(saved.get("updated_at")).isEqualTo(directFirst ? before.get("updated_at") : Timestamp.from(REQUEST_TIME));
                assertCommentAudits(fixture, List.of(expectedAudit(fixture.first(), "HIDE", "VISIBLE", "HIDDEN", FIRST_REASON)));
                assertDirectSql("direct-author-race", true);
            }
            assertThat(saved.get("created_at")).isEqualTo(before.get("created_at"));
            assertThat(saved.get("parent_id")).isEqualTo(before.get("parent_id"));
            assertThat(saved.get("depth")).isEqualTo(before.get("depth"));
            assertThat(postRow(fixture.post())).isEqualTo(postBefore);
            assertAuthorLockOrder("author-race");
            System.out.println("COMMENT_MODERATION_AUTHOR_RACE_EVIDENCE directFirst=" + directFirst
                    + " authorDelete=" + authorDelete + " actualRowLockWait=true resurrection=false deadlock=false");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    private Hold holdDirectLock(Fixture fixture) {
        Hold hold = new Hold();
        doAnswer(invocation -> {
            Object found = delegate(invocation);
            // The second request cannot enter this answer until the first real lock has committed.
            if (hold.locked.compareAndSet(null, identity())) {
                hold.ready.countDown();
                await(hold.release, "release first real comment SELECT FOR UPDATE");
            }
            return found;
        }).when(comments).findByIdForUpdate(eq(fixture.comment()));
        return hold;
    }

    private Hold holdAuthorLock(Fixture fixture) {
        Hold hold = new Hold();
        doAnswer(invocation -> {
            Object found = delegate(invocation);
            hold.locked.set(identity());
            hold.ready.countDown();
            await(hold.release, "release author's real post then comment locks");
            return found;
        }).when(comments).findOwnedForUpdate(eq(fixture.comment()), eq(fixture.author().id()), eq(fixture.post()));
        return hold;
    }

    private Hold probeDirectMutation(Fixture fixture, Account moderator) {
        Hold hold = new Hold();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            assertThat(affected).isEqualTo(1);
            hold.affected.set((Integer) affected);
            hold.mutated.set(identity());
            assertThat(hold.mutated.get().isolation()).isEqualTo("read committed");
            return affected;
        }).when(comments).moderateDirect(eq(fixture.comment()), anyString(), eq(moderator.id()), anyString());
        return hold;
    }

    private Hold probeReportMutation(Fixture fixture, Account moderator) {
        Hold hold = new Hold();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            assertThat((Integer) affected).isBetween(0, 1);
            hold.affected.set((Integer) affected);
            hold.mutated.set(identity());
            assertThat(hold.mutated.get().isolation()).isEqualTo("read committed");
            return affected;
        }).when(comments).moderateForReport(eq(fixture.comment()), anyString(), eq(moderator.id()), anyString());
        return hold;
    }

    private void holdAudit(Fixture fixture, Account moderator, String type, long target, String expectedStatus,
            Hold hold, boolean fail) {
        doAnswer(invocation -> {
            assertThat(identity()).isEqualTo(hold.mutated.get());
            invocation.callRealMethod();
            entityManager.flush();
            hold.audited.set(identity());
            assertThat(hold.audited.get()).isEqualTo(hold.mutated.get());
            assertThat(((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()")
                    .getSingleResult()).intValue()).isEqualTo(hold.mutated.get().pid());
            assertThat(commentRow(fixture.comment()).get("status")).isEqualTo(expectedStatus);
            assertThat(auditRows(type, target)).hasSize(1);
            hold.ready.countDown();
            await(hold.release, "release actual comment mutation and flushed audit INSERT before commit");
            System.out.println("COMMENT_MODERATION_TRANSACTION_EVIDENCE backendPid=" + hold.mutated.get().pid()
                    + " transactionId=" + hold.mutated.get().transactionId() + " targetAndAuditSameTransaction=true");
            if (fail) jdbc.execute("SELECT 1 / 0");
            return null;
        }).when(audit).recordDetail(eq(Long.valueOf(moderator.id())), anyString(), eq(type), eq(target), any());
    }

    private int awaitBlocked(int holderPid, String queryPattern) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> waiters = jdbc.queryForList("""
                    SELECT a.pid FROM pg_stat_activity a
                    WHERE a.datname=current_database() AND a.backend_type='client backend'
                      AND ?=ANY(pg_blocking_pids(a.pid)) AND a.query ILIKE ?
                      AND EXISTS (SELECT 1 FROM pg_locks l WHERE l.pid=a.pid
                        AND NOT l.granted AND l.locktype='transactionid')
                    """, Integer.class, holderPid, queryPattern);
            if (waiters.size() == 1) {
                assertThat(waiters.get(0)).isNotEqualTo(holderPid);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE pid IN (?, ?) "
                        + "AND locktype='advisory'", Integer.class, holderPid, waiters.get(0))).isZero();
                System.out.println("COMMENT_MODERATION_LOCK_EVIDENCE holderPid=" + holderPid
                        + " waiterPid=" + waiters.get(0) + " ungrantedTransactionIdWait=true");
                return waiters.get(0);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing PostgreSQL comment row lock", exception);
            }
        }
        throw new AssertionError("No actual transactionid waiter for " + queryPattern + " within " + TIMEOUT);
    }

    private Fixture fixture(boolean edited, boolean reportCase) throws Exception {
        Account author = account(false, true);
        Account first = account(true, false);
        Account second = account(true, false);
        long[] ids = transaction().execute(status -> {
            long postId = jdbc.queryForObject("""
                    INSERT INTO community.posts(author_id,category,title,content,status,published_at,created_at,updated_at)
                    VALUES (?,'QUESTION','Comment moderation concurrency fixture','Synthetic post','APPROVED',?,?,?) RETURNING id
                    """, Long.class, author.id(), Timestamp.from(CREATED), Timestamp.from(CREATED), Timestamp.from(CREATED));
            long commentId = jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content,created_at,updated_at) "
                    + "VALUES (?,?,?,?,?) RETURNING id", Long.class, postId, author.id(), CONTENT,
                    Timestamp.from(CREATED), Timestamp.from(edited ? CREATED.plusSeconds(1) : CREATED));
            long child = jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,created_at,updated_at) "
                    + "VALUES (?,?,?,'Synthetic visible reply',1,?,?) RETURNING id", Long.class, postId, commentId,
                    author.id(), Timestamp.from(CREATED.plusSeconds(2)), Timestamp.from(CREATED.plusSeconds(2)));
            return new long[] {postId, commentId, child};
        });
        long caseId = 0;
        if (reportCase) {
            Account reporter = account(false, true);
            MvcResult reported = mvc.perform(post("/api/community/reports")
                    .header("Authorization", "Bearer " + reporter.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("targetType", "COMMENT", "targetId", ids[1],
                            "reason", "SPAM", "detail", "Synthetic report")))).andReturn();
            assertThat(reported.getResponse().getStatus()).isEqualTo(201);
            caseId = jdbc.queryForObject("SELECT id FROM community.moderation_cases "
                    + "WHERE target_type='COMMENT' AND target_id=? AND status='PENDING'", Long.class, ids[1]);
        }
        return new Fixture(author, first, second, ids[0], ids[1], ids[2], caseId);
    }

    private Account account(boolean moderator, boolean verified) throws Exception {
        String email = "comment-moderation-" + UUID.randomUUID() + "@example.test";
        String password = "Moderation" + UUID.randomUUID() + "A9";
        String name = "Synthetic Comment Moderator " + UUID.randomUUID();
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password,
                        "confirmPassword", password, "displayName", name, "acceptTerms", true)))).andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        long id = body(registered).path("userId").asLong();
        assertThat(id).isPositive();
        if (verified) {
            JsonNode verification = null;
            for (JsonNode message : mailpit("/api/v1/messages").path("messages")) {
                if (message.toString().contains(email)) {
                    verification = mailpit("/api/v1/message/" + message.path("ID").asText());
                    break;
                }
            }
            assertThat(verification).as("real verification email delivered to Mailpit").isNotNull();
            Matcher token = VERIFY_LINK.matcher(verification.path("Text").asText());
            assertThat(token.find()).isTrue();
            MvcResult response = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("token", token.group(1))))).andReturn();
            assertThat(response.getResponse().getStatus()).isEqualTo(200);
        }
        if (moderator) transaction().executeWithoutResult(status -> jdbc.update(
                "INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", id));
        return new Account(id, jwt.issueAccessToken(id, List.of(moderator ? "MODERATOR" : "LEARNER"), verified,
                UUID.randomUUID()), name);
    }

    private void seedState(Fixture fixture, String status, Long moderator, String reason) {
        transaction().executeWithoutResult(tx -> assertThat(jdbc.update("UPDATE community.comments "
                + "SET status=?,moderated_by=?,moderation_reason=? WHERE id=?", status, moderator, reason,
                fixture.comment())).isEqualTo(1));
    }

    private MvcResult direct(String key, Account moderator, Fixture fixture, String action, String reason) throws Exception {
        capture.start(key);
        try {
            return mvc.perform(patch("/api/community/moderation/comments/" + fixture.comment())
                    .header("Authorization", "Bearer " + moderator.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("action", action, "reason", " \u2003" + reason + "\u2003 "))))
                    .andReturn();
        } finally {
            capture.stop();
        }
    }

    private CompletableFuture<MvcResult> submitDirect(ExecutorService workers, String key, Account moderator,
            Fixture fixture, String action, String reason) {
        return submit(workers, () -> direct(key, moderator, fixture, action, reason));
    }

    private CompletableFuture<MvcResult> submitCase(ExecutorService workers, String key, Account moderator,
            Fixture fixture, String action) {
        return submit(workers, () -> {
            capture.start(key);
            try {
                return mvc.perform(patch("/api/community/moderation/reports/" + fixture.caseId())
                        .header("Authorization", "Bearer " + moderator.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "ACTION", "note", CASE_NOTE,
                                "commentAction", action)))).andReturn();
            } finally {
                capture.stop();
            }
        });
    }

    private CompletableFuture<MvcResult> submitAuthor(ExecutorService workers, String key, Fixture fixture, boolean deleting) {
        return submit(workers, () -> authorMutation(key, fixture, deleting));
    }

    private MvcResult authorMutation(String key, Fixture fixture, boolean deleting) throws Exception {
        capture.start(key);
        try {
            var request = deleting ? delete("/api/community/comments/" + fixture.comment())
                    : put("/api/community/comments/" + fixture.comment()).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("content", EDITED_CONTENT)));
            return mvc.perform(request.header("Authorization", "Bearer " + fixture.author().token())).andReturn();
        } finally {
            capture.stop();
        }
    }

    private void assertDirectResponse(MvcResult response, Fixture fixture, String status, Long moderator,
            String reason, String content) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(response);
        assertThat(json.path("id").asLong()).isEqualTo(fixture.comment());
        assertThat(json.path("postId").asLong()).isEqualTo(fixture.post());
        assertThat(json.path("parentId").isNull()).isTrue();
        assertThat(json.path("depth").asInt()).isZero();
        assertThat(json.path("status").asText()).isEqualTo(status);
        assertThat(json.path("content").asText()).isEqualTo(content);
        assertThat(json.path("author").path("id").asLong()).isEqualTo(fixture.author().id());
        assertThat(json.path("author").path("displayName").asText()).isEqualTo(fixture.author().name());
        assertThat(Instant.parse(json.path("createdAt").asText())).isEqualTo(CREATED);
        if (moderator == null) assertThat(json.path("moderatedBy").isNull()).isTrue();
        else {
            assertThat(json.path("moderatedBy").path("id").asLong()).isEqualTo(moderator);
            Account actor = List.of(fixture.author(), fixture.first(), fixture.second()).stream()
                    .filter(account -> account.id() == moderator).findFirst().orElseThrow();
            assertThat(json.path("moderatedBy").path("displayName").asText()).isEqualTo(actor.name());
        }
        if (reason == null) assertThat(json.path("moderationReason").isNull()).isTrue();
        else assertThat(json.path("moderationReason").asText()).isEqualTo(reason);
    }

    private void assertCaseResponse(MvcResult response, Fixture fixture, Account moderator, String targetState)
            throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(response);
        assertThat(json.path("id").asLong()).isEqualTo(fixture.caseId());
        assertThat(json.path("status").asText()).isEqualTo("ACTIONED");
        assertThat(json.path("handledBy").path("id").asLong()).isEqualTo(moderator.id());
        assertThat(json.path("decisionNote").asText()).isEqualTo(CASE_NOTE);
        assertThat(json.path("target").path("id").asLong()).isEqualTo(fixture.comment());
        assertThat(json.path("target").path("status").asText()).isEqualTo(targetState);
        Map<String, Object> row = caseRow(fixture.caseId());
        assertThat(row.get("status")).isEqualTo("ACTIONED");
        assertThat(numberOrNull(row.get("handled_by"))).isEqualTo(moderator.id());
        assertThat(row.get("handled_at")).isEqualTo(Timestamp.from(REQUEST_TIME));
        assertThat(row.get("decision_note")).isEqualTo(CASE_NOTE);
    }

    private void assertModerationOnly(Map<String, Object> before, Map<String, Object> after, String state,
            long moderator, String reason) {
        assertThat(after.get("status")).isEqualTo(state);
        assertThat(numberOrNull(after.get("moderated_by"))).isEqualTo(moderator);
        assertThat(after.get("moderation_reason")).isEqualTo(reason);
        assertPreserved(before, after, MODERATION_FIELDS);
        assertThat(after.get("row_version")).isNotEqualTo(before.get("row_version"));
    }

    private void assertCommentAudits(Fixture fixture, List<ExpectedAudit> expected) throws Exception {
        List<Map<String, Object>> rows = auditRows("COMMENT", fixture.comment());
        assertThat(rows).hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            ExpectedAudit entry = expected.get(index);
            Map<String, Object> row = rows.get(index);
            assertThat(numberOrNull(row.get("actor_id"))).isEqualTo(entry.actor());
            assertThat(row.get("action")).isEqualTo("COMMUNITY_COMMENT_" + entry.action());
            assertThat(row.get("target_type")).isEqualTo("COMMENT");
            assertThat(numberOrNull(row.get("target_id"))).isEqualTo(fixture.comment());
            assertThat(mapper.readTree(row.get("detail").toString())).isEqualTo(mapper.valueToTree(Map.of(
                    "before", entry.before(), "after", entry.after(), "reason", entry.reason())));
        }
    }

    private void assertCaseAudit(Fixture fixture, Account moderator, String action) throws Exception {
        List<Map<String, Object>> rows = auditRows("REPORT_CASE", fixture.caseId());
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(numberOrNull(row.get("actor_id"))).isEqualTo(moderator.id());
        assertThat(row.get("action")).isEqualTo("COMMUNITY_REPORT_ACTION");
        assertThat(mapper.readTree(row.get("detail").toString())).isEqualTo(mapper.readTree(mapper.writeValueAsString(Map.of(
                "targetType", "COMMENT", "targetId", fixture.comment(), "commentAction", action))));
    }

    private void assertDirectSql(String key, boolean mutation) {
        List<String> statements = capture.statements(key);
        assertThat(statements).anyMatch(sql -> sql.contains("community.comments") && explicitLock(sql));
        assertThat(statements).noneMatch(sql -> sql.contains("community.posts") && explicitLock(sql));
        assertThat(statements).noneMatch(sql -> sql.contains("pg_advisory"));
        List<String> updates = commentUpdates(key);
        assertThat(updates).hasSize(mutation ? 1 : 0);
        if (mutation) {
            String sql = updates.get(0).replaceAll("\\s+", "");
            assertThat(sql).contains("setstatus=", "moderated_by=", "moderation_reason=", "whereid=")
                    .doesNotContain("updated_at", "created_at", "content=", "parent_id=", "post_id=", "author_id=", "depth=", "version");
        }
        assertThat(statements.stream().filter(sql -> sql.startsWith("insert into shared.audit_logs")).count())
                .isEqualTo(mutation ? 1 : 0);
    }

    private List<String> commentUpdates(String key) {
        return capture.statements(key).stream().filter(sql -> sql.startsWith("update community.comments ")).toList();
    }

    private void assertAuthorLockOrder(String key) {
        List<String> statements = capture.statements(key);
        List<String> locks = statements.stream().filter(CommentModerationConcurrencyIntegrationTest::explicitLock).toList();
        assertThat(locks).hasSize(2);
        assertThat(locks.get(0)).contains("community.posts");
        assertThat(locks.get(1)).contains("community.comments");
        assertThat(statements.indexOf(locks.get(0))).isLessThan(statements.indexOf(locks.get(1)));
    }

    private JsonNode publicTree(long postId) throws Exception {
        MvcResult response = mvc.perform(get("/api/public/community/posts/" + postId + "/comments")).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        return body(response);
    }

    private static JsonNode commentIn(JsonNode tree, long id) {
        for (JsonNode comment : tree) if (comment.path("id").asLong() == id) return comment;
        throw new AssertionError("Expected retained public comment " + id);
    }

    private Map<String, Object> commentRow(long id) {
        return jdbc.queryForMap("SELECT *,xmin::text AS row_version FROM community.comments WHERE id=?", id);
    }
    private Map<String, Object> postRow(long id) { return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id); }
    private Map<String, Object> caseRow(long id) { return jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", id); }
    private List<Map<String, Object>> auditRows(String type, long id) {
        return jdbc.queryForList("SELECT actor_id,action,target_type,target_id,detail::text AS detail "
                + "FROM shared.audit_logs WHERE target_type=? AND target_id=? ORDER BY id", type, id);
    }

    private TransactionIdentity identity() {
        return new TransactionIdentity(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class),
                jdbc.queryForObject("SELECT pg_current_xact_id()::text", String.class),
                jdbc.queryForObject("SHOW transaction_isolation", String.class));
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout((int) TIMEOUT.toSeconds() + 5);
        return template;
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(mailpitUrl + path))
                .timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private void assertError(MvcResult response, int status, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(status);
        JsonNode json = body(response);
        assertThat(json.path("error_code").asText()).isEqualTo(code);
        assertThat(json.path("message").asText()).isNotBlank();
        assertThat(json.path("request_id").asText()).isNotBlank();
        assertThat(json.has("trace")).isFalse();
        assertThat(response.getResponse().getContentAsString()).doesNotContain(
                "org.hibernate", "PSQLException", "ck_cmt_", "stackTrace", "division by zero");
    }

    private JsonNode body(MvcResult response) throws Exception {
        return mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Object delegate(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
    private static Long numberOrNull(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static boolean explicitLock(String sql) {
        return sql.contains("for update") || sql.contains("for no key update")
                || sql.contains("for share") || sql.contains("for key share");
    }
    private static String directAfter(String before, String action) {
        return switch (action) {
            case "HIDE" -> before.equals("VISIBLE") ? "HIDDEN" : before;
            case "DELETE" -> "DELETED";
            case "RESTORE" -> "VISIBLE";
            default -> throw new IllegalArgumentException("Unexpected synthetic action");
        };
    }
    private static String reportAfter(String before, String action) {
        if (before.equals("DELETED")) return before;
        return action.equals("DELETE") ? "DELETED" : "HIDDEN";
    }
    private static ExpectedAudit expectedAudit(Account actor, String action, String before, String after, String reason) {
        return new ExpectedAudit(actor.id(), action, before, after, reason);
    }
    private static void assertPreserved(Map<String, Object> before, Map<String, Object> after, Set<String> changed) {
        before.forEach((field, value) -> {
            if (!changed.contains(field)) assertThat(after.get(field)).as("preserved field %s", field).isEqualTo(value);
        });
    }
    private static CompletableFuture<MvcResult> submit(ExecutorService workers, Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request.perform();
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent comment moderation HTTP request failed", exception);
            }
        }, workers);
    }
    private static void await(CountDownLatch latch, String description) {
        try {
            assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as(description).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting " + description, exception);
        }
    }
    private static <T> T result(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent HTTP request exceeded bounded timeout", exception);
        }
    }
    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface private interface Request { MvcResult perform() throws Exception; }
    private record Account(long id, String token, String name) { }
    private record Fixture(Account author, Account first, Account second, long post, long comment, long child, long caseId) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }
    private record ExpectedAudit(long actor, String action, String before, String after, String reason) { }
    private static final class Hold {
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<TransactionIdentity> locked = new AtomicReference<>();
        private final AtomicReference<TransactionIdentity> mutated = new AtomicReference<>();
        private final AtomicReference<TransactionIdentity> audited = new AtomicReference<>();
        private final AtomicReference<Integer> affected = new AtomicReference<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModerationConfiguration {
        @Bean @Primary Clock commentModerationConcurrencyClock() { return Clock.fixed(REQUEST_TIME, ZoneOffset.UTC); }
        @Bean ModerationSqlCapture commentModerationSqlCapture() { return new ModerationSqlCapture(); }
        @Bean HibernatePropertiesCustomizer commentModerationStatementInspector(ModerationSqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static final class ModerationSqlCapture implements StatementInspector {
        private final ThreadLocal<String> active = new ThreadLocal<>();
        private final Map<String, List<String>> statements = new ConcurrentHashMap<>();
        void start(String key) {
            statements.put(key, new CopyOnWriteArrayList<>());
            active.set(key);
        }
        void stop() { active.remove(); }
        List<String> statements(String key) { return List.copyOf(statements.getOrDefault(key, List.of())); }
        @Override public String inspect(String sql) {
            String key = active.get();
            if (key != null) statements.get(key).add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
