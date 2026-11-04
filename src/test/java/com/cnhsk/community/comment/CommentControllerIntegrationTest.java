package com.cnhsk.community.comment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(CommentControllerIntegrationTest.CommentClockConfiguration.class)
class CommentControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD = "CommentTestPass123";
    private static final String DISPLAY_NAME = "Synthetic Comment Author";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final Set<String> COMMENT_FIELDS = Set.of("id", "postId", "parentId", "depth", "status",
            "content", "author", "createdAt", "editedAt");
    private static final List<String> NONPUBLIC = List.of("DRAFT", "PENDING", "REJECTED", "HIDDEN");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private CommentClock clock;
    @Autowired private MutationSqlCapture sqlCapture;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    void resetClockAndMail() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        HttpResponse<Void> result = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitUrl + "/api/v1/messages")).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(result.statusCode()).isBetween(200, 299);
    }

    @AfterEach
    void restoreClock() {
        clock.reset();
    }

    @Test
    void writesRequireAuthenticationLearnerEmailAndRealCookieCsrfBeforeTargetOrQuotaChecks() throws Exception {
        Account unverified = register();
        Session pending = login(unverified.email());
        long postId = seedPost(unverified.id(), "APPROVED");
        long parentId = seedComment(postId, null, unverified.id(), 0, "VISIBLE", clock.instant());
        for (String path : List.of(rootPath(postId), replyPath(parentId))) {
            error(post(path).contentType(MediaType.APPLICATION_JSON).content("not-json"), 401, "UNAUTHENTICATED");
            error(post(path).cookie(pending.access()).contentType(MediaType.APPLICATION_JSON).content("not-json"),
                    403, "CSRF_TOKEN_INVALID");
            error(cookieWrite(path, pending, body("Body")), 403, "ACCOUNT_UNVERIFIED");
            error(cookieWrite(path, pending, body("\u3000")), 422, "VALIDATION_FAILED");
            for (String role : List.of("ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
                String token = jwt.issueAccessToken(unverified.id(), Set.of(role), true, UUID.randomUUID());
                error(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Body")), 403, "FORBIDDEN");
            }
        }
        verifyEmail(unverified);
        Session verified = login(unverified.email());
        created(cookieWrite(rootPath(postId), verified, body("Cookie root")));
        created(post(replyPath(parentId)).header("Authorization", "Bearer " + verified.access().getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body("Bearer reply")));
        String mixed = jwt.issueAccessToken(unverified.id(), Set.of("ADMIN", "LEARNER"), true, UUID.randomUUID());
        created(post(rootPath(postId)).header("Authorization", "Bearer " + mixed)
                .contentType(MediaType.APPLICATION_JSON).content(body("Learner with another role")));
    }

    @Test
    void rootWritesRequirePublishedPostsAndMissingTargetsWinOverAFullQuota() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long approved = seedPost(owner.id(), "APPROVED");
        JsonNode root = created(cookieWrite(rootPath(approved), session, body("Root")));
        assertThat(root.path("depth").asInt()).isZero();
        assertThat(root.path("parentId").isNull()).isTrue();
        for (int i = 1; i < 30; i++) seedComment(approved, null, owner.id(), 0, "VISIBLE", clock.instant());
        JsonNode missing = error(cookieWrite(rootPath(Long.MAX_VALUE), session, body("Body")), 404, "POST_NOT_FOUND");
        long count = commentCount(owner.id());
        for (String state : NONPUBLIC) {
            long id = seedPost(owner.id(), state);
            JsonNode nonpublic = error(cookieWrite(rootPath(id), session, body("Body")), 404, "POST_NOT_FOUND");
            assertThat(withoutRequestId(nonpublic)).isEqualTo(withoutRequestId(missing));
        }
        assertThat(commentCount(owner.id())).isEqualTo(count);
        error(cookieWrite(rootPath(approved), session, body("Body")), 429, "TOO_MANY_REQUESTS");
    }

    @Test
    void missingAndNonpublicReplyParentsHaveTheSameSafeErrorEvenWhenQuotaIsFull() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long approved = seedPost(owner.id(), "APPROVED");
        for (int i = 0; i < 30; i++) seedComment(approved, null, owner.id(), 0, "VISIBLE", clock.instant());
        JsonNode missing = error(cookieWrite(replyPath(Long.MAX_VALUE), session, body("Reply")), 404, "COMMENT_NOT_FOUND");
        for (String state : NONPUBLIC) {
            long postId = seedPost(owner.id(), state);
            long parent = seedComment(postId, null, owner.id(), 0, "VISIBLE", clock.instant());
            var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", parent);
            JsonNode nonpublic = error(cookieWrite(replyPath(parent), session, body("Reply")), 404, "COMMENT_NOT_FOUND");
            assertThat(withoutRequestId(nonpublic)).isEqualTo(withoutRequestId(missing));
            assertThat(nonpublic.toString()).doesNotContain("Private parent content", "Synthetic fixture");
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", parent)).isEqualTo(before);
        }
    }

    @Test
    void repliesBuildThreeLevelsThenAttachToTheSameParentWithoutChangingExistingRows() throws Exception {
        Account author = verifiedAccount();
        Account other = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(other.id(), "APPROVED");
        var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        JsonNode root = created(cookieWrite(rootPath(postId), session, body("Root")));
        long previous = root.path("id").asLong();
        long secondLevel = 0;
        for (int depth = 1; depth <= 3; depth++) {
            var parentBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", previous);
            JsonNode reply = created(cookieWrite(replyPath(previous), session, body("Reply " + depth)));
            assertComment(reply, author.id(), postId, previous, depth, "Reply " + depth);
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", previous)).isEqualTo(parentBefore);
            previous = reply.path("id").asLong();
            if (depth == 2) secondLevel = previous;
        }
        var deepestBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", previous);
        JsonNode sibling = created(cookieWrite(replyPath(previous), session, mapper.writeValueAsString(Map.of(
                "content", "Sibling", "postId", Long.MAX_VALUE, "parentId", Long.MAX_VALUE,
                "depth", 19, "authorId", other.id(), "status", "HIDDEN", "editedAt", "2000-01-01T00:00:00Z"))));
        assertComment(sibling, author.id(), postId, secondLevel, 3, "Sibling");
        var saved = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", sibling.path("id").asLong());
        assertThat(((Number) saved.get("post_id")).longValue()).isEqualTo(postId);
        assertThat(((Number) saved.get("parent_id")).longValue()).isEqualTo(secondLevel);
        assertThat(((Number) saved.get("author_id")).longValue()).isEqualTo(author.id());
        assertThat(saved.get("moderated_by")).isNull();
        assertThat(saved.get("moderation_reason")).isNull();
        assertThat(saved.get("created_at")).isEqualTo(saved.get("updated_at"));
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", previous)).isEqualTo(deepestBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
    }

    @Test
    void everyParentStatusAcceptsRepliesWithoutDisclosingParentContentOrModerationMetadata() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long postId = seedPost(owner.id(), "APPROVED");
        for (String state : List.of("VISIBLE", "HIDDEN", "DELETED")) {
            long parent = seedComment(postId, null, owner.id(), 0, state, clock.instant().minusSeconds(20));
            var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", parent);
            JsonNode result = created(cookieWrite(replyPath(parent), session, body("Public reply")));
            assertComment(result, owner.id(), postId, parent, 1, "Public reply");
            assertThat(result.toString()).doesNotContain("Private parent content", "Private moderation reason");
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", parent)).isEqualTo(before);
        }
    }

    @Test
    void contentRoundTripsLiteralMarkupAndIgnoresEveryForgedRootField() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long postId = seedPost(owner.id(), "APPROVED");
        String literal = "<img src=x onerror=alert(1)>\n**đậm**";
        ObjectNode request = mapper.createObjectNode().put("content", "\u3000 " + literal + " \u3000")
                .put("id", -1).put("authorId", -42).put("postId", -3).put("parentId", -4).put("depth", 3)
                .put("status", "DELETED").put("moderatedBy", -7).put("moderationReason", "Forged")
                .put("createdAt", "2000-01-01T00:00:00Z").put("editedAt", "2000-01-01T00:00:00Z");
        JsonNode result = created(cookieWrite(rootPath(postId), session, mapper.writeValueAsString(request)));
        assertComment(result, owner.id(), postId, null, 0, literal);
        var row = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", result.path("id").asLong());
        assertThat(row.get("content")).isEqualTo(literal);
        assertThat(row.get("parent_id")).isNull();
        assertThat(row.get("moderated_by")).isNull();
        assertThat(row.get("moderation_reason")).isNull();
        assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(owner.id());
        assertThat(row.get("created_at")).isEqualTo(Timestamp.from(clock.instant()));
        assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));
    }

    @Test
    void validationStripsUnicodeWhitespaceAndMeasuresTheFiveThousandUtf16UnitBoundary() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long postId = seedPost(owner.id(), "APPROVED");
        long parent = seedComment(postId, null, owner.id(), 0, "VISIBLE", clock.instant());
        for (String path : List.of(rootPath(postId), replyPath(parent))) {
            long before = commentCount(owner.id());
            for (String invalid : List.of("{}", "{\"content\":null}", body(""), body(" \t\r\n "),
                    body("\u3000\u2003"), body("x".repeat(5001)), body("😀".repeat(2501)), "not-json", "{\"content\":")) {
                JsonNode failure = error(cookieWrite(path, session, invalid), 422, "VALIDATION_FAILED");
                assertThat(fields(failure)).containsExactlyInAnyOrder("error_code", "message", "request_id", "details");
                assertThat(failure.toString()).doesNotContain("not-json", "JsonParseException", "HttpMessageNotReadableException",
                        "com.fasterxml.jackson", "stackTrace");
            }
            assertThat(commentCount(owner.id())).isEqualTo(before);
            assertThat(created(cookieWrite(path, session, body("汉".repeat(5000)))).path("content").asText())
                    .hasSize(5000);
            assertThat(created(cookieWrite(path, session, body("😀".repeat(2500)))).path("content").asText())
                    .hasSize(5000);
            assertThat(created(cookieWrite(path, session, body("\u3000汉\u3000"))).path("content").asText()).isEqualTo("汉");
        }
    }

    @Test
    void malformedAndOverflowPathsAreValidationErrorsWhileNumericMissingIdsAreNotFound() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        for (String template : List.of("/api/community/posts/%s/comments", "/api/community/comments/%s/replies")) {
            for (String id : List.of("not-a-number", "9223372036854775808", "-9223372036854775809")) {
                error(cookieWrite(template.formatted(id), session, body("Body")), 422, "VALIDATION_FAILED");
            }
            for (String id : List.of("0", "-1", Long.toString(Long.MAX_VALUE))) {
                error(cookieWrite(template.formatted(id), session, body("Body")), 404,
                        template.contains("/posts/") ? "POST_NOT_FOUND" : "COMMENT_NOT_FOUND");
            }
        }
    }

    @Test
    void rootsAndRepliesShareGlobalQuotaAcrossPostsAndStatusesThenSameUserCanWriteAfterSixtyOneMinutes() throws Exception {
        Account owner = verifiedAccount();
        Session session = login(owner.email());
        long first = seedPost(owner.id(), "APPROVED");
        long second = seedPost(owner.id(), "APPROVED");
        long parent = seedComment(first, null, owner.id(), 0, "VISIBLE", clock.instant().minusSeconds(60));
        for (int i = 1; i < 30; i++) {
            long postId = i % 2 == 0 ? first : second;
            Long parentId = postId == first ? parent : null;
            seedComment(postId, parentId, owner.id(), parentId == null ? 0 : 1,
                    List.of("VISIBLE", "HIDDEN", "DELETED").get(i % 3), clock.instant().minusSeconds(60));
        }
        long countBefore = commentCount(owner.id());
        for (String path : List.of(rootPath(second), replyPath(parent))) {
            JsonNode failure = error(cookieWrite(path, session, body("Blocked")), 429, "TOO_MANY_REQUESTS");
            assertThat(failure.path("retryAfterSeconds").asLong()).isEqualTo(3540);
        }
        assertThat(commentCount(owner.id())).isEqualTo(countBefore);
        clock.set(clock.instant().plus(61, ChronoUnit.MINUTES));
        MvcResult refresh = mvc.perform(post("/api/auth/refresh").cookie(session.refresh()))
                .andExpect(status().isOk()).andReturn();
        Session renewed = new Session(cookie(refresh, "access_token"), cookie(refresh, "refresh_token"), session.csrf());
        assertThat(currentUserId(renewed)).isEqualTo(owner.id());
        JsonNode response = created(cookieWrite(replyPath(parent), renewed, body("After the window")));
        assertThat(response.path("author").path("id").asLong()).isEqualTo(owner.id());
        assertThat(commentCount(owner.id())).isEqualTo(countBefore + 1);
    }

    @Test
    void exactCutoffIsExcludedAndOneMicrosecondInsideTheWindowProducesOneSecondRetry() throws Exception {
        Account owner = verifiedAccount();
        Account other = verifiedAccount();
        Session session = login(owner.email());
        long postId = seedPost(owner.id(), "APPROVED");
        Instant cutoff = clock.instant().minus(60, ChronoUnit.MINUTES);
        long parent = seedComment(postId, null, owner.id(), 0, "VISIBLE", cutoff);
        seedComment(postId, null, owner.id(), 0, "HIDDEN", cutoff.plusNanos(1000));
        for (int i = 0; i < 28; i++) seedComment(postId, parent, owner.id(), 1, "VISIBLE", clock.instant().minusSeconds(60));
        for (int i = 0; i < 30; i++) seedComment(postId, null, other.id(), 0, "VISIBLE", clock.instant());
        created(cookieWrite(rootPath(postId), session, body("Thirtieth active comment")));
        JsonNode blocked = error(cookieWrite(replyPath(parent), session, body("Blocked")), 429, "TOO_MANY_REQUESTS");
        assertThat(blocked.path("retryAfterSeconds").asLong()).isEqualTo(1);
        clock.set(clock.instant().plusNanos(1000));
        created(cookieWrite(replyPath(parent), session, body("Oldest is now exactly expired")));
    }

    @Test
    void generatedOpenApiMatchesBothLockedOperationsAndTheNineFieldCommentProjection() throws Exception {
        JsonNode document = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        for (String[] contract : List.of(new String[]{"/api/community/posts/{id}/comments", "createComment"},
                new String[]{"/api/community/comments/{id}/replies", "replyToComment"})) {
            JsonNode operation = document.path("paths").path(contract[0]).path("post");
            assertThat(operation.path("operationId").asText()).isEqualTo(contract[1]);
            assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrder("201", "401", "403", "404", "422", "429");
            assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
            assertThat(operation.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/CommentWrite");
            assertThat(operation.path("responses").path("201").path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/Comment");
            for (String code : List.of("401", "403", "404", "422", "429")) {
                assertThat(operation.path("responses").path(code).toString()).contains("#/components/schemas/ErrorResponse");
            }
            JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
            assertThat(security.isArray()).isTrue();
            assertThat(security.toString()).contains("cookieAuth", "bearerAuth");
        }
        JsonNode comment = document.path("components").path("schemas").path("Comment");
        assertThat(fields(comment.path("properties"))).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        assertThat(comment.path("required")).extracting(JsonNode::asText).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        JsonNode write = document.path("components").path("schemas").path("CommentWrite");
        assertThat(fields(write.path("properties"))).containsExactly("content");
        assertThat(write.path("properties").path("content").path("maxLength").asInt()).isEqualTo(5000);
    }

    @Test
    void mutationsAuthorizeBeforeMalformedBodyAndIdBindingForGuestsRolesCsrfAndUnverifiedEmail() throws Exception {
        Account unverified = register();
        Session pending = login(unverified.email());
        long postId = seedPost(unverified.id(), "APPROVED");
        long id = seedComment(postId, null, unverified.id(), 0, "VISIBLE", clock.instant());
        var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
        for (String method : List.of("PUT", "DELETE")) {
            for (String target : List.of(Long.toString(id), Long.toString(Long.MAX_VALUE), "not-a-number")) {
                error(mutation(method, target).contentType(MediaType.APPLICATION_JSON).content("not-json"),
                        401, "UNAUTHENTICATED");
                error(mutation(method, target).cookie(pending.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"), 403, "CSRF_TOKEN_INVALID");
                error(cookieMutation(method, target, pending, "not-json"), 403, "ACCOUNT_UNVERIFIED");
                error(cookieMutation(method, target, pending, body("\u3000")), 403, "ACCOUNT_UNVERIFIED");
                for (String role : List.of("ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
                    String token = jwt.issueAccessToken(unverified.id(), Set.of(role), true, UUID.randomUUID());
                    error(mutation(method, target).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("not-json"), 403, "FORBIDDEN");
                }
            }
        }
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(before);
        verifyEmail(unverified);
        Session verified = login(unverified.email());
        updated(cookieMutation("PUT", Long.toString(id), verified, body("Cookie update")));
        String mixed = jwt.issueAccessToken(unverified.id(), Set.of("ADMIN", "LEARNER"), true, UUID.randomUUID());
        updated(put(commentPath(id)).header("Authorization", "Bearer " + mixed)
                .contentType(MediaType.APPLICATION_JSON).content(body("Mixed-role bearer update")));
        mvc.perform(delete(commentPath(id)).header("Authorization", "Bearer " + verified.access().getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void mutationsReturnIdenticalGenericForbiddenForMissingForeignMalformedAndOverflowIds() throws Exception {
        Account caller = verifiedAccount();
        Account other = verifiedAccount();
        Session session = login(caller.email());
        long postId = seedPost(other.id(), "APPROVED");
        List<String> targets = new ArrayList<>(List.of("0", "-1", Long.toString(Long.MAX_VALUE), "not-a-number",
                "9223372036854775808", "-9223372036854775809"));
        List<Map<String, Object>> before = new ArrayList<>();
        for (String state : List.of("VISIBLE", "HIDDEN", "DELETED")) {
            long id = seedComment(postId, null, other.id(), 0, state, clock.instant().minusSeconds(3600));
            targets.add(Long.toString(id));
            before.add(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id));
        }
        JsonNode expected = null;
        for (String method : List.of("PUT", "DELETE")) {
            for (String target : targets) {
                JsonNode response = error(cookieMutation(method, target, session, body("Attempted overwrite")), 403, "FORBIDDEN");
                assertThat(fields(response)).containsExactlyInAnyOrder("error_code", "message", "request_id");
                assertThat(response.path("message").asText()).isEqualTo("Bạn không có quyền thực hiện thao tác này.");
                assertThat(response.toString()).doesNotContain(caller.email(), other.email(), "Private parent content",
                        "Private moderation reason", "NumberFormatException", "MethodArgumentTypeMismatchException");
                if (expected == null) expected = withoutRequestId(response);
                assertThat(withoutRequestId(response)).isEqualTo(expected);
            }
        }
        for (Map<String, Object> row : before) {
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", row.get("id"))).isEqualTo(row);
        }
    }

    @Test
    void updateChangesOnlyContentAndDatabaseMicrosecondTimestampAndIgnoresAllForgedImmutableFields() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        long parent = seedComment(postId, null, author.id(), 0, "VISIBLE", clock.instant().minusSeconds(700));
        long id = seedComment(postId, parent, author.id(), 1, "VISIBLE", clock.instant().minusSeconds(600));
        jdbc.update("UPDATE community.comments SET moderated_by=? WHERE id=?", author.id(), id);
        var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
        var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        Instant rawNow = clock.instant().plusNanos(999);
        clock.set(rawNow);
        String literal = "<img src=x onerror=alert(1)>\n**đậm**";
        ObjectNode body = mapper.createObjectNode().put("content", "\u3000 " + literal + " \u3000")
                .put("id", -1).put("postId", -2).put("parentId", -3).put("authorId", -4).put("depth", 3)
                .put("status", "DELETED").put("moderatedBy", -5).put("moderationReason", "Forged reason")
                .put("createdAt", "2000-01-01T00:00:00Z").put("updatedAt", "2000-01-01T00:00:00Z")
                .put("editedAt", "2000-01-01T00:00:00Z");

        JsonNode response = updated(cookieMutation("PUT", Long.toString(id), session, mapper.writeValueAsString(body)));

        assertThat(response.path("id").asLong()).isEqualTo(id);
        assertThat(response.path("postId").asLong()).isEqualTo(postId);
        assertThat(response.path("parentId").asLong()).isEqualTo(parent);
        assertThat(response.path("depth").asInt()).isEqualTo(1);
        assertThat(response.path("status").asText()).isEqualTo("VISIBLE");
        assertThat(response.path("content").asText()).isEqualTo(literal);
        assertThat(fields(response.path("author"))).containsExactlyInAnyOrder("id", "displayName");
        assertThat(response.path("author").path("id").asLong()).isEqualTo(author.id());
        assertThat(response.path("author").path("displayName").asText()).isEqualTo(DISPLAY_NAME);
        assertThat(Instant.parse(response.path("createdAt").asText())).isEqualTo(((Timestamp) before.get("created_at")).toInstant());
        assertThat(Instant.parse(response.path("editedAt").asText())).isEqualTo(rawNow.truncatedTo(ChronoUnit.MICROS));
        assertThat(response.toString()).doesNotContain(author.email(), "Private moderation reason", "Forged reason");
        var expected = new java.util.HashMap<>(before);
        expected.put("content", literal);
        expected.put("updated_at", Timestamp.from(rawNow.truncatedTo(ChronoUnit.MICROS)));
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(expected);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
    }

    @Test
    void updateAllowsTenMinutesAndExactDeadlineButRejectsOneMicrosecondPastUsingPersistedCreationTime() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        Instant now = clock.instant();
        for (Instant createdAt : List.of(now.minusSeconds(600), now.minusSeconds(900).plusNanos(1000), now.minusSeconds(900))) {
            long id = seedComment(postId, null, author.id(), 0, "VISIBLE", createdAt);
            JsonNode response = updated(cookieMutation("PUT", Long.toString(id), session, body("Within deadline")));
            assertThat(Instant.parse(response.path("createdAt").asText())).isEqualTo(createdAt);
            assertThat(Instant.parse(response.path("editedAt").asText())).isEqualTo(now);
            assertThat(jdbc.queryForMap("SELECT created_at,updated_at FROM community.comments WHERE id=?", id))
                    .containsEntry("created_at", Timestamp.from(createdAt)).containsEntry("updated_at", Timestamp.from(now));
        }
        for (Instant createdAt : List.of(now.minusSeconds(900).minusNanos(1000), now.minusSeconds(960))) {
            long id = seedComment(postId, null, author.id(), 0, "VISIBLE", createdAt);
            var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
            error(cookieMutation("PUT", Long.toString(id), session, body("Expired")), 409, "COMMENT_EDIT_WINDOW_EXPIRED");
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(before);
        }
        long submicrosecondExpired = seedComment(postId, null, author.id(), 0, "VISIBLE", now.minusSeconds(900));
        clock.set(now.plusNanos(1));
        error(cookieMutation("PUT", Long.toString(submicrosecondExpired), session, body("Expired by one nanosecond")),
                409, "COMMENT_EDIT_WINDOW_EXPIRED");
        clock.set(now);
        long simultaneous = seedComment(postId, null, author.id(), 0, "VISIBLE", now);
        JsonNode response = updated(cookieMutation("PUT", Long.toString(simultaneous), session, body("Same instant")));
        assertThat(response.path("editedAt").isNull()).isTrue();
        assertThat(jdbc.queryForMap("SELECT created_at,updated_at FROM community.comments WHERE id=?", simultaneous))
                .containsEntry("created_at", Timestamp.from(now)).containsEntry("updated_at", Timestamp.from(now));
    }

    @Test
    void updateValidationRunsBeforeOwnershipAndIdParsingAndUsesTheExistingUnicodeUtf16Rules() throws Exception {
        Account author = verifiedAccount();
        Account other = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        long own = seedComment(postId, null, author.id(), 0, "VISIBLE", clock.instant().minusSeconds(30));
        long foreign = seedComment(postId, null, other.id(), 0, "VISIBLE", clock.instant().minusSeconds(30));
        var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", own);
        for (String target : List.of(Long.toString(own), Long.toString(foreign), Long.toString(Long.MAX_VALUE), "not-a-number",
                "9223372036854775808")) {
            for (String invalid : List.of("{}", "{\"content\":null}", body(""), body("\u3000\u2003"), body(" \t\r\n "),
                    body("x".repeat(5001)), body("😀".repeat(2501)), "not-json", "{\"content\":")) {
                JsonNode response = error(cookieMutation("PUT", target, session, invalid), 422, "VALIDATION_FAILED");
                assertThat(fields(response)).containsExactlyInAnyOrder("error_code", "message", "request_id", "details");
                assertThat(response.toString()).doesNotContain(author.email(), other.email(), "JsonParseException", "com.fasterxml.jackson");
            }
        }
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", own)).isEqualTo(before);
        for (String valid : List.of("汉".repeat(5000), "😀".repeat(2500), "\u3000汉\u3000")) {
            assertThat(updated(cookieMutation("PUT", Long.toString(own), session, body(valid))).path("content").asText())
                    .isEqualTo(valid.strip());
        }
    }

    @Test
    void updateRejectsHiddenAndDeletedCommentsBeforeAgeChecksWithoutChangingTheirRows() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        for (String state : List.of("HIDDEN", "DELETED")) {
            for (long age : List.of(30L, 3600L)) {
                long id = seedComment(postId, null, author.id(), 0, state, clock.instant().minusSeconds(age));
                var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
                error(cookieMutation("PUT", Long.toString(id), session, body("Attempted edit")), 409, "COMMENT_NOT_EDITABLE");
                assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(before);
            }
        }
    }

    @Test
    void mutationsRemainAvailableForOwnCommentsOnEveryNonpublicParentPostAndIgnoreTheCreationQuota() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long approved = seedPost(author.id(), "APPROVED");
        for (int i = 0; i < 30; i++) seedComment(approved, null, author.id(), 0, "VISIBLE", clock.instant().minusSeconds(30));
        for (String state : NONPUBLIC) {
            long postId = seedPost(author.id(), state);
            var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
            long id = seedComment(postId, null, author.id(), 0, "VISIBLE", clock.instant().minusSeconds(30));
            assertThat(updated(cookieMutation("PUT", Long.toString(id), session, body("Private post comment")))
                    .path("content").asText()).isEqualTo("Private post comment");
            mvc.perform(cookieMutation("DELETE", Long.toString(id), session, "")).andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("SELECT status FROM community.comments WHERE id=?", String.class, id)).isEqualTo("DELETED");
            assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
        }
    }

    @Test
    void deleteSoftDeletesOnceDecrementsBothPublicCountsAndRetainsNineFieldAncestorAndVisibleReplies() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        long id = seedComment(postId, null, author.id(), 0, "VISIBLE", clock.instant().minusSeconds(86400));
        long firstReply = seedComment(postId, id, author.id(), 1, "VISIBLE", clock.instant().minusSeconds(50));
        long secondReply = seedComment(postId, id, author.id(), 1, "VISIBLE", clock.instant().minusSeconds(40));
        var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
        var firstBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", firstReply);
        var secondBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", secondReply);
        var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        assertPublicCommentCounts(postId, 3);
        MvcResult deleted = mvc.perform(cookieMutation("DELETE", Long.toString(id), session, ""))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(deleted.getResponse().getContentAsByteArray()).isEmpty();
        var expected = new java.util.HashMap<>(before);
        expected.put("status", "DELETED");
        expected.put("moderated_by", author.id());
        expected.put("moderation_reason", "AUTHOR_DELETED");
        expected.put("updated_at", Timestamp.from(clock.instant()));
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(expected);
        assertPublicCommentCounts(postId, 2);
        JsonNode tree = read(mvc.perform(get("/api/public/community/posts/{id}/comments", postId))
                .andExpect(status().isOk()).andReturn());
        assertThat(tree).hasSize(3);
        JsonNode ancestor = tree.get(0);
        assertThat(fields(ancestor)).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        assertThat(ancestor.path("id").asLong()).isEqualTo(id);
        assertThat(ancestor.path("postId").asLong()).isEqualTo(postId);
        assertThat(ancestor.path("status").asText()).isEqualTo("DELETED");
        assertThat(ancestor.path("content").isNull()).isTrue();
        assertThat(ancestor.path("author").isNull()).isTrue();
        assertThat(ancestor.path("editedAt").isNull()).isTrue();
        assertThat(ancestor.toString()).doesNotContain("Private parent content", "Private moderation reason", author.email(), "AUTHOR_DELETED");
        for (int index = 1; index <= 2; index++) {
            assertThat(fields(tree.get(index))).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
            assertThat(tree.get(index).path("id").asLong()).isEqualTo(index == 1 ? firstReply : secondReply);
            assertThat(tree.get(index).path("content").asText()).isEqualTo("Private parent content");
            assertThat(tree.get(index).path("parentId").asLong()).isEqualTo(id);
            assertThat(tree.get(index).path("status").asText()).isEqualTo("VISIBLE");
        }
        clock.set(clock.instant().plusSeconds(1));
        sqlCapture.start();
        try {
            mvc.perform(cookieMutation("DELETE", Long.toString(id), session, "")).andExpect(status().isNoContent());
            assertThat(sqlCapture.statements()).noneMatch(sql -> sql.startsWith("update ") && sql.contains("community.comments"));
        } finally { sqlCapture.stop(); }
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(expected);
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", firstReply)).isEqualTo(firstBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", secondReply)).isEqualTo(secondBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
        assertPublicCommentCounts(postId, 2);
    }

    @Test
    void deletingAlreadyHiddenOrDeletedCommentsIsATrueSqlNoopForEveryPersistedColumn() throws Exception {
        Account author = verifiedAccount();
        Session session = login(author.email());
        long postId = seedPost(author.id(), "APPROVED");
        for (String state : List.of("HIDDEN", "DELETED")) {
            long id = seedComment(postId, null, author.id(), 0, state, clock.instant().minusSeconds(86400));
            jdbc.update("UPDATE community.comments SET moderated_by=?,updated_at=? WHERE id=?",
                    author.id(), Timestamp.from(clock.instant().minusSeconds(30)), id);
            var before = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id);
            sqlCapture.start();
            try {
                for (int i = 0; i < 2; i++) {
                    MvcResult response = mvc.perform(cookieMutation("DELETE", Long.toString(id), session, ""))
                            .andExpect(status().isNoContent()).andReturn();
                    assertThat(response.getResponse().getContentAsByteArray()).isEmpty();
                }
                assertThat(sqlCapture.statements()).noneMatch(sql -> sql.startsWith("update ") && sql.contains("community.comments"));
            } finally { sqlCapture.stop(); }
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id)).isEqualTo(before);
        }
    }

    @Test
    void generatedMutationOpenApiMatchesLockedStatusesSchemasIdTypeAndSecurity() throws Exception {
        JsonNode document = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        JsonNode path = document.path("paths").path("/api/community/comments/{id}");
        for (String method : List.of("put", "delete")) {
            JsonNode operation = path.path(method);
            assertThat(operation.path("operationId").asText()).isEqualTo(method.equals("put") ? "updateComment" : "deleteComment");
            assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrderElementsOf(method.equals("put")
                    ? List.of("200", "401", "403", "409", "422") : List.of("204", "401", "403"));
            assertThat(operation.path("parameters")).hasSize(1);
            JsonNode id = operation.path("parameters").get(0);
            assertThat(id.path("name").asText()).isEqualTo("id");
            assertThat(id.path("in").asText()).isEqualTo("path");
            assertThat(id.path("required").asBoolean()).isTrue();
            assertThat(id.path("schema").path("type").asText()).isEqualTo("integer");
            assertThat(id.path("schema").path("format").asText()).isEqualTo("int64");
            JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
            assertThat(security.isArray()).isTrue();
            assertThat(security.toString()).contains("cookieAuth", "bearerAuth");
            for (String code : method.equals("put") ? List.of("401", "403", "409", "422") : List.of("401", "403")) {
                assertThat(operation.path("responses").path(code).toString()).contains("#/components/schemas/ErrorResponse");
            }
        }
        JsonNode update = path.path("put");
        assertThat(update.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(update.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/CommentWrite");
        assertThat(update.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/Comment");
        assertThat(path.path("delete").has("requestBody")).isFalse();
        assertThat(path.path("delete").path("responses").path("204").has("content")).isFalse();
    }

    private void assertPublicCommentCounts(long postId, long count) throws Exception {
        JsonNode detail = read(mvc.perform(get("/api/public/community/posts/{id}", postId)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("commentCount").asLong()).isEqualTo(count);
        JsonNode page = read(mvc.perform(get("/api/public/community/posts").param("size", "50"))
                .andExpect(status().isOk()).andReturn());
        List<JsonNode> found = new ArrayList<>();
        page.path("items").forEach(item -> { if (item.path("id").asLong() == postId) found.add(item); });
        assertThat(found).singleElement().satisfies(item -> assertThat(item.path("commentCount").asLong()).isEqualTo(count));
    }

    private MockHttpServletRequestBuilder mutation(String method, String id) {
        String path = "/api/community/comments/" + id;
        return method.equals("PUT") ? put(path) : delete(path);
    }

    private MockHttpServletRequestBuilder cookieMutation(String method, String id, Session session, String content) {
        return mutation(method, id).cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private JsonNode updated(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode response = read(mvc.perform(request).andExpect(status().isOk()).andReturn());
        assertThat(fields(response)).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        return response;
    }

    private void assertComment(JsonNode result, long author, long postId, Long parent, int depth, String content) {
        assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        assertThat(result.path("id").asLong()).isPositive();
        assertThat(result.path("postId").asLong()).isEqualTo(postId);
        if (parent == null) assertThat(result.path("parentId").isNull()).isTrue();
        else assertThat(result.path("parentId").asLong()).isEqualTo(parent);
        assertThat(result.path("depth").asInt()).isEqualTo(depth);
        assertThat(result.path("status").asText()).isEqualTo("VISIBLE");
        assertThat(result.path("content").asText()).isEqualTo(content);
        assertThat(fields(result.path("author"))).containsExactlyInAnyOrder("id", "displayName");
        assertThat(result.path("author").path("id").asLong()).isEqualTo(author);
        assertThat(result.path("author").path("displayName").asText()).isEqualTo(DISPLAY_NAME);
        assertThat(Instant.parse(result.path("createdAt").asText())).isEqualTo(clock.instant());
        assertThat(result.path("editedAt").isNull()).isTrue();
    }

    private long seedPost(long authorId, String state) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,"
                        + "published_at,created_at,updated_at) VALUES (?,'QUESTION','Synthetic fixture','Private post content',?,?,?,?,?) RETURNING id",
                Long.class, authorId, state, "Private review reason", Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant().minusSeconds(120)), Timestamp.from(clock.instant().minusSeconds(30)));
    }

    private long seedComment(long postId, Long parentId, long authorId, int depth, String state, Instant createdAt) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,status,"
                        + "moderation_reason,created_at,updated_at) VALUES (?,?,?,'Private parent content',?,?,?,?,?) RETURNING id",
                Long.class, postId, parentId, authorId, depth, state, "Private moderation reason",
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private long commentCount(long author) {
        return jdbc.queryForObject("SELECT count(*) FROM community.comments WHERE author_id=?", Long.class, author);
    }

    private Account verifiedAccount() throws Exception {
        Account account = register();
        verifyEmail(account);
        return account;
    }

    private Account register() throws Exception {
        String email = "comment-" + UUID.randomUUID() + "@example.test";
        JsonNode response = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", DISPLAY_NAME, "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn());
        long id = response.path("userId").asLong();
        assertThat(id).isPositive();
        return new Account(id, email);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode messages = mail("/api/v1/messages").path("messages");
        JsonNode detail = null;
        for (JsonNode candidate : messages) {
            JsonNode full = mail("/api/v1/message/" + candidate.path("ID").asText());
            if (full.path("To").toString().contains(account.email())) {
                detail = full;
                break;
            }
        }
        assertThat(detail != null).as("verification mail delivered to synthetic account").isTrue();
        Matcher token = VERIFY_TOKEN.matcher(detail.path("Text").asText());
        assertThat(token.find()).as("verification link exists").isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token.group(1)))))
                .andExpect(status().isOk());
    }

    private JsonNode mail(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private Session login(String email) throws Exception {
        return session(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn());
    }

    private Session session(MvcResult response) {
        return new Session(cookie(response, "access_token"), cookie(response, "refresh_token"), cookie(response, "XSRF-TOKEN"));
    }

    private Cookie cookie(MvcResult response, String name) {
        Cookie found = response.getResponse().getCookie(name);
        assertThat(found != null).as("required session cookie exists").isTrue();
        return found;
    }

    private long currentUserId(Session session) throws Exception {
        return read(mvc.perform(get("/api/auth/me").cookie(session.access())).andExpect(status().isOk())
                .andReturn()).path("id").asLong();
    }

    private MockHttpServletRequestBuilder cookieWrite(String path, Session session, String content) {
        return post(path).cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private JsonNode created(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode result = read(mvc.perform(request).andExpect(status().isCreated()).andReturn());
        assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        return result;
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int statusCode, String code) throws Exception {
        JsonNode result = read(mvc.perform(request).andExpect(status().is(statusCode)).andReturn());
        assertThat(result.path("error_code").asText()).isEqualTo(code);
        assertThat(result.path("request_id").asText()).isNotBlank();
        assertThat(result.toString()).doesNotContain("stackTrace", "SQLException", "org.hibernate");
        return result;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ObjectNode withoutRequestId(JsonNode response) {
        ObjectNode copy = response.deepCopy();
        copy.remove("request_id");
        return copy;
    }

    private static List<String> fields(JsonNode node) {
        return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(
                node.fieldNames(), 0), false).toList();
    }

    private String body(String content) throws Exception {
        return mapper.writeValueAsString(Map.of("content", content));
    }

    private static String rootPath(long id) { return "/api/community/posts/" + id + "/comments"; }
    private static String replyPath(long id) { return "/api/community/comments/" + id + "/replies"; }
    private static String commentPath(long id) { return "/api/community/comments/" + id; }
    private record Account(long id, String email) { }
    private record Session(Cookie access, Cookie refresh, Cookie csrf) { }

    @TestConfiguration
    static class CommentClockConfiguration {
        @Bean
        @Primary
        CommentClock commentIntegrationClock() { return new CommentClock(); }
        @Bean MutationSqlCapture commentMutationSqlCapture() { return new MutationSqlCapture(); }
        @Bean HibernatePropertiesCustomizer commentMutationStatementInspector(MutationSqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class MutationSqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        void stop() { active.remove(); }
        List<String> statements() { return List.copyOf(active.get()); }
        @Override public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }

    static class CommentClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void set(Instant now) { instant.set(now); }
        void reset() { instant.set(null); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() {
            Instant fixed = instant.get();
            return fixed == null ? Instant.now() : fixed;
        }
    }
}
