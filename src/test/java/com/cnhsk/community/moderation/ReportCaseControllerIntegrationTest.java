package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(ReportCaseControllerIntegrationTest.ReportCaseConfiguration.class)
class ReportCaseControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/moderation/reports";
    private static final String POSTS = "/api/community/moderation/posts";
    private static final String PASSWORD = "ReportCaseFixturePass123";
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private static final List<String> SUMMARY = List.of("id", "targetType", "targetId", "reportCount", "latestReason", "createdAt", "targetPreview");
    private static final List<String> DETAIL = List.of("id", "status", "reportCount", "target", "reports", "handledBy", "handledAt", "decisionNote", "createdAt");
    private static final List<String> POST_TARGET = List.of("kind", "id", "title", "content", "category", "status", "author");
    private static final List<String> COMMENT_TARGET = List.of("kind", "id", "content", "status", "author", "postId", "postTitle");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ReportCaseClock clock;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private AuthApi auth;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    void prepare() {
        clean();
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789));
    }

    @AfterEach
    void restore() {
        capture.stop();
        clock.reset();
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.moderation_cases");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void moderatorOnlyReadsAndDecisionsSupportBearerAndCookieWithoutVerifiedEmailOrLearnerRole() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long id = reportCase("POST", postTarget(author.id(), "APPROVED"), List.of(moderator), ORIGINAL);
        error(get(BASE), 401, "UNAUTHENTICATED");
        error(get(BASE + "/{id}", id), 401, "UNAUTHENTICATED");
        error(decide(id, "not-json"), 401, "UNAUTHENTICATED");
        for (String role : List.of("LEARNER", "ADMIN", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
            String token = bearer(moderator, Set.of(role), true);
            error(get(BASE).header("Authorization", token), 403, "FORBIDDEN");
            error(get(BASE + "/{id}", id).header("Authorization", token), 403, "FORBIDDEN");
            error(decide(id, "not-json").header("Authorization", token), 403, "FORBIDDEN");
        }
        String token = moderatorToken(moderator);
        clearInvocations(auth);
        ok(get(BASE).header("Authorization", token));
        ok(get(BASE + "/{id}", id).header("Authorization", token));
        error(decide(id, "{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        ok(decide(id, body("REJECT", "Bearer note", null)).header("Authorization", token));
        verify(auth, never()).requireVerifiedEmail(anyLong());
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", moderator.id());
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", moderator.id());
        Session session = login(moderator);
        long cookieCase = reportCase("POST", postTarget(author.id(), "APPROVED"), List.of(moderator), ORIGINAL);
        ok(get(BASE).cookie(session.access()));
        ok(get(BASE + "/{id}", cookieCase).cookie(session.access()));
        error(decide(cookieCase, "not-json").cookie(session.access()), 403, "CSRF_TOKEN_INVALID");
        error(decide(cookieCase, body("REJECT", "Note", null)).cookie(session.access(), session.csrf())
                .header("X-XSRF-TOKEN", "wrong"), 403, "CSRF_TOKEN_INVALID");
        ok(decide(cookieCase, body("REJECT", "Cookie note", null)).cookie(session.access(), session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    @Test
    void pendingQueueOrdersReportCountThenCreatedTimeAndIdAndUsesLatestReasonAnd120CodePointPreview() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        List<Account> reporters = List.of(register("Reporter A"), register("Reporter B"), register("Reporter C"));
        long single = reportCase("POST", postTarget(author.id(), "APPROVED"), reporters.subList(0, 1), ORIGINAL.minusSeconds(100));
        long post = postTarget(author.id(), "PENDING");
        jdbc.update("UPDATE community.posts SET title=? WHERE id=?", "😀".repeat(121), post);
        long first = reportCase("POST", post, reporters, ORIGINAL);
        long parent = postTarget(author.id(), "HIDDEN");
        long comment = commentTarget(parent, author.id(), "DELETED", null);
        jdbc.update("UPDATE community.comments SET content=? WHERE id=?", "中".repeat(121), comment);
        long second = reportCase("COMMENT", comment, reporters, ORIGINAL);
        long third = reportCase("POST", postTarget(author.id(), "DRAFT"), reporters, ORIGINAL.plusSeconds(1));
        long closed = reportCase("POST", postTarget(author.id(), "APPROVED"), reporters, ORIGINAL.minusSeconds(500));
        closeFixture(closed, moderator, "REJECTED");
        String token = moderatorToken(moderator);
        JsonNode result = ok(get(BASE).header("Authorization", token));
        assertThat(fields(result)).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
        assertThat(ids(result)).containsExactly(first, second, third, single);
        assertThat(result.path("totalElements").asLong()).isEqualTo(4);
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        for (JsonNode item : result.path("items")) assertThat(fields(item)).containsExactlyInAnyOrderElementsOf(SUMMARY);
        assertThat(result.path("items").get(0).path("targetPreview").asText()).isEqualTo("😀".repeat(120));
        assertThat(result.path("items").get(1).path("targetPreview").asText()).isEqualTo("中".repeat(120));
        assertThat(result.path("items").get(0).path("latestReason").asText()).isEqualTo("OTHER");
        assertThat(ids(ok(get(BASE).header("Authorization", token).param("page", "1").param("size", "2"))))
                .containsExactly(third, single);
        for (String page : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            assertThat(ok(get(BASE).header("Authorization", token).param("page", page).param("size", "50")).path("items")).isEmpty();
        }
        for (String page : List.of("-1", "abc", "2147483648")) error(get(BASE).header("Authorization", token).param("page", page), 422, "VALIDATION_FAILED");
        for (String size : List.of("0", "51", "abc", "2147483648")) error(get(BASE).header("Authorization", token).param("size", size), 422, "VALIDATION_FAILED");
        assertThat(ok(get(BASE).header("Authorization", token).param("page", "").param("size", ""))).isEqualTo(result);
        assertPrivacy(result);
    }

    @Test
    void caseDetailsKeepAllTargetStatesAndCaseStatesWithReporterReferencesAndRequiredNullDetail() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        List<Account> reporters = List.of(register("Reporter A"), register("Reporter B"), register("Reporter C"));
        String token = moderatorToken(moderator);
        for (String caseState : List.of("PENDING", "ACTIONED", "REJECTED")) {
            for (String state : List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN")) {
                long target = postTarget(author.id(), state);
                long id = reportCase("POST", target, reporters, ORIGINAL);
                if (!caseState.equals("PENDING")) closeFixture(id, moderator, caseState);
                JsonNode result = ok(get(BASE + "/{id}", id).header("Authorization", token));
                assertDetail(result, id, caseState, reporters);
                assertThat(fields(result.path("target"))).containsExactlyInAnyOrderElementsOf(POST_TARGET);
                assertThat(result.path("target").path("kind").asText()).isEqualTo("POST");
                assertThat(result.path("target").path("status").asText()).isEqualTo(state);
                assertRef(result.path("target").path("author"), author);
            }
            for (String state : List.of("VISIBLE", "HIDDEN", "DELETED")) {
                long parent = postTarget(author.id(), "HIDDEN");
                long target = commentTarget(parent, author.id(), state, null);
                long id = reportCase("COMMENT", target, reporters, ORIGINAL);
                if (!caseState.equals("PENDING")) closeFixture(id, moderator, caseState);
                JsonNode result = ok(get(BASE + "/{id}", id).header("Authorization", token));
                assertDetail(result, id, caseState, reporters);
                assertThat(fields(result.path("target"))).containsExactlyInAnyOrderElementsOf(COMMENT_TARGET);
                assertThat(result.path("target").path("kind").asText()).isEqualTo("COMMENT");
                assertThat(result.path("target").path("status").asText()).isEqualTo(state);
                assertThat(result.path("target").path("content").asText()).isEqualTo("Private synthetic comment");
                assertThat(result.path("target").path("postId").asLong()).isEqualTo(parent);
                assertThat(result.path("target").path("postTitle").asText()).isEqualTo("Private synthetic title");
                assertRef(result.path("target").path("author"), author);
            }
        }
        long own = reportCase("POST", postTarget(moderator.id(), "APPROVED"), reporters, ORIGINAL);
        ok(get(BASE + "/{id}", own).header("Authorization", token));
        error(get(BASE + "/{id}", own).header("Authorization", bearer(author, Set.of("LEARNER"), false)), 403, "FORBIDDEN");
    }

    @Test
    void independentBodyValidationRejectsMalformedTypesMissingNullAndOverlongNotesBeforeCaseLookup() throws Exception {
        Account moderator = register("Moderator");
        String token = moderatorToken(moderator);
        List<String> invalid = new ArrayList<>(List.of("", "not-json", "null", "[]", "42", "true", "{}",
                "{\"decision\":\"REJECT\",\"note\":\"Note\"", "{\"decision\":\"REJECT\",\"note\":\"Note\"} {}",
                "{\"decision\":\"REJECT\",\"note\":\"Note\"} true"));
        String valid = body("REJECT", "Note", null);
        for (String field : List.of("decision", "note")) {
            ObjectNode missing = (ObjectNode) mapper.readTree(valid);
            missing.remove(field);
            invalid.add(missing.toString());
            ObjectNode explicitNull = (ObjectNode) mapper.readTree(valid);
            explicitNull.putNull(field);
            invalid.add(explicitNull.toString());
            for (String replacement : List.of("42", "true", "[]", "{}")) {
                ObjectNode wrong = (ObjectNode) mapper.readTree(valid);
                wrong.set(field, mapper.readTree(replacement));
                invalid.add(wrong.toString());
            }
        }
        for (String value : List.of("BAN_USER", "action", " ACTION ", "")) invalid.add(body(value, "Note", null));
        for (String value : List.of("", " \t\n\u3000", "x".repeat(1001), "😀".repeat(1001))) invalid.add(body("REJECT", value, null));
        for (String action : List.of("null", "42", "true", "[]", "{}", "\"RESTORE\"", "\"hide\"", "\" HIDE \"")) {
            ObjectNode wrong = (ObjectNode) mapper.readTree(valid);
            wrong.set("commentAction", mapper.readTree(action));
            invalid.add(wrong.toString());
        }
        for (String input : invalid) error(decide(Long.MAX_VALUE, input).header("Authorization", token), 422, "VALIDATION_FAILED");
        for (String id : List.of("missing", "9223372036854775808", Long.toString(Long.MAX_VALUE), "0", "-1")) {
            error(get(BASE + "/" + id).header("Authorization", token), 404, "REPORT_CASE_NOT_FOUND");
            error(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(valid).header("Authorization", token), 404, "REPORT_CASE_NOT_FOUND");
            error(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        }
    }

    @Test
    void strippedOneAndThousandCodePointNotesAndProtectedMetadataUseOnlyAuthenticatedValues() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        for (String note : List.of("x", "😀", "x".repeat(1000), "😀".repeat(1000))) {
            long target = postTarget(author.id(), "APPROVED");
            long id = reportCase("POST", target, List.of(moderator), ORIGINAL);
            ObjectNode input = (ObjectNode) mapper.readTree(body("REJECT", "\u3000 " + note + " \t", null));
            input.put("handledBy", author.id()).put("status", "ACTIONED").put("handledAt", "2000-01-01T00:00:00Z")
                    .put("targetId", Long.MAX_VALUE).put("noteValid", true);
            var targetBefore = postRow(target);
            JsonNode result = ok(decide(id, input.toString()).header("Authorization", moderatorToken(moderator)));
            assertThat(result.path("status").asText()).isEqualTo("REJECTED");
            assertThat(result.path("decisionNote").asText()).isEqualTo(note);
            assertRef(result.path("handledBy"), moderator);
            assertThat(Instant.parse(result.path("handledAt").asText())).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
            assertThat(postRow(target)).isEqualTo(targetBefore);
            assertAudit(id, moderator.id(), "COMMUNITY_REPORT_REJECT", Map.of("targetType", "POST", "targetId", target));
        }
    }

    @Test
    void selfCheckPrecedesContextValidationWhichPrecedesAlreadyHandledConflict() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        long parent = postTarget(author.id(), "APPROVED");
        long own = reportCase("COMMENT", commentTarget(parent, moderator.id(), "VISIBLE", null), List.of(author), ORIGINAL);
        closeFixture(own, author, "REJECTED");
        var before = caseRow(own);
        error(decide(own, body("ACTION", "Note", null)).header("Authorization", token), 403, "SELF_MODERATION");
        error(decide(own, body("REJECT", "Note", "HIDE")).header("Authorization", token), 403, "SELF_MODERATION");
        error(decide(own, "{\"decision\":\"ACTION\",\"note\":\"Note\",\"commentAction\":null}").header("Authorization", token), 422, "VALIDATION_FAILED");
        assertThat(caseRow(own)).isEqualTo(before);
        long other = reportCase("COMMENT", commentTarget(parent, author.id(), "VISIBLE", null), List.of(moderator), ORIGINAL);
        closeFixture(other, moderator, "ACTIONED");
        error(decide(other, body("ACTION", "Note", null)).header("Authorization", token), 422, "VALIDATION_FAILED");
        error(decide(other, body("REJECT", "Note", "DELETE")).header("Authorization", token), 422, "VALIDATION_FAILED");
        error(decide(other, body("ACTION", "Note", "HIDE")).header("Authorization", token), 409, "REPORT_ALREADY_HANDLED");
        long post = reportCase("POST", postTarget(author.id(), "APPROVED"), List.of(moderator), ORIGINAL);
        error(decide(post, body("ACTION", "Note", "HIDE")).header("Authorization", token), 422, "VALIDATION_FAILED");
        long ownPost = reportCase("POST", postTarget(moderator.id(), "APPROVED"), List.of(author), ORIGINAL);
        error(decide(ownPost, body("ACTION", "Note", "HIDE")).header("Authorization", token), 403, "SELF_MODERATION");
        assertThat(auditRows(own)).isEmpty();
        assertThat(auditRows(other)).isEmpty();
        assertThat(auditRows(post)).isEmpty();
    }

    @Test
    void explicitNullCommentActionIsAlways422ForBothDecisionsAndTargetTypes() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long parent = postTarget(author.id(), "APPROVED");
        for (String type : List.of("POST", "COMMENT")) {
            long target = type.equals("POST") ? postTarget(author.id(), "APPROVED") : commentTarget(parent, author.id(), "VISIBLE", null);
            long id = reportCase(type, target, List.of(moderator), ORIGINAL);
            var before = caseRow(id);
            for (String decision : List.of("ACTION", "REJECT")) {
                ObjectNode input = (ObjectNode) mapper.readTree(body(decision, "Note", null));
                input.putNull("commentAction");
                error(decide(id, input.toString()).header("Authorization", moderatorToken(moderator)), 422, "VALIDATION_FAILED");
            }
            assertThat(caseRow(id)).isEqualTo(before);
            assertThat(auditRows(id)).isEmpty();
        }
    }

    @Test
    void actionOnPostHidesFreshTargetPreservesPublicationAndMakesAuthorReasonVisibleImmediately() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long target = postTarget(author.id(), "APPROVED");
        long id = reportCase("POST", target, List.of(moderator), ORIGINAL);
        var before = postRow(target);
        JsonNode result = ok(decide(id, body("ACTION", " \u3000Post reason\t ", null)).header("Authorization", moderatorToken(moderator)));
        assertThat(result.path("status").asText()).isEqualTo("ACTIONED");
        assertThat(result.path("target").path("status").asText()).isEqualTo("HIDDEN");
        assertThat(result.path("decisionNote").asText()).isEqualTo("Post reason");
        assertRef(result.path("handledBy"), moderator);
        assertThat(Instant.parse(result.path("handledAt").asText())).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
        Map<String, Object> stored = postRow(target);
        assertThat(stored.get("status")).isEqualTo("HIDDEN");
        assertThat(stored.get("review_reason")).isEqualTo("Post reason");
        assertThat(((Number) stored.get("reviewed_by")).longValue()).isEqualTo(moderator.id());
        assertThat(((Timestamp) stored.get("reviewed_at")).toInstant()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
        preserved(before, stored, Set.of("status", "reviewed_by", "reviewed_at", "review_reason"));
        error(get("/api/public/community/posts/{id}", target), 404, "POST_NOT_FOUND");
        assertThat(ids(ok(get("/api/public/community/posts")))).doesNotContain(target);
        JsonNode mine = ok(get("/api/community/posts/mine/{id}", target).header("Authorization", bearer(author, Set.of("LEARNER"), false)));
        assertThat(mine.path("status").asText()).isEqualTo("HIDDEN");
        assertThat(mine.path("reviewReason").asText()).isEqualTo("Post reason");
        assertAudit(id, moderator.id(), "COMMUNITY_REPORT_ACTION", Map.of("targetType", "POST", "targetId", target));
    }

    @Test
    void actionOnCommentHidesOrDeletesOnlyTargetAndKeepsChildrenAndStoredTimestamps() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long post = postTarget(author.id(), "APPROVED");
        for (String action : List.of("HIDE", "DELETE")) {
            long target = commentTarget(post, author.id(), "VISIBLE", null);
            long child = commentTarget(post, author.id(), "VISIBLE", target);
            long id = reportCase("COMMENT", target, List.of(moderator), ORIGINAL);
            var before = commentRow(target);
            var childBefore = commentRow(child);
            JsonNode result = ok(decide(id, body("ACTION", "Comment reason", action)).header("Authorization", moderatorToken(moderator)));
            String state = action.equals("HIDE") ? "HIDDEN" : "DELETED";
            assertThat(result.path("status").asText()).isEqualTo("ACTIONED");
            assertThat(result.path("target").path("status").asText()).isEqualTo(state);
            Map<String, Object> stored = commentRow(target);
            assertThat(stored.get("status")).isEqualTo(state);
            assertThat(stored.get("moderation_reason")).isEqualTo("Comment reason");
            assertThat(((Number) stored.get("moderated_by")).longValue()).isEqualTo(moderator.id());
            preserved(before, stored, Set.of("status", "moderated_by", "moderation_reason"));
            assertThat(commentRow(child)).isEqualTo(childBefore);
            JsonNode publicTree = ok(get("/api/public/community/posts/{id}/comments", post));
            JsonNode placeholder = null;
            for (JsonNode entry : publicTree) if (entry.path("id").asLong() == target) placeholder = entry;
            assertThat(placeholder).isNotNull();
            assertThat(placeholder.path("content").isNull()).isTrue();
            assertThat(placeholder.path("author").isNull()).isTrue();
            assertAudit(id, moderator.id(), "COMMUNITY_REPORT_ACTION", Map.of("targetType", "COMMENT", "targetId", target, "commentAction", action));
        }
    }

    @Test
    void existingTargetStateIsNotRewrittenAndDeletedCommentsNeverReturnToHidden() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long hidden = postTarget(author.id(), "HIDDEN");
        jdbc.update("UPDATE community.posts SET reviewed_by=?,reviewed_at=? WHERE id=?", moderator.id(), Timestamp.from(ORIGINAL), hidden);
        long postCase = reportCase("POST", hidden, List.of(moderator), ORIGINAL);
        var postBefore = postRow(hidden);
        ok(decide(postCase, body("ACTION", "New case note", null)).header("Authorization", moderatorToken(moderator)));
        assertThat(postRow(hidden)).isEqualTo(postBefore);
        assertAudit(postCase, moderator.id(), "COMMUNITY_REPORT_ACTION", Map.of("targetType", "POST", "targetId", hidden));
        for (String initial : List.of("HIDDEN", "DELETED")) {
            for (String action : List.of("HIDE", "DELETE")) {
                long target = commentTarget(hidden, author.id(), initial, null);
                long id = reportCase("COMMENT", target, List.of(moderator), ORIGINAL);
                var before = commentRow(target);
                JsonNode result = ok(decide(id, body("ACTION", "New case note", action)).header("Authorization", moderatorToken(moderator)));
                boolean noOp = initial.equals("DELETED") || action.equals("HIDE");
                if (noOp) assertThat(commentRow(target)).isEqualTo(before);
                else assertThat(commentRow(target).get("status")).isEqualTo("DELETED");
                assertThat(result.path("status").asText()).isEqualTo("ACTIONED");
                assertAudit(id, moderator.id(), "COMMUNITY_REPORT_ACTION", Map.of("targetType", "COMMENT", "targetId", target, "commentAction", action));
            }
        }
    }

    @Test
    void rejectionLeavesTargetUntouchedAndAnotherReporterCanOpenANewPendingCase() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        Account nextReporter = register("Next reporter");
        verifyEmail(nextReporter);
        long target = postTarget(author.id(), "APPROVED");
        long id = reportCase("POST", target, List.of(moderator), ORIGINAL);
        var before = postRow(target);
        String token = moderatorToken(moderator);
        JsonNode result = ok(decide(id, body("REJECT", "Not a violation", null)).header("Authorization", token));
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(postRow(target)).isEqualTo(before);
        error(decide(id, body("REJECT", "Again", null)).header("Authorization", token), 409, "REPORT_ALREADY_HANDLED");
        assertAudit(id, moderator.id(), "COMMUNITY_REPORT_REJECT", Map.of("targetType", "POST", "targetId", target));
        mvc.perform(post("/api/community/reports").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("targetType", "POST", "targetId", target, "reason", "SPAM")))
                .header("Authorization", bearer(nextReporter, Set.of("LEARNER"), true))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE target_type='POST' AND target_id=?", Long.class, target)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE target_type='POST' AND target_id=? AND status='PENDING'", Long.class, target)).isEqualTo(1L);
    }

    @Test
    void rejectingEveryPostAndCommentStateNeverRestoresOrRewritesTheTarget() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long parent = postTarget(author.id(), "HIDDEN");
        for (String type : List.of("POST", "COMMENT")) {
            List<String> states = type.equals("POST") ? List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN") : List.of("VISIBLE", "HIDDEN", "DELETED");
            for (String state : states) {
                long target = type.equals("POST") ? postTarget(author.id(), state) : commentTarget(parent, author.id(), state, null);
                long id = reportCase(type, target, List.of(moderator), ORIGINAL);
                Map<String, Object> before = type.equals("POST") ? postRow(target) : commentRow(target);
                JsonNode result = ok(decide(id, body("REJECT", "Not a violation", null)).header("Authorization", moderatorToken(moderator)));
                assertThat(result.path("target").path("status").asText()).isEqualTo(state);
                assertThat(type.equals("POST") ? postRow(target) : commentRow(target)).isEqualTo(before);
                assertAudit(id, moderator.id(), "COMMUNITY_REPORT_REJECT", Map.of("targetType", type, "targetId", target));
            }
        }
    }

    @Test
    void orphanAndUnsupportedTargetsFailSafelyForQueueDetailAndDecisionWithoutAuditOrPrivateLogs() throws Exception {
        Account moderator = register("Moderator");
        Account reporter = register("Private reporter");
        String token = moderatorToken(moderator);
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(ReportCaseService.class);
        Logger errorLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        errorLogger.addAppender(appender);
        try {
            for (String type : List.of("POST", "COMMENT", "COLLECTION")) {
                clean();
                long id = reportCase(type, Long.MAX_VALUE, List.of(moderator, reporter), ORIGINAL);
                var before = caseRow(id);
                error(get(BASE).header("Authorization", token), 500, "INTERNAL_ERROR");
                error(get(BASE + "/{id}", id).header("Authorization", token), 500, "INTERNAL_ERROR");
                error(decide(id, body("ACTION", "Note", "HIDE")).header("Authorization", token), 500, "INTERNAL_ERROR");
                assertThat(caseRow(id)).isEqualTo(before);
                assertThat(auditRows(id)).isEmpty();
            }
            assertThat(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR)).as("orphan emits ERROR").isTrue();
            List<ILoggingEvent> serviceErrors = appender.list.stream().filter(event -> event.getLoggerName().equals(ReportCaseService.class.getName()) && event.getLevel() == Level.ERROR).toList();
            assertThat(serviceErrors.size()).isEqualTo(9);
            for (ILoggingEvent event : serviceErrors) {
                assertThat(event.getFormattedMessage().matches("Report case target unavailable caseId=[0-9]+ targetType=(POST|COMMENT|COLLECTION)"))
                        .as("service error includes only the case ID and target type").isTrue();
            }
            for (ILoggingEvent event : appender.list) {
                String message = event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage());
                for (String privateValue : List.of(moderator.email(), reporter.email(), PASSWORD, "Private synthetic title", "Private synthetic body", "Private synthetic comment", "Reporter private detail")) {
                    assertThat(message.contains(privateValue)).as("scoped ERROR log excludes private values").isFalse();
                }
            }
        } finally {
            serviceLogger.detachAppender(appender);
            errorLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void mixedQueueUsesConstantBatchedQueriesAndDetailResolvesAllNamesInOneAuthBatch() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        Account reporter = register("Reporter");
        String token = moderatorToken(moderator);
        Captured empty = captured(BASE, token, 200);
        assertThat(empty.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("count(", "community.moderation_cases"));
        long parent = postTarget(author.id(), "APPROVED");
        for (int index = 0; index < 25; index++) {
            String type = index % 2 == 0 ? "POST" : "COMMENT";
            long target = type.equals("POST") ? postTarget(author.id(), "APPROVED") : commentTarget(parent, author.id(), "VISIBLE", null);
            reportCase(type, target, List.of(reporter), ORIGINAL.plusSeconds(index));
        }
        List<Captured> pages = new ArrayList<>();
        for (int size : List.of(5, 20)) {
            clearInvocations(auth);
            Captured result = captured(BASE + "?size=" + size, token, 200);
            pages.add(result);
            assertThat(result.body().path("items")).hasSize(size);
            assertThat(result.body().path("totalElements").asLong()).isEqualTo(25);
            assertThat(result.sql()).hasSize(4);
            assertThat(result.sql().get(0)).contains("count(", "community.moderation_cases");
            assertThat(result.sql().get(1)).contains("community.moderation_cases", "pending", "order by")
                    .matches(".*order by.*report_count desc.*created_at.*id.*");
            assertThat(result.sql().stream().filter(sql -> sql.contains("community.posts")).toList()).hasSize(1);
            assertThat(result.sql().stream().filter(sql -> sql.contains("community.comments")).toList()).hasSize(1);
            for (String table : List.of("community.posts", "community.comments")) {
                assertThat(result.sql().stream().filter(sql -> sql.contains(table)).toList()).singleElement()
                        .satisfies(sql -> assertThat(sql).contains(" in "));
            }
            assertThat(result.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select").doesNotContain("for update", "auth.users", "community.reactions"));
            verify(auth, never()).displayNames(anySet());
        }
        assertThat(pages.get(0).sql()).hasSameSizeAs(pages.get(1).sql());
        for (String page : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            Captured result = captured(BASE + "?page=" + page + "&size=50", token, 200);
            assertThat(result.body().path("items")).isEmpty();
            assertThat(result.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("count(", "community.moderation_cases"));
        }
        long id = reportCase("COMMENT", commentTarget(parent, author.id(), "DELETED", null), List.of(reporter), ORIGINAL);
        closeFixture(id, moderator, "ACTIONED");
        clearInvocations(auth);
        Captured detail = captured(BASE + "/" + id, token, 200);
        assertThat(detail.sql()).hasSize(4);
        verify(auth).displayNames(Set.of(author.id(), reporter.id(), moderator.id()));
        assertThat(detail.sql().stream().filter(sql -> sql.contains("auth.users")).toList()).hasSize(1);
        assertThat(detail.body().path("reports")).hasSize(1);
        long postCase = reportCase("POST", postTarget(author.id(), "HIDDEN"), List.of(reporter), ORIGINAL);
        clearInvocations(auth);
        Captured postDetail = captured(BASE + "/" + postCase, token, 200);
        assertThat(postDetail.sql()).hasSize(3);
        verify(auth).displayNames(Set.of(author.id(), reporter.id()));
        clearInvocations(auth);
        Captured missing = captured(BASE + "/" + Long.MAX_VALUE, token, 404);
        assertThat(missing.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("community.moderation_cases"));
        verify(auth, never()).displayNames(anySet());
    }

    @Test
    void existingPostModerationRoutesKeepTheirBehaviorAfterControllerPrefixChange() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        long approve = postTarget(author.id(), "PENDING");
        long reject = postTarget(author.id(), "PENDING");
        ok(get(POSTS).header("Authorization", token));
        ok(get(POSTS + "/{id}", approve).header("Authorization", token));
        assertThat(ok(patch(POSTS + "/{id}/approve", approve).header("Authorization", token)).path("status").asText()).isEqualTo("APPROVED");
        error(patch(POSTS + "/{id}/reject", reject).contentType(MediaType.APPLICATION_JSON).content("{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        assertThat(ok(patch(POSTS + "/{id}/reject", reject).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reason\"}")
                .header("Authorization", token)).path("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void generatedOpenApiPreservesAllSevenModerationOperationsAndExactLockedReportSchemas() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        String snapshot = System.getProperty("community.openapi.snapshot");
        if (snapshot != null) Files.writeString(Path.of(snapshot), document.toPrettyString(), StandardCharsets.UTF_8);
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        operation(document, BASE, "get", "listPendingReportCases", "ReportCasePage", List.of("200", "401", "403", "422"));
        operation(document, BASE + "/{id}", "get", "getReportCase", "ReportCaseDetail", List.of("200", "401", "403", "404"));
        JsonNode decision = operation(document, BASE + "/{id}", "patch", "decideReportCase", "ReportCaseDetail", List.of("200", "401", "403", "404", "409", "422"));
        operation(document, POSTS, "get", "listPendingPosts", "ModerationPostPage", List.of("200", "401", "403", "422"));
        operation(document, POSTS + "/{id}", "get", "getModerationPost", "ModerationPost", List.of("200", "401", "403", "404"));
        JsonNode approve = operation(document, POSTS + "/{id}/approve", "patch", "approvePost", "ModerationPost", List.of("200", "401", "403", "404", "409"));
        JsonNode reject = operation(document, POSTS + "/{id}/reject", "patch", "rejectPost", "ModerationPost", List.of("200", "401", "403", "404", "409", "422"));
        assertThat(approve.has("requestBody")).isFalse();
        assertThat(reject.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/RejectWrite");
        assertThat(decision.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(fields(decision.path("requestBody").path("content"))).containsExactly("application/json");
        assertThat(decision.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ReportDecision");
        JsonNode schemas = document.path("components").path("schemas");
        schema(schemas.path("ReportCaseSummary"), SUMMARY, SUMMARY);
        schema(schemas.path("ReportCasePage"), List.of("items", "page", "size", "totalElements"), List.of("items", "page", "size", "totalElements"));
        schema(schemas.path("ReportCaseDetail"), DETAIL, List.of("id", "status", "reportCount", "target", "reports", "createdAt"));
        schema(schemas.path("ReportedPost"), POST_TARGET, POST_TARGET);
        schema(schemas.path("ReportedComment"), COMMENT_TARGET, COMMENT_TARGET);
        schema(schemas.path("ReportEntry"), List.of("reporter", "reason", "detail", "reportedAt"), List.of("reporter", "reason", "detail", "reportedAt"));
        schema(schemas.path("ReportDecision"), List.of("decision", "note", "commentAction"), List.of("decision", "note"));
        assertThat(schemas.has("ReportEntryResponse")).isFalse();
        JsonNode summary = schemas.path("ReportCaseSummary").path("properties");
        ref(summary.path("targetType"), "ReportTargetType");
        ref(summary.path("latestReason"), "ReportReason");
        assertThat(strings(schemas.path("ReportTargetType").path("enum"))).containsExactlyInAnyOrder("POST", "COMMENT");
        assertThat(strings(schemas.path("ReportReason").path("enum"))).containsExactlyInAnyOrder("SPAM", "OFFENSIVE", "HARASSMENT", "COPYRIGHT", "OTHER");
        assertThat(strings(schemas.path("CaseStatus").path("enum"))).containsExactlyInAnyOrder("PENDING", "ACTIONED", "REJECTED");
        assertThat(summary.path("targetPreview").path("maxLength").asInt()).isEqualTo(120);
        JsonNode detail = schemas.path("ReportCaseDetail").path("properties");
        ref(detail.path("status"), "CaseStatus");
        ref(detail.path("target"), "ReportTarget");
        JsonNode reportTarget = schemas.path("ReportTarget");
        assertThat(reportTarget.path("oneOf")).hasSize(2);
        List<String> targetRefs = new ArrayList<>();
        reportTarget.path("oneOf").forEach(part -> targetRefs.add(part.path("$ref").asText()));
        assertThat(targetRefs).containsExactlyInAnyOrder("#/components/schemas/ReportedPost", "#/components/schemas/ReportedComment");
        assertThat(reportTarget.path("discriminator").path("propertyName").asText()).isEqualTo("kind");
        assertThat(hasRef(detail.path("handledBy"), "#/components/schemas/UserRef")).isTrue();
        assertThat(types(detail.path("handledBy")).contains("null") || detail.path("handledBy").path("nullable").asBoolean()).isTrue();
        assertThat(types(detail.path("handledAt"))).containsExactlyInAnyOrder("string", "null");
        assertThat(detail.path("handledAt").path("format").asText()).isEqualTo("date-time");
        assertThat(types(detail.path("decisionNote"))).containsExactlyInAnyOrder("string", "null");
        ref(detail.path("reports").path("items"), "ReportEntry");
        ref(schemas.path("ReportCasePage").path("properties").path("items").path("items"), "ReportCaseSummary");
        for (String name : List.of("ReportedPost", "ReportedComment")) {
            JsonNode target = schemas.path(name).path("properties");
            assertThat(target.path("kind").path("const").asText()).isEqualTo(name.equals("ReportedPost") ? "POST" : "COMMENT");
            ref(target.path("author"), "UserRef");
            ref(target.path("status"), name.equals("ReportedPost") ? "PostStatus" : "CommentStatus");
            integer64(target.path("id"));
        }
        ref(schemas.path("ReportedPost").path("properties").path("category"), "PostCategory");
        integer64(schemas.path("ReportedComment").path("properties").path("postId"));
        integer64(summary.path("id"));
        integer64(summary.path("targetId"));
        integer64(detail.path("id"));
        integer64(schemas.path("ReportCasePage").path("properties").path("totalElements"));
        for (JsonNode integer : List.of(summary.path("reportCount"), detail.path("reportCount"), schemas.path("ReportCasePage").path("properties").path("page"), schemas.path("ReportCasePage").path("properties").path("size"))) {
            assertThat(integer.path("type").asText()).isEqualTo("integer");
        }
        JsonNode entry = schemas.path("ReportEntry").path("properties");
        ref(entry.path("reporter"), "UserRef");
        ref(entry.path("reason"), "ReportReason");
        assertThat(types(entry.path("detail"))).containsExactlyInAnyOrder("string", "null");
        JsonNode write = schemas.path("ReportDecision").path("properties");
        assertThat(strings(write.path("decision").path("enum"))).containsExactlyInAnyOrder("ACTION", "REJECT");
        assertThat(strings(write.path("commentAction").path("enum"))).containsExactlyInAnyOrder("HIDE", "DELETE");
        assertThat(types(write.path("commentAction"))).containsExactly("string");
        assertThat(write.path("note").path("minLength").asInt()).isEqualTo(1);
        assertThat(write.path("note").path("maxLength").asInt()).isEqualTo(1000);
        for (JsonNode timestamp : List.of(summary.path("createdAt"), detail.path("createdAt"), entry.path("reportedAt"))) {
            assertThat(types(timestamp)).containsExactly("string");
            assertThat(timestamp.path("format").asText()).isEqualTo("date-time");
        }
        schema(schemas.path("UserRef"), List.of("id", "displayName"), List.of("id", "displayName"));
        integer64(schemas.path("UserRef").path("properties").path("id"));
        assertThat(types(schemas.path("UserRef").path("properties").path("displayName"))).containsExactlyInAnyOrder("string", "null");
        assertThat(fields(schemas.path("ModerationPost").path("properties"))).containsExactlyInAnyOrder("id", "title", "content", "category", "status", "author", "reviewReason", "reviewedBy", "reviewedAt", "publishedAt", "createdAt", "updatedAt");
    }

    private JsonNode operation(JsonNode document, String path, String method, String operationId, String response, List<String> statuses) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.path("operationId").asText()).isEqualTo(operationId);
        assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrderElementsOf(statuses);
        for (String code : statuses) ref(operation.path("responses").path(code).path("content").path("application/json").path("schema"), code.equals("200") ? response : "ErrorResponse");
        JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
        assertThat(security).hasSize(2);
        List<List<String>> alternatives = new ArrayList<>();
        security.forEach(alternative -> { assertThat(alternative).hasSize(1); alternative.forEach(scopes -> assertThat(scopes).isEmpty()); alternatives.add(fields(alternative)); });
        assertThat(alternatives).containsExactlyInAnyOrder(List.of("cookieAuth"), List.of("bearerAuth"));
        if (path.contains("{id}")) {
            JsonNode id = parameter(operation, "id");
            assertThat(id.path("in").asText()).isEqualTo("path");
            assertThat(id.path("required").asBoolean()).isTrue();
            integer64(id.path("schema"));
        } else {
            for (String name : List.of("page", "size")) {
                JsonNode param = parameter(operation, name);
                assertThat(param.path("in").asText()).isEqualTo("query");
                assertThat(param.path("schema").path("type").asText()).isEqualTo("integer");
                assertThat(param.path("schema").path("default").asInt()).isEqualTo(name.equals("page") ? 0 : 20);
                assertThat(param.path("schema").path("minimum").asInt()).isEqualTo(name.equals("page") ? 0 : 1);
                if (name.equals("size")) assertThat(param.path("schema").path("maximum").asInt()).isEqualTo(50);
            }
        }
        return operation;
    }

    private static JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode candidate : operation.path("parameters")) if (candidate.path("name").asText().equals(name)) return candidate;
        throw new AssertionError("Required operation parameter missing: " + name);
    }
    private static void schema(JsonNode schema, List<String> properties, List<String> required) {
        assertThat(fields(schema.path("properties"))).containsExactlyInAnyOrderElementsOf(properties);
        assertThat(strings(schema.path("required"))).containsExactlyInAnyOrderElementsOf(required);
    }
    private static void integer64(JsonNode schema) { assertThat(schema.path("type").asText()).isEqualTo("integer"); assertThat(schema.path("format").asText()).isEqualTo("int64"); }
    private static void ref(JsonNode schema, String name) { assertThat(schema.path("$ref").asText()).isEqualTo("#/components/schemas/" + name); }
    private void assertDetail(JsonNode result, long id, String state, List<Account> reporters) {
        assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(DETAIL);
        assertThat(result.path("target").isObject()).isTrue();
        assertThat(result.path("target").has("ReportTarget")).isFalse();
        assertThat(result.path("id").asLong()).isEqualTo(id);
        assertThat(result.path("status").asText()).isEqualTo(state);
        assertThat(result.path("reportCount").asInt()).isEqualTo(reporters.size());
        assertThat(result.path("reports")).hasSize(reporters.size());
        for (int index = 0; index < reporters.size(); index++) {
            JsonNode entry = result.path("reports").get(index);
            assertThat(fields(entry)).containsExactlyInAnyOrder("reporter", "reason", "detail", "reportedAt");
            assertRef(entry.path("reporter"), reporters.get(index));
            assertThat(entry.path("reason").asText()).isEqualTo(List.of("SPAM", "HARASSMENT", "OTHER").get(index % 3));
            assertThat(Instant.parse(entry.path("reportedAt").asText())).isEqualTo(ORIGINAL.plusSeconds(index));
            assertThat(entry.has("detail")).isTrue();
            if (index == 0) assertThat(entry.path("detail").isNull()).isTrue();
            else assertThat(entry.path("detail").asText()).isEqualTo("Reporter private detail");
        }
        if (state.equals("PENDING")) {
            assertThat(result.path("handledBy").isNull()).isTrue();
            assertThat(result.path("handledAt").isNull()).isTrue();
            assertThat(result.path("decisionNote").isNull()).isTrue();
        } else {
            assertThat(result.path("handledBy").path("id").asLong()).isPositive();
            assertThat(result.path("handledBy").path("displayName").asText()).isEqualTo("Moderator");
            assertThat(Instant.parse(result.path("handledAt").asText())).isEqualTo(ORIGINAL);
            assertThat(result.path("decisionNote").asText()).isEqualTo("Stored case note");
        }
        assertPrivacy(result);
    }
    private static void assertRef(JsonNode ref, Account account) { assertThat(fields(ref)).containsExactlyInAnyOrder("id", "displayName"); assertThat(ref.path("id").asLong()).isEqualTo(account.id()); assertThat(ref.path("displayName").asText()).isEqualTo(account.name()); }
    private static void assertPrivacy(JsonNode node) {
        if (node.isObject()) assertThat(fields(node)).doesNotContain("userId", "email", "roles", "password", "passwordHash", "authorId", "handledById", "reasons", "autoHidden");
        if (node.isContainerNode()) node.forEach(ReportCaseControllerIntegrationTest::assertPrivacy);
    }
    private void assertAudit(long id, long actor, String action, Map<String, ?> detail) throws Exception {
        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("actor_id")).longValue()).isEqualTo(actor);
        assertThat(rows.get(0).get("action")).isEqualTo(action);
        assertThat(rows.get(0).get("target_type")).isEqualTo("REPORT_CASE");
        assertThat(mapper.readTree(rows.get(0).get("detail").toString())).isEqualTo(mapper.readTree(mapper.writeValueAsString(detail)));
    }
    private List<Map<String, Object>> auditRows(long id) { return jdbc.queryForList("SELECT * FROM shared.audit_logs WHERE target_type='REPORT_CASE' AND target_id=? ORDER BY id", id); }
    private Map<String, Object> caseRow(long id) { return jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", id); }
    private Map<String, Object> postRow(long id) { return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id); }
    private Map<String, Object> commentRow(long id) { return jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id); }
    private static void preserved(Map<String, Object> before, Map<String, Object> after, Set<String> changed) { before.forEach((key, value) -> { if (!changed.contains(key)) assertThat(after.get(key)).as("preserved column %s", key).isEqualTo(value); }); }
    private long postTarget(long author, String state) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) VALUES (?,'QUESTION','Private synthetic title','Private synthetic body',?,'Stored reason',?,?,?) RETURNING id",
                Long.class, author, state, Timestamp.from(ORIGINAL.minusSeconds(1)), Timestamp.from(ORIGINAL), Timestamp.from(ORIGINAL.plusSeconds(3)));
    }
    private long commentTarget(long post, long author, String state, Long parent) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,status,moderation_reason,created_at,updated_at) VALUES (?,?,?,'Private synthetic comment',?,?,'Stored reason',?,?) RETURNING id",
                Long.class, post, parent, author, parent == null ? 0 : 1, state, Timestamp.from(ORIGINAL), Timestamp.from(ORIGINAL.plusSeconds(3)));
    }
    private long reportCase(String type, long target, List<Account> reporters, Instant created) throws Exception {
        ArrayNode reasons = mapper.createArrayNode();
        for (int index = 0; index < reporters.size(); index++) {
            ObjectNode entry = reasons.addObject().put("userId", reporters.get(index).id()).put("reason", List.of("SPAM", "HARASSMENT", "OTHER").get(index % 3))
                    .put("reportedAt", ORIGINAL.plusSeconds(index).toString());
            if (index == 0) entry.putNull("detail"); else entry.put("detail", "Reporter private detail");
        }
        return jdbc.queryForObject("INSERT INTO community.moderation_cases(target_type,target_id,report_count,reasons,created_at) VALUES (?,?,?,CAST(? AS jsonb),?) RETURNING id",
                Long.class, type, target, reporters.size(), mapper.writeValueAsString(reasons), Timestamp.from(created));
    }
    private void closeFixture(long id, Account moderator, String state) { jdbc.update("UPDATE community.moderation_cases SET status=?,handled_by=?,handled_at=?,decision_note='Stored case note' WHERE id=?", state, moderator.id(), Timestamp.from(ORIGINAL), id); }
    private Account register(String name) throws Exception {
        String email = "report-case-" + UUID.randomUUID() + "@example.test";
        JsonNode result = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD, "displayName", name, "acceptTerms", true)))).andExpect(status().isCreated()).andReturn());
        assertThat(result.path("userId").asLong()).isPositive();
        return new Account(result.path("userId").asLong(), email, name);
    }
    private void verifyEmail(Account account) throws Exception {
        JsonNode found = null;
        for (JsonNode message : mail("/api/v1/messages").path("messages")) {
            JsonNode full = mail("/api/v1/message/" + message.path("ID").asText());
            if (full.path("To").toString().contains(account.email())) { found = full; break; }
        }
        assertThat(found != null).as("verification mail exists").isTrue();
        var token = VERIFY_TOKEN.matcher(found.path("Text").asText());
        assertThat(token.find()).as("verification token exists").isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("token", token.group(1))))).andExpect(status().isOk());
    }
    private JsonNode mail(String path) throws Exception { HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString()); assertThat(response.statusCode()).isEqualTo(200); return mapper.readTree(response.body()); }
    private Session login(Account account) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email", account.email(), "password", PASSWORD)))).andExpect(status().isOk()).andReturn();
        Cookie access = result.getResponse().getCookie("access_token"); Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(access).isNotNull(); assertThat(csrf).isNotNull(); return new Session(access, csrf);
    }
    private String moderatorToken(Account account) { return bearer(account, Set.of("MODERATOR"), false); }
    private String bearer(Account account, Set<String> roles, boolean verified) { return "Bearer " + jwt.issueAccessToken(account.id(), roles, verified, UUID.randomUUID()); }
    private String body(String decision, String note, String action) throws Exception { ObjectNode body = mapper.createObjectNode().put("decision", decision).put("note", note); if (action != null) body.put("commentAction", action); return mapper.writeValueAsString(body); }
    private MockHttpServletRequestBuilder decide(long id, String body) { return patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body); }
    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return read(mvc.perform(request).andExpect(status().isOk()).andReturn()); }
    private JsonNode error(MockHttpServletRequestBuilder request, int expected, String code) throws Exception {
        JsonNode result = read(mvc.perform(request).andExpect(status().is(expected)).andReturn());
        assertThat(result.path("error_code").asText()).isEqualTo(code);
        assertThat(result.path("message").asText()).isNotBlank(); assertThat(result.path("request_id").asText()).isNotBlank();
        assertThat(result.toString()).doesNotContain("stackTrace", "Exception", "org.hibernate", "java.lang", "SELECT ", "UPDATE "); assertPrivacy(result); return result;
    }
    private JsonNode read(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private Captured captured(String path, String token, int expected) throws Exception { capture.start(); try { JsonNode body = read(mvc.perform(get(path).header("Authorization", token)).andExpect(status().is(expected)).andReturn()); return new Captured(body, capture.statements()); } finally { capture.stop(); } }
    private static List<Long> ids(JsonNode page) { List<Long> ids = new ArrayList<>(); page.path("items").forEach(item -> ids.add(item.path("id").asLong())); return ids; }
    private static List<String> fields(JsonNode node) { return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList(); }
    private static List<String> strings(JsonNode node) { List<String> values = new ArrayList<>(); node.forEach(value -> values.add(value.asText())); return values; }
    private static Set<String> types(JsonNode schema) { Set<String> result = new HashSet<>(); JsonNode type = schema.path("type"); if (type.isTextual()) result.add(type.asText()); else if (type.isArray()) type.forEach(value -> result.add(value.asText())); for (String key : List.of("oneOf", "anyOf", "allOf")) schema.path(key).forEach(part -> result.addAll(types(part))); return result; }
    private static boolean hasRef(JsonNode schema, String ref) { if (schema.path("$ref").asText().equals(ref)) return true; for (String key : List.of("oneOf", "anyOf", "allOf")) for (JsonNode part : schema.path(key)) if (hasRef(part, ref)) return true; return false; }
    private record Account(long id, String email, String name) { }
    private record Session(Cookie access, Cookie csrf) { }
    private record Captured(JsonNode body, List<String> sql) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReportCaseConfiguration {
        @Bean @Primary ReportCaseClock reportCaseClock() { return new ReportCaseClock(); }
        @Bean SqlCapture reportCaseSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer reportCaseStatementInspector(SqlCapture capture) { return properties -> properties.put("hibernate.session_factory.statement_inspector", capture); }
    }
    static class ReportCaseClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void set(Instant now) { instant.set(now); } void reset() { instant.set(null); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { Instant value = instant.get(); return value == null ? Instant.now() : value; }
    }
    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); } void stop() { active.remove(); } List<String> statements() { return List.copyOf(active.get()); }
        @Override public String inspect(String sql) { List<String> statements = active.get(); if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).strip()); return sql; }
    }
}
