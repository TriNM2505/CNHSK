package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
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

/** Real PostgreSQL decisions and report upserts with committed fixtures and independent HTTP transactions. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReportCaseConcurrencyIntegrationTest.CaseConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=6",
        "spring.datasource.hikari.minimum-idle=6",
        "spring.datasource.hikari.connection-timeout=20000"
})
class ReportCaseConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Instant SAVED_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Instant REQUEST_TIME = SAVED_TIME.plusNanos(987);
    private static final Instant CREATED = SAVED_TIME.minusSeconds(300);
    private static final String NOTE = "Synthetic case decision 中文 😀";
    private static final String CONTENT = "Synthetic reported parent content";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CaseSqlCapture capture;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;
    @PersistenceContext private EntityManager entityManager;
    @MockitoSpyBean private ModerationCaseRepository cases;
    @MockitoSpyBean private PostRepository posts;
    @MockitoSpyBean private CommentRepository comments;
    @MockitoSpyBean private AuditService audit;

    @ParameterizedTest(name = "{0} case winner ACTION={1}")
    @CsvSource({"POST,true", "POST,false", "COMMENT,true", "COMMENT,false"})
    void competingModeratorsCommitOneDecisionOneTargetStateAndOneAudit(String type, boolean action)
            throws Exception {
        Fixture fixture = fixture(type, false);
        Map<String, Object> beforeTarget = targetRow(fixture);
        Hold hold = probeClose(fixture, fixture.first());
        probeTarget(fixture, fixture.first(), hold);
        holdAudit(fixture, fixture.first(), hold, false);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> winner = submitDecision(workers, "winner", fixture.first(), fixture, action, "HIDE");
            await(hold.ready, "real case close, target mutation if ACTION and real audit INSERT");
            CompletableFuture<MvcResult> loser = submitDecision(workers, "loser", fixture.second(), fixture, !action, "HIDE");
            awaitBlocked(hold.closed.get().pid(), "%update%community.moderation_cases%");
            hold.release.countDown();
            assertClosedResponse(result(winner), fixture, fixture.first(), action, "HIDE");
            assertError(result(loser), 409, "REPORT_ALREADY_HANDLED");
            assertAudit(fixture, fixture.first(), action, "HIDE");
            if (action) assertTargetChanged(fixture, fixture.first(), "HIDE", beforeTarget);
            else assertThat(targetRow(fixture)).isEqualTo(beforeTarget);
            assertDecisionSql("winner", fixture.type(), action);
            assertDecisionSql("loser", fixture.type(), false);
            System.out.println("REPORT_CASE_DECISION_RACE_EVIDENCE type=" + type + " winnerAction=" + action
                    + " statuses=200,409 actualCaseUpdateWait=true auditRows=1");
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "{0} report upsert completes before waiting close")
    @ValueSource(strings = {"POST", "COMMENT"})
    void reportUpsertFirstMakesClosingUpdatePreserveEveryCountAndReason(String type) throws Exception {
        Fixture fixture = fixture(type, false);
        Account reporter = account(false, true);
        Hold reportHold = new Hold();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            assertThat(affected).isEqualTo(1);
            reportHold.closed.set(identity());
            reportHold.ready.countDown();
            await(reportHold.release, "report upsert commits before case closer proceeds");
            return affected;
        }).when(cases).upsertPendingReport(eq(type), eq(fixture.target()), anyString(), any());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> report = submitReport(workers, "report-first", reporter, fixture);
            await(reportHold.ready, "real report case UPSERT");
            CompletableFuture<MvcResult> close = submitDecision(workers, "close-second", fixture.first(), fixture, true, "HIDE");
            awaitBlocked(reportHold.closed.get().pid(), "%update%community.moderation_cases%");
            reportHold.release.countDown();
            assertReceived(result(report));
            assertClosedResponse(result(close), fixture, fixture.first(), true, "HIDE");
            assertReports(fixture.caseId(), List.of(fixture.reporter().id(), reporter.id()));
            assertAudit(fixture, fixture.first(), true, "HIDE");
            assertDecisionSql("close-second", type, true);
            System.out.println("REPORT_CASE_UPSERT_FIRST_EVIDENCE type=" + type
                    + " actualCaseUpdateWait=true closedReportCount=2 appendedReasonsPreserved=true");
        } finally {
            reportHold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "{0} close first, audit rollback={1}")
    @CsvSource({"POST,false", "POST,true", "COMMENT,false", "COMMENT,true"})
    void reportWaitingForCaseCloseCreatesNewPendingCaseOrAppendsToRolledBackOriginal(String type, boolean rollback)
            throws Exception {
        Fixture fixture = fixture(type, false);
        Account reporter = account(false, true);
        Map<String, Object> originalCase = caseRow(fixture.caseId());
        Map<String, Object> originalTarget = targetRow(fixture);
        Hold hold = probeClose(fixture, fixture.first());
        probeTarget(fixture, fixture.first(), hold);
        holdAudit(fixture, fixture.first(), hold, rollback);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> close = submitDecision(workers, "close-first", fixture.first(), fixture, true, "HIDE");
            await(hold.ready, "closed case and hidden target still uncommitted");
            CompletableFuture<MvcResult> report = submitReport(workers, "report-second", reporter, fixture);
            // The actual UPSERT wait proves the request already passed its nonlocking visibility guard.
            awaitBlocked(hold.closed.get().pid(), "%insert%into%community.moderation_cases%");
            hold.release.countDown();
            MvcResult closed = result(close);
            assertReceived(result(report));
            if (rollback) {
                assertError(closed, 500, "INTERNAL_ERROR");
                assertPreserved(originalCase, caseRow(fixture.caseId()), Set.of("report_count", "reasons"));
                assertThat(targetRow(fixture)).isEqualTo(originalTarget);
                assertReports(fixture.caseId(), List.of(fixture.reporter().id(), reporter.id()));
                assertThat(pendingCase(type, fixture.target())).isEqualTo(fixture.caseId());
                assertThat(caseCount(fixture)).isEqualTo(1);
                assertThat(auditRows(fixture.caseId())).isEmpty();
            } else {
                assertClosedResponse(closed, fixture, fixture.first(), true, "HIDE");
                assertReports(fixture.caseId(), List.of(fixture.reporter().id()));
                long next = pendingCase(type, fixture.target());
                assertThat(next).isNotEqualTo(fixture.caseId());
                assertReports(next, List.of(reporter.id()));
                assertThat(caseCount(fixture)).isEqualTo(2);
                assertThat(auditRows(next)).isEmpty();
                assertAudit(fixture, fixture.first(), true, "HIDE");
            }
            assertThat(hold.audited.get()).isEqualTo(hold.closed.get());
            System.out.println("REPORT_CASE_CLOSE_FIRST_EVIDENCE type=" + type + " rollback=" + rollback
                    + " actualUpsertWait=true reportStatus=201 newPendingCase=" + !rollback);
        } finally {
            hold.release.countDown();
            stop(workers);
        }
    }

    @ParameterizedTest(name = "{0} real audit INSERT and SQL error roll back the entire decision")
    @ValueSource(strings = {"POST", "COMMENT"})
    void sqlFailureAfterActualAuditInsertRollsBackCaseTargetAndAuditInOneTransaction(String type) throws Exception {
        Fixture fixture = fixture(type, false);
        Map<String, Object> beforeCase = caseRow(fixture.caseId());
        Map<String, Object> beforeTarget = targetRow(fixture);
        Hold hold = probeClose(fixture, fixture.first());
        probeTarget(fixture, fixture.first(), hold);
        holdAudit(fixture, fixture.first(), hold, true);
        hold.release.countDown();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertError(result(submitDecision(worker, "audit-fail", fixture.first(), fixture, true, "HIDE")),
                    500, "INTERNAL_ERROR");
            assertThat(hold.closed.get()).isNotNull();
            assertThat(hold.target.get()).isEqualTo(hold.closed.get());
            assertThat(hold.audited.get()).isEqualTo(hold.closed.get());
            assertThat(caseRow(fixture.caseId())).isEqualTo(beforeCase);
            assertThat(targetRow(fixture)).isEqualTo(beforeTarget);
            assertThat(auditRows(fixture.caseId())).isEmpty();
        } finally {
            hold.release.countDown();
            stop(worker);
        }
    }

    @Test
    void actualCommentCheckFailureAfterCaseCloseRollsBackCaseAndTargetWithoutAudit() throws Exception {
        Fixture fixture = fixture("COMMENT", false);
        Map<String, Object> beforeCase = caseRow(fixture.caseId());
        Map<String, Object> beforeTarget = targetRow(fixture);
        Hold hold = probeClose(fixture, fixture.first());
        AtomicReference<String> constraint = new AtomicReference<>();
        doAnswer(invocation -> {
            assertThat(identity()).isEqualTo(hold.closed.get());
            assertThat(caseRow(fixture.caseId()).get("status")).isEqualTo("ACTIONED");
            // Execute the real target UPDATE with a test-only null reason to violate ck_cmt_mod.
            invocation.getArguments()[3] = null;
            try {
                return delegate(invocation);
            } catch (Throwable exception) {
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    if (cause instanceof ConstraintViolationException violation) {
                        constraint.set(violation.getConstraintName());
                        assertThat(violation.getSQLState()).isEqualTo("23514");
                        break;
                    }
                }
                throw exception;
            }
        }).when(comments).moderateForReport(eq(fixture.target()), eq("HIDDEN"), eq(fixture.first().id()), anyString());
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertError(result(submitDecision(worker, "target-fail", fixture.first(), fixture, true, "HIDE")),
                    500, "INTERNAL_ERROR");
            assertThat(constraint.get()).isEqualTo("ck_cmt_mod");
            assertThat(caseRow(fixture.caseId())).isEqualTo(beforeCase);
            assertThat(targetRow(fixture)).isEqualTo(beforeTarget);
            assertThat(auditRows(fixture.caseId())).isEmpty();
        } finally {
            stop(worker);
        }
    }

    @Test
    void rejectedCaseKeepsLifetimeDuplicateAndLetsNewReporterOpenAnotherCase() throws Exception {
        Fixture fixture = fixture("POST", false);
        Account reporter = account(false, true);
        assertClosedResponse(decide("reject-e9", fixture.first(), fixture, false, null),
                fixture, fixture.first(), false, null);
        Map<String, Object> closed = caseRow(fixture.caseId());
        assertError(report(fixture.reporter(), fixture), 409, "DUPLICATE_REPORT");
        assertReceived(report(reporter, fixture));
        long next = pendingCase("POST", fixture.target());
        assertThat(next).isNotEqualTo(fixture.caseId());
        assertThat(caseRow(fixture.caseId())).isEqualTo(closed);
        assertReports(next, List.of(reporter.id()));
        assertThat(caseCount(fixture)).isEqualTo(2);
        assertAudit(fixture, fixture.first(), false, null);
        assertThat(auditRows(next)).isEmpty();
    }

    @ParameterizedTest(name = "N6 {0} preserves content timestamp, previously edited={1}")
    @CsvSource({"HIDE,false", "HIDE,true", "DELETE,false", "DELETE,true"})
    void commentModerationPreservesEditedAtContentTreeAndVisibleReplies(String action, boolean edited)
            throws Exception {
        Fixture fixture = fixture("COMMENT", edited);
        Map<String, Object> before = targetRow(fixture);
        Map<String, Object> beforeChild = commentRow(fixture.child());
        Map<String, Object> beforePost = postRow(fixture.post());
        JsonNode visibleBefore = publicComment(fixture.post(), fixture.target());
        JsonNode childBefore = publicComment(fixture.post(), fixture.child());
        if (edited) assertThat(Instant.parse(visibleBefore.path("editedAt").asText())).isEqualTo(CREATED.plusSeconds(1));
        else assertThat(visibleBefore.path("editedAt").isNull()).isTrue();
        assertClosedResponse(decide("n6", fixture.first(), fixture, true, action), fixture, fixture.first(), true, action);
        assertTargetChanged(fixture, fixture.first(), action, before);
        assertThat(commentRow(fixture.child())).isEqualTo(beforeChild);
        assertThat(postRow(fixture.post())).isEqualTo(beforePost);
        JsonNode placeholder = publicComment(fixture.post(), fixture.target());
        assertThat(placeholder.path("content").isNull()).isTrue();
        assertThat(placeholder.path("author").isNull()).isTrue();
        assertThat(placeholder.path("editedAt").isNull()).isTrue();
        assertThat(placeholder.path("status").asText()).isEqualTo(action.equals("DELETE") ? "DELETED" : "HIDDEN");
        assertThat(publicComment(fixture.post(), fixture.child())).isEqualTo(childBefore);
        assertAudit(fixture, fixture.first(), true, action);
        assertDecisionSql("n6", "COMMENT", true);
    }

    @ParameterizedTest(name = "{0} already {1}, ACTION {2} keeps all target metadata")
    @CsvSource({"POST,HIDDEN,HIDE", "COMMENT,HIDDEN,HIDE", "COMMENT,DELETED,HIDE", "COMMENT,DELETED,DELETE"})
    void alreadyHiddenOrDeletedTargetsAreNoOpsWhileTheCaseStillCloses(String type, String state, String action)
            throws Exception {
        Fixture fixture = fixture(type, true);
        transaction().executeWithoutResult(status -> {
            if (type.equals("POST")) jdbc.update("UPDATE community.posts SET status='HIDDEN',reviewed_by=?,"
                    + "reviewed_at=?,review_reason='Existing moderator reason' WHERE id=?", fixture.second().id(),
                    Timestamp.from(CREATED), fixture.target());
            else jdbc.update("UPDATE community.comments SET status=?,moderated_by=?,"
                    + "moderation_reason='Existing moderator reason' WHERE id=?", state, fixture.second().id(), fixture.target());
        });
        Map<String, Object> before = targetRow(fixture);
        AtomicReference<Integer> affected = new AtomicReference<>();
        org.mockito.stubbing.Answer<Object> observeTarget = invocation -> {
            Object actual = delegate(invocation);
            affected.set((Integer) actual);
            return actual;
        };
        if (type.equals("POST")) doAnswer(observeTarget).when(posts)
                .hideForReport(eq(fixture.target()), eq(fixture.first().id()), any(), anyString());
        else doAnswer(observeTarget).when(comments)
                .moderateForReport(eq(fixture.target()), anyString(), eq(fixture.first().id()), anyString());
        assertClosedResponse(decide("noop", fixture.first(), fixture, true, action), fixture, fixture.first(), true, action);
        assertThat(affected.get()).as("real conditional target UPDATE returns zero for a no-op").isZero();
        assertThat(targetRow(fixture)).isEqualTo(before);
        assertAudit(fixture, fixture.first(), true, action);
        assertError(decide("already-closed", fixture.second(), fixture, false, null), 409, "REPORT_ALREADY_HANDLED");
        assertThat(targetRow(fixture)).isEqualTo(before);
        assertThat(auditRows(fixture.caseId())).hasSize(1);
        assertDecisionSql("noop", type, true);
    }

    @Test
    void deleteCanChangeAnAlreadyHiddenCommentWithoutChangingItsContentTimestamp() throws Exception {
        Fixture fixture = fixture("COMMENT", true);
        transaction().executeWithoutResult(status -> jdbc.update("UPDATE community.comments SET status='HIDDEN',"
                + "moderated_by=?,moderation_reason='Previous hide' WHERE id=?", fixture.second().id(), fixture.target()));
        Map<String, Object> before = targetRow(fixture);
        assertClosedResponse(decide("hidden-delete", fixture.first(), fixture, true, "DELETE"),
                fixture, fixture.first(), true, "DELETE");
        assertTargetChanged(fixture, fixture.first(), "DELETE", before);
        assertAudit(fixture, fixture.first(), true, "DELETE");
    }

    @Test
    void casesForAPostAndItsCommentReachTargetWritesTogetherWithoutParentLockInversion() throws Exception {
        Fixture comment = fixture("COMMENT", false);
        assertReceived(report(comment.reporter(), "POST", comment.post()));
        Fixture post = new Fixture(comment.author(), comment.first(), comment.second(), comment.reporter(),
                "POST", comment.post(), comment.post(), comment.child(), pendingCase("POST", comment.post()));
        CountDownLatch atTargets = new CountDownLatch(2);
        doAnswer(invocation -> {
            atTargets.countDown();
            await(atTargets, "post and comment case target DML rendezvous");
            return delegate(invocation);
        }).when(posts).hideForReport(eq(post.target()), eq(post.first().id()), any(), anyString());
        doAnswer(invocation -> {
            atTargets.countDown();
            await(atTargets, "post and comment case target DML rendezvous");
            return delegate(invocation);
        }).when(comments).moderateForReport(eq(comment.target()), eq("HIDDEN"), eq(comment.second().id()), anyString());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = submitDecision(workers, "related-post", post.first(), post, true, null);
            CompletableFuture<MvcResult> second = submitDecision(workers, "related-comment", comment.second(), comment, true, "HIDE");
            assertClosedResponse(result(first), post, post.first(), true, null);
            assertClosedResponse(result(second), comment, comment.second(), true, "HIDE");
            assertAudit(post, post.first(), true, null);
            assertAudit(comment, comment.second(), true, "HIDE");
            assertDecisionSql("related-post", "POST", true);
            assertDecisionSql("related-comment", "COMMENT", true);
        } finally {
            stop(workers);
        }
    }

    private Hold probeClose(Fixture fixture, Account moderator) {
        Hold hold = new Hold();
        doAnswer(invocation -> {
            Object affected = delegate(invocation);
            assertThat(affected).isEqualTo(1);
            hold.closed.set(identity());
            assertThat(hold.closed.get().isolation()).isEqualTo("read committed");
            assertThat(caseRow(fixture.caseId()).get("handled_at")).isEqualTo(Timestamp.from(SAVED_TIME));
            return affected;
        }).when(cases).closePending(eq(fixture.caseId()), anyString(), eq(moderator.id()), any(), anyString());
        return hold;
    }

    private void probeTarget(Fixture fixture, Account moderator, Hold hold) {
        org.mockito.stubbing.Answer<Object> answer = invocation -> {
            assertThat(identity()).isEqualTo(hold.closed.get());
            Object affected = delegate(invocation);
            assertThat(affected).isEqualTo(1);
            hold.target.set(identity());
            assertThat(hold.target.get()).isEqualTo(hold.closed.get());
            return affected;
        };
        if (fixture.type().equals("POST")) doAnswer(answer).when(posts)
                .hideForReport(eq(fixture.target()), eq(moderator.id()), any(), anyString());
        else doAnswer(answer).when(comments)
                .moderateForReport(eq(fixture.target()), anyString(), eq(moderator.id()), anyString());
    }

    private void holdAudit(Fixture fixture, Account moderator, Hold hold, boolean fail) {
        doAnswer(invocation -> {
            assertThat(identity()).isEqualTo(hold.closed.get());
            invocation.callRealMethod();
            entityManager.flush();
            hold.audited.set(identity());
            assertThat(hold.audited.get()).isEqualTo(hold.closed.get());
            assertThat(((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()")
                    .getSingleResult()).intValue()).isEqualTo(hold.closed.get().pid());
            assertThat(auditRows(fixture.caseId())).hasSize(1);
            if (invocation.getArgument(1, String.class).equals("COMMUNITY_REPORT_ACTION")) {
                assertThat(hold.target.get()).isEqualTo(hold.closed.get());
                assertThat(targetRow(fixture).get("status")).isEqualTo("HIDDEN");
            }
            hold.ready.countDown();
            await(hold.release, "release real case/target/audit writes before commit");
            System.out.println("REPORT_CASE_TRANSACTION_EVIDENCE backendPid=" + hold.closed.get().pid()
                    + " transactionId=" + hold.closed.get().transactionId() + " sameCaseTargetAuditTransaction=true");
            if (fail) jdbc.execute("SELECT 1 / 0");
            return null;
        }).when(audit).recordDetail(eq(Long.valueOf(moderator.id())), anyString(), eq("REPORT_CASE"), eq(fixture.caseId()), any());
    }

    private void assertClosedResponse(MvcResult response, Fixture fixture, Account moderator, boolean action,
            String commentAction) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> saved = caseRow(fixture.caseId());
        JsonNode json = body(response);
        assertThat(json.path("id").asLong()).isEqualTo(fixture.caseId());
        assertThat(saved.get("status")).isEqualTo(action ? "ACTIONED" : "REJECTED");
        assertThat(json.path("status").asText()).isEqualTo(saved.get("status"));
        assertThat(((Number) saved.get("handled_by")).longValue()).isEqualTo(moderator.id());
        assertThat(saved.get("handled_at")).isEqualTo(Timestamp.from(SAVED_TIME));
        assertThat(saved.get("decision_note")).isEqualTo(NOTE);
        assertThat(saved.get("auto_hidden")).isEqualTo(false);
        assertThat(json.path("handledBy").path("id").asLong()).isEqualTo(moderator.id());
        assertThat(json.path("handledBy").path("displayName").asText()).isEqualTo(moderator.name());
        assertThat(Instant.parse(json.path("handledAt").asText())).isEqualTo(SAVED_TIME);
        assertThat(json.path("decisionNote").asText()).isEqualTo(NOTE);
        assertThat(Instant.parse(json.path("createdAt").asText())).isEqualTo(((Timestamp) saved.get("created_at")).toInstant());
        assertThat(json.path("reportCount").asInt()).isEqualTo(((Number) saved.get("report_count")).intValue());
        assertThat(json.path("reports")).hasSize(json.path("reportCount").asInt());
        JsonNode storedReasons = mapper.readTree(saved.get("reasons").toString());
        for (int index = 0; index < storedReasons.size(); index++) {
            JsonNode entry = json.path("reports").get(index);
            JsonNode stored = storedReasons.get(index);
            assertThat(entry.path("reporter").path("id").asLong()).isEqualTo(stored.path("userId").asLong());
            assertThat(entry.path("reporter").path("displayName").asText()).isNotBlank();
            assertThat(entry.path("reason")).isEqualTo(stored.path("reason"));
            assertThat(entry.path("detail")).isEqualTo(stored.path("detail"));
            assertThat(Instant.parse(entry.path("reportedAt").asText())).isEqualTo(Instant.parse(stored.path("reportedAt").asText()));
        }
        assertThat(json.path("target").path("id").asLong()).isEqualTo(fixture.target());
        assertThat(json.path("target").path("kind").asText()).isEqualTo(fixture.type());
        assertThat(json.path("target").path("status").asText()).isEqualTo(targetRow(fixture).get("status"));
        assertThat(json.path("target").path("author").path("id").asLong()).isEqualTo(fixture.author().id());
        MvcResult fresh = mvc.perform(get("/api/community/moderation/reports/" + fixture.caseId())
                .header("Authorization", "Bearer " + moderator.token())).andReturn();
        assertThat(fresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(fresh)).isEqualTo(json);
    }

    private void assertTargetChanged(Fixture fixture, Account moderator, String action, Map<String, Object> before) {
        Map<String, Object> after = targetRow(fixture);
        if (fixture.type().equals("POST")) {
            assertThat(after.get("status")).isEqualTo("HIDDEN");
            assertThat(((Number) after.get("reviewed_by")).longValue()).isEqualTo(moderator.id());
            assertThat(after.get("reviewed_at")).isEqualTo(Timestamp.from(SAVED_TIME));
            assertThat(after.get("review_reason")).isEqualTo(NOTE);
            assertPreserved(before, after, Set.of("status", "reviewed_by", "reviewed_at", "review_reason"));
        } else {
            assertThat(after.get("status")).isEqualTo(action.equals("DELETE") ? "DELETED" : "HIDDEN");
            assertThat(((Number) after.get("moderated_by")).longValue()).isEqualTo(moderator.id());
            assertThat(after.get("moderation_reason")).isEqualTo(NOTE);
            assertPreserved(before, after, Set.of("status", "moderated_by", "moderation_reason"));
            assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
        }
    }

    private void assertAudit(Fixture fixture, Account moderator, boolean action, String commentAction) throws Exception {
        List<Map<String, Object>> rows = auditRows(fixture.caseId());
        assertThat(rows).hasSize(1);
        Map<String, Object> saved = rows.get(0);
        assertThat(((Number) saved.get("actor_id")).longValue()).isEqualTo(moderator.id());
        assertThat(saved.get("action")).isEqualTo(action ? "COMMUNITY_REPORT_ACTION" : "COMMUNITY_REPORT_REJECT");
        assertThat(saved.get("target_type")).isEqualTo("REPORT_CASE");
        assertThat(((Number) saved.get("target_id")).longValue()).isEqualTo(fixture.caseId());
        Map<String, Object> detail = action && fixture.type().equals("COMMENT")
                ? Map.of("targetType", fixture.type(), "targetId", fixture.target(), "commentAction", commentAction)
                : Map.of("targetType", fixture.type(), "targetId", fixture.target());
        assertThat(mapper.readTree(saved.get("detail").toString()))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(detail)));
    }

    private void assertReports(long id, List<Long> reporters) throws Exception {
        Map<String, Object> saved = caseRow(id);
        assertThat(((Number) saved.get("report_count")).intValue()).isEqualTo(reporters.size());
        List<Long> actual = new ArrayList<>();
        for (JsonNode entry : mapper.readTree(saved.get("reasons").toString())) actual.add(entry.path("userId").asLong());
        assertThat(actual).containsExactlyElementsOf(reporters);
    }

    private void assertDecisionSql(String key, String type, boolean targetWrite) {
        List<String> sql = capture.statements(key);
        assertThat(sql).noneMatch(statement -> statement.contains("for update") || statement.contains("for no key update")
                || statement.contains("for share") || statement.contains("for key share") || statement.contains("pg_advisory"));
        List<String> close = sql.stream().filter(statement -> statement.startsWith("update community.moderation_cases ")).toList();
        assertThat(close).hasSize(1);
        String normalized = close.get(0).replaceAll("\\s+", "");
        assertThat(normalized).contains("status=", "handled_by=", "handled_at=", "decision_note=", "status='pending'")
                .doesNotContain("report_count=", "reasons=", "auto_hidden=", "created_at=", "target_id=", "target_type=");
        List<String> target = sql.stream().filter(statement -> statement.startsWith("update community.posts ")
                || statement.startsWith("update community.comments ")).toList();
        assertThat(target).hasSize(targetWrite ? 1 : 0);
        if (targetWrite) {
            assertThat(sql.indexOf(close.get(0))).isLessThan(sql.indexOf(target.get(0)));
            if (type.equals("COMMENT")) assertThat(target.get(0)).doesNotContain("updated_at", "content=", "content =", "parent_id", "post_id");
        }
    }

    private int awaitBlocked(int holder, String queryPattern) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            List<Integer> waiting = jdbc.queryForList("""
                    SELECT a.pid FROM pg_stat_activity a
                    WHERE a.datname=current_database() AND a.backend_type='client backend'
                      AND ?=ANY(pg_blocking_pids(a.pid)) AND a.query ILIKE ?
                      AND EXISTS (SELECT 1 FROM pg_locks l WHERE l.pid=a.pid
                        AND NOT l.granted AND l.locktype='transactionid')
                    """, Integer.class, holder, queryPattern);
            if (waiting.size() == 1) {
                assertThat(waiting.get(0)).isNotEqualTo(holder);
                System.out.println("REPORT_CASE_LOCK_EVIDENCE holderPid=" + holder + " waiterPid=" + waiting.get(0)
                        + " query=" + (queryPattern.contains("insert") ? "UPSERT" : "CASE_UPDATE")
                        + " ungrantedTransactionIdWait=true");
                return waiting.get(0);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while observing PostgreSQL case wait", exception);
            }
        }
        throw new AssertionError("No actual transactionid waiter for " + queryPattern + " within " + TIMEOUT);
    }

    private Fixture fixture(String type, boolean edited) throws Exception {
        Account author = account(false, false);
        Account first = account(true, false);
        Account second = account(true, false);
        Account reporter = account(false, true);
        long[] ids = transaction().execute(status -> {
            long postId = jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,"
                    + "published_at,created_at,updated_at) VALUES (?,'QUESTION','Case concurrency post',"
                    + "'Synthetic reported post','APPROVED',?,?,?) RETURNING id", Long.class, author.id(),
                    Timestamp.from(CREATED), Timestamp.from(CREATED), Timestamp.from(CREATED));
            if (type.equals("POST")) return new long[] {postId, postId, 0};
            long target = jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content,created_at,updated_at) "
                    + "VALUES (?,?,?,?,?) RETURNING id", Long.class, postId, author.id(), CONTENT,
                    Timestamp.from(CREATED), Timestamp.from(edited ? CREATED.plusSeconds(1) : CREATED));
            long child = jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,created_at,updated_at) "
                    + "VALUES (?,?,?,'Synthetic visible child',1,?,?) RETURNING id", Long.class, postId, target,
                    author.id(), Timestamp.from(CREATED.plusSeconds(2)), Timestamp.from(CREATED.plusSeconds(2)));
            return new long[] {postId, target, child};
        });
        assertReceived(report(reporter, type, ids[1]));
        return new Fixture(author, first, second, reporter, type, ids[1], ids[0], ids[2], pendingCase(type, ids[1]));
    }

    private Account account(boolean moderator, boolean verified) throws Exception {
        String email = "report-case-" + UUID.randomUUID() + "@example.test";
        String password = "Case" + UUID.randomUUID() + "A9";
        String name = "Case fixture " + UUID.randomUUID();
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
            assertThat(jdbc.queryForObject("SELECT email_verified_at IS NOT NULL FROM auth.users WHERE id=?", Boolean.class, id)).isTrue();
        }
        if (moderator) transaction().executeWithoutResult(status -> jdbc.update(
                "INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", id));
        return new Account(id, jwt.issueAccessToken(id, List.of(moderator ? "MODERATOR" : "LEARNER"), verified,
                UUID.randomUUID()), name);
    }

    private MvcResult decide(String key, Account moderator, Fixture fixture, boolean action, String commentAction)
            throws Exception {
        capture.start(key);
        try {
            var request = mapper.createObjectNode().put("decision", action ? "ACTION" : "REJECT").put("note", NOTE);
            if (action && fixture.type().equals("COMMENT")) request.put("commentAction", commentAction);
            return mvc.perform(patch("/api/community/moderation/reports/" + fixture.caseId())
                    .header("Authorization", "Bearer " + moderator.token()).contentType(MediaType.APPLICATION_JSON)
                    .content(request.toString())).andReturn();
        } finally {
            capture.stop();
        }
    }

    private CompletableFuture<MvcResult> submitDecision(ExecutorService workers, String key, Account moderator,
            Fixture fixture, boolean action, String commentAction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return decide(key, moderator, fixture, action, commentAction);
            } catch (Exception exception) {
                throw new IllegalStateException("Report decision HTTP request failed", exception);
            }
        }, workers);
    }

    private CompletableFuture<MvcResult> submitReport(ExecutorService workers, String key, Account reporter, Fixture fixture) {
        return CompletableFuture.supplyAsync(() -> {
            capture.start(key);
            try {
                return report(reporter, fixture);
            } catch (Exception exception) {
                throw new IllegalStateException("Report HTTP request failed", exception);
            } finally {
                capture.stop();
            }
        }, workers);
    }

    private MvcResult report(Account reporter, Fixture fixture) throws Exception {
        return report(reporter, fixture.type(), fixture.target());
    }

    private MvcResult report(Account reporter, String type, long target) throws Exception {
        return mvc.perform(post("/api/community/reports").header("Authorization", "Bearer " + reporter.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "targetType", type, "targetId", target, "reason", "SPAM", "detail", "Synthetic report 中文 😀"))))
                .andReturn();
    }

    private JsonNode publicComment(long postId, long commentId) throws Exception {
        MvcResult result = mvc.perform(get("/api/public/community/posts/" + postId + "/comments")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        for (JsonNode comment : body(result)) if (comment.path("id").asLong() == commentId) return comment;
        throw new AssertionError("Expected retained public comment " + commentId);
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(mailpitUrl + path))
                .timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private long pendingCase(String type, long target) {
        return jdbc.queryForObject("SELECT id FROM community.moderation_cases WHERE target_type=? AND target_id=? AND status='PENDING'",
                Long.class, type, target);
    }

    private int caseCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE target_type=? AND target_id=?",
                Integer.class, fixture.type(), fixture.target());
    }

    private Map<String, Object> caseRow(long id) { return jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", id); }
    private Map<String, Object> commentRow(long id) { return jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id); }
    private Map<String, Object> postRow(long id) { return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id); }
    private Map<String, Object> targetRow(Fixture fixture) {
        return fixture.type().equals("POST") ? postRow(fixture.target()) : commentRow(fixture.target());
    }
    private List<Map<String, Object>> auditRows(long id) {
        return jdbc.queryForList("SELECT actor_id,action,target_type,target_id,detail::text AS detail "
                + "FROM shared.audit_logs WHERE target_type='REPORT_CASE' AND target_id=?", id);
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

    private static Object delegate(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private static void assertPreserved(Map<String, Object> before, Map<String, Object> after, Set<String> changed) {
        before.forEach((field, value) -> {
            if (!changed.contains(field)) assertThat(after.get(field)).as("preserved field %s", field).isEqualTo(value);
        });
    }

    private void assertReceived(MvcResult response) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(response)).isEqualTo(mapper.readTree("{\"received\":true}"));
    }

    private void assertError(MvcResult response, int status, String code) throws Exception {
        assertThat(response.getResponse().getStatus()).isEqualTo(status);
        JsonNode json = body(response);
        assertThat(json.path("error_code").asText()).isEqualTo(code);
        assertThat(json.path("message").asText()).isNotBlank();
        assertThat(json.path("request_id").asText()).isNotBlank();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("org.hibernate", "PSQLException", "ck_cmt_", "stackTrace");
    }

    private JsonNode body(MvcResult response) throws Exception {
        return mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
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

    private record Account(long id, String token, String name) { }
    private record Fixture(Account author, Account first, Account second, Account reporter, String type,
            long target, long post, long child, long caseId) { }
    private record TransactionIdentity(int pid, String transactionId, String isolation) { }
    private static final class Hold {
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<TransactionIdentity> closed = new AtomicReference<>();
        private final AtomicReference<TransactionIdentity> target = new AtomicReference<>();
        private final AtomicReference<TransactionIdentity> audited = new AtomicReference<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaseConfiguration {
        @Bean @Primary Clock reportCaseConcurrencyClock() { return Clock.fixed(REQUEST_TIME, ZoneOffset.UTC); }
        @Bean CaseSqlCapture reportCaseSqlCapture() { return new CaseSqlCapture(); }
        @Bean HibernatePropertiesCustomizer reportCaseStatementInspector(CaseSqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static final class CaseSqlCapture implements StatementInspector {
        private final ThreadLocal<String> active = new ThreadLocal<>();
        private final Map<String, List<String>> statements = new ConcurrentHashMap<>();
        void start(String key) {
            statements.put(key, new CopyOnWriteArrayList<>());
            active.set(key);
        }
        void stop() { active.remove(); }
        List<String> statements(String key) { return List.copyOf(statements.get(key)); }
        @Override public String inspect(String sql) {
            String key = active.get();
            if (key != null) statements.get(key).add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
