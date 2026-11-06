package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(CommentModerationControllerIntegrationTest.CommentModerationConfiguration.class)
class CommentModerationControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String COMMENTS = "/api/community/moderation/comments";
    private static final String POSTS = "/api/community/moderation/posts";
    private static final String LIST_PATH = POSTS + "/{id}/comments";
    private static final String PATCH_PATH = COMMENTS + "/{id}";
    private static final String PASSWORD = "CommentModerationFixturePass123";
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private static final String CONTENT = "Literal <b>private comment</b> **text** 中文 😀";
    private static final List<String> POST_STATES = List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN");
    private static final List<String> ACTIONS = List.of("HIDE", "DELETE", "RESTORE");
    private static final List<String> COMMENT_FIELDS = List.of("id", "postId", "parentId", "depth", "status",
            "content", "author", "createdAt", "moderatedBy", "moderationReason");
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Path CONTRACT = Path.of("specs", "009-community-blog-moderation", "contracts", "openapi-community.yaml");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private SqlCapture capture;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    void prepare() { cleanCommunity(); }

    @AfterEach
    void restore() {
        capture.stop();
        cleanCommunity();
    }

    private void cleanCommunity() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.moderation_cases");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void moderatorOnlyOperationsAllowUnverifiedAccountsAndEnforceCookieCsrfAndIdentityPrecedence() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long postId = postTarget(author.id(), "PENDING");
        long id = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        error(get(LIST_PATH, postId), 401, "UNAUTHENTICATED");
        error(moderate(id, "not-json"), 401, "UNAUTHENTICATED");
        for (String role : List.of("LEARNER", "ADMIN", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
            String token = bearer(moderator, Set.of(role), true);
            error(get(LIST_PATH, postId).header("Authorization", token), 403, "FORBIDDEN");
            error(moderate(id, "not-json").header("Authorization", token), 403, "FORBIDDEN");
        }
        assertThat(jdbc.queryForObject("SELECT email_verified_at IS NULL FROM auth.users WHERE id=?", Boolean.class, moderator.id())).isTrue();
        String token = moderatorToken(moderator);
        ok(get(LIST_PATH, postId).header("Authorization", token));
        ok(moderate(id, body("RESTORE", "Bearer no-op")).header("Authorization", token));
        assertThat(auditRows(id)).isEmpty();

        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", moderator.id());
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", moderator.id());
        Session session = login(moderator);
        ok(get(LIST_PATH, postId).cookie(session.access()));
        error(moderate(id, "not-json").cookie(session.access()), 403, "CSRF_TOKEN_INVALID");
        error(moderate(id, body("HIDE", "Reason")).cookie(session.access(), session.csrf())
                .header("X-XSRF-TOKEN", "wrong"), 403, "CSRF_TOKEN_INVALID");
        error(moderate(id, body("HIDE", "Reason")).cookie(session.access())
                .header("Authorization", token), 403, "CSRF_TOKEN_INVALID");
        Cookie learnerCookie = new Cookie("access_token", bearer(author, Set.of("LEARNER"), false).substring(7));
        error(get(LIST_PATH, postId).cookie(learnerCookie).header("Authorization", token), 403, "FORBIDDEN");
        error(moderate(id, body("HIDE", "Reason")).cookie(learnerCookie, session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue()).header("Authorization", token), 403, "FORBIDDEN");
        JsonNode hidden = ok(withCsrf(moderate(id, body("HIDE", "Cookie reason")), session)
                .header("Authorization", bearer(author, Set.of("LEARNER"), false)));
        assertRef(hidden.path("moderatedBy"), moderator);
        assertAudit(id, moderator.id(), "HIDE", "VISIBLE", "HIDDEN", "Cookie reason");
    }

    @Test
    void listReturnsEveryCommentForEveryPostStateAsAnOrderedFlatArrayWithRawPrivateFields() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        for (String state : POST_STATES) {
            long postId = postTarget(author.id(), state);
            long root = commentTarget(postId, author.id(), "HIDDEN", null, 0, moderator.id(), "Stored reason", ORIGINAL);
            long reply = commentTarget(postId, author.id(), "DELETED", root, 1, moderator.id(), "Deleted reason", ORIGINAL);
            long grandchild = commentTarget(postId, author.id(), "VISIBLE", reply, 2, null, null, ORIGINAL.plusSeconds(1));
            long deepest = commentTarget(postId, author.id(), "VISIBLE", grandchild, 3, author.id(), "Legacy visible reason", ORIGINAL.plusSeconds(2));
            long earlier = commentTarget(postId, moderator.id(), "DELETED", null, 0, moderator.id(), "AUTHOR_DELETED", ORIGINAL.minusSeconds(1));
            long unrelated = postTarget(author.id(), "APPROVED");
            commentTarget(unrelated, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL.minusSeconds(100));
            List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM community.comments WHERE post_id=? ORDER BY id", postId);
            JsonNode result = ok(get(LIST_PATH, postId).header("Authorization", token));
            assertThat(result.isArray()).isTrue();
            assertThat(ids(result)).containsExactly(earlier, root, reply, grandchild, deepest);
            assertComment(result.get(0), earlier, postId, null, 0, "DELETED", moderator, moderator, "AUTHOR_DELETED", ORIGINAL.minusSeconds(1));
            assertComment(result.get(1), root, postId, null, 0, "HIDDEN", author, moderator, "Stored reason", ORIGINAL);
            assertComment(result.get(2), reply, postId, root, 1, "DELETED", author, moderator, "Deleted reason", ORIGINAL);
            assertComment(result.get(3), grandchild, postId, reply, 2, "VISIBLE", author, null, null, ORIGINAL.plusSeconds(1));
            assertComment(result.get(4), deepest, postId, grandchild, 3, "VISIBLE", author, author, "Legacy visible reason", ORIGINAL.plusSeconds(2));
            assertThat(jdbc.queryForList("SELECT * FROM community.comments WHERE post_id=? ORDER BY id", postId)).isEqualTo(before);
            long empty = postTarget(author.id(), state);
            JsonNode emptyResult = ok(get(LIST_PATH, empty).header("Authorization", token));
            assertThat(emptyResult.isArray()).isTrue();
            assertThat(emptyResult).isEmpty();
        }
    }

    @Test
    void malformedAndMissingIdsAre404AndIndependentBodyValidationRunsBeforeLookupOrSelfChecks() throws Exception {
        Account moderator = register("Moderator");
        String token = moderatorToken(moderator);
        for (String id : List.of("missing", "9223372036854775808", "-9223372036854775809", Long.toString(Long.MAX_VALUE), "0", "-1")) {
            error(get(POSTS + "/" + id + "/comments").header("Authorization", token), 404, "POST_NOT_FOUND");
            error(moderate(id, body("HIDE", "Reason")).header("Authorization", token), 404, "COMMENT_NOT_FOUND");
            error(moderate(id, "{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        }
        long postId = postTarget(moderator.id(), "APPROVED");
        long own = commentTarget(postId, moderator.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        var before = commentRow(own);
        error(moderate(own, "{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        assertThat(commentRow(own)).isEqualTo(before);
        assertThat(auditRows(own)).isEmpty();
    }

    @Test
    void invalidJsonMissingNullWrongTypesUnknownActionsAndReservedReasonsAreSafe422WithoutWrites() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long postId = postTarget(author.id(), "APPROVED");
        long id = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        var before = commentRow(id);
        String valid = body("HIDE", "Reason");
        List<String> invalid = new ArrayList<>(List.of("", "not-json", "null", "[]", "42", "true", "{}",
                "{\"action\":\"HIDE\",\"reason\":\"Reason\"", valid + " {}", valid + " true"));
        for (String field : List.of("action", "reason")) {
            ObjectNode missing = (ObjectNode) mapper.readTree(valid);
            missing.remove(field);
            invalid.add(missing.toString());
            ObjectNode explicitNull = (ObjectNode) mapper.readTree(valid);
            explicitNull.putNull(field);
            invalid.add(explicitNull.toString());
            for (String value : List.of("42", "true", "[]", "{}")) {
                ObjectNode wrong = (ObjectNode) mapper.readTree(valid);
                wrong.set(field, mapper.readTree(value));
                invalid.add(wrong.toString());
            }
        }
        for (String action : List.of("hide", " HIDE ", "BAN", "", "UNKNOWN")) invalid.add(body(action, "Reason"));
        for (String reason : List.of("", " \t\n\u3000", "\u2003", "x".repeat(1001), "😀".repeat(1001), "AUTHOR_DELETED", "\u3000 AUTHOR_DELETED \t")) {
            invalid.add(body("HIDE", reason));
        }
        String token = moderatorToken(moderator);
        for (String input : invalid) {
            capture.start();
            try {
                JsonNode failure = error(moderate(id, input).header("Authorization", token), 422, "VALIDATION_FAILED");
                assertThat(failure.path("details").isArray()).isTrue();
                assertThat(failure.path("details")).isNotEmpty();
                assertNoWrites(capture.statements());
            } finally { capture.stop(); }
            assertThat(commentRow(id)).isEqualTo(before);
            assertThat(auditRows(id)).isEmpty();
            error(moderate("not-a-number", input).header("Authorization", token), 422, "VALIDATION_FAILED");
        }
        for (String action : List.of("DELETE", "RESTORE")) {
            error(moderate(id, body(action, "AUTHOR_DELETED")).header("Authorization", token), 422, "VALIDATION_FAILED");
        }
    }

    @Test
    void completeStateMatrixPreservesUnrelatedColumnsAndNoOpsProduceNeitherUpdatesNorAudit() throws Exception {
        Account moderator = register("Moderator");
        Account previous = register("Previous moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        int index = 0;
        for (String initial : List.of("VISIBLE", "HIDDEN", "DELETED", "AUTHOR_DELETED")) {
            for (String action : ACTIONS) {
                boolean authorDeleted = initial.equals("AUTHOR_DELETED");
                String statusBefore = authorDeleted ? "DELETED" : initial;
                long postId = postTarget(author.id(), POST_STATES.get(index++ % POST_STATES.size()));
                long parent = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL.minusSeconds(1));
                long id = commentTarget(postId, author.id(), statusBefore, parent, 1,
                        authorDeleted ? author.id() : previous.id(), authorDeleted ? "AUTHOR_DELETED" : "Stored reason", ORIGINAL);
                long reply = commentTarget(postId, author.id(), "VISIBLE", id, 2, null, null, ORIGINAL.plusSeconds(1));
                Map<String, Object> before = commentRow(id);
                Map<String, Object> replyBefore = commentRow(reply);
                Map<String, Object> postBefore = postRow(postId);
                String reason = "Lý do 举报 \"quoted\" \\ line\n第二行 😀";
                capture.start();
                List<String> sql;
                try {
                    if (authorDeleted && action.equals("RESTORE")) {
                        error(moderate(id, body(action, reason)).header("Authorization", token), 409, "COMMENT_NOT_RESTORABLE");
                        sql = capture.statements();
                        assertNoWrites(sql);
                        assertThat(commentRow(id)).isEqualTo(before);
                        assertThat(auditRows(id)).isEmpty();
                    } else {
                        JsonNode result = ok(moderate(id, body(action, "\u3000 \t" + reason + " \u3000")).header("Authorization", token));
                        sql = capture.statements();
                        String after = nextStatus(statusBefore, action);
                        boolean changed = !after.equals(statusBefore);
                        assertComment(result, id, postId, parent, 1, after, author,
                                changed ? moderator : authorDeleted ? author : previous,
                                changed ? reason : authorDeleted ? "AUTHOR_DELETED" : "Stored reason", ORIGINAL);
                        if (changed) {
                            assertPreserved(before, commentRow(id), Set.of("status", "moderated_by", "moderation_reason"));
                            assertThat(commentUpdates(sql)).hasSize(1);
                            assertAudit(id, moderator.id(), action, statusBefore, after, reason);
                        } else {
                            assertNoWrites(sql);
                            assertThat(commentRow(id)).isEqualTo(before);
                            assertThat(auditRows(id)).isEmpty();
                        }
                    }
                } finally { capture.stop(); }
                assertThat(commentRow(reply)).isEqualTo(replyBefore);
                assertThat(postRow(postId)).isEqualTo(postBefore);
            }
        }
    }

    @Test
    void selfModerationIs403BeforeEveryNoOpAndAuthorDeletedConflictWhileAnotherModeratorCanBeModerated() throws Exception {
        Account moderator = register("Moderator");
        Account otherModerator = register("Other moderator");
        long postId = postTarget(otherModerator.id(), "HIDDEN");
        String token = moderatorToken(moderator);
        for (String state : List.of("VISIBLE", "HIDDEN", "DELETED")) {
            long id = commentTarget(postId, moderator.id(), state, null, 0, moderator.id(),
                    state.equals("DELETED") ? "AUTHOR_DELETED" : "Stored reason", ORIGINAL);
            var before = commentRow(id);
            for (String action : ACTIONS) {
                capture.start();
                try {
                    error(moderate(id, body(action, "Reason")).header("Authorization", token), 403, "SELF_MODERATION");
                    assertNoWrites(capture.statements());
                } finally { capture.stop(); }
                assertThat(commentRow(id)).isEqualTo(before);
                assertThat(auditRows(id)).isEmpty();
            }
        }
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", otherModerator.id());
        long other = commentTarget(postId, otherModerator.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        JsonNode result = ok(moderate(other, body("HIDE", "Different moderator")).header("Authorization", token));
        assertRef(result.path("author"), otherModerator);
        assertAudit(other, moderator.id(), "HIDE", "VISIBLE", "HIDDEN", "Different moderator");
    }

    @Test
    void oneAndThousandUnicodeCodePointReasonsAreStrippedUnknownFieldsIgnoredAndSecondHideIsReadOnly() throws Exception {
        Account moderator = register("Moderator");
        Account otherModerator = register("Other moderator");
        Account author = register("Author");
        long postId = postTarget(author.id(), "APPROVED");
        for (String reason : List.of("x", "😀", "x".repeat(1000), "😀".repeat(1000), "author_deleted", "Author_Deleted")) {
            long id = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
            ObjectNode input = (ObjectNode) mapper.readTree(body("HIDE", "\u3000 \t" + reason + " \t"));
            input.put("id", Long.MAX_VALUE).put("postId", Long.MAX_VALUE).put("parentId", Long.MAX_VALUE)
                    .put("depth", 3).put("status", "VISIBLE").put("content", "Spoofed replacement")
                    .put("authorId", moderator.id()).put("moderatedBy", author.id()).put("moderationReason", "Spoofed reason")
                    .put("createdAt", "2000-01-01T00:00:00Z").put("updatedAt", "2000-01-01T00:00:00Z").put("reasonValid", true);
            JsonNode first = ok(moderate(id, input.toString()).header("Authorization", moderatorToken(moderator)));
            assertComment(first, id, postId, null, 0, "HIDDEN", author, moderator, reason, ORIGINAL);
            assertAudit(id, moderator.id(), "HIDE", "VISIBLE", "HIDDEN", reason);
            var before = commentRow(id);
            List<Map<String, Object>> audits = auditRows(id);
            Captured second = captured(moderate(id, body("HIDE", "Different valid reason"))
                    .header("Authorization", moderatorToken(otherModerator)), 200);
            assertThat(second.body()).isEqualTo(first);
            assertNoWrites(second.sql());
            assertThat(commentRow(id)).isEqualTo(before);
            assertThat(auditRows(id)).isEqualTo(audits);
        }
    }

    @Test
    void onlyExactAuthorDeletionProvenanceBlocksRestoreAndActualAuthorDeletionRemainsIrreversible() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        verifyEmail(author);
        long postId = postTarget(author.id(), "APPROVED");
        String token = moderatorToken(moderator);
        for (Long legacyModerator : new Long[] {moderator.id(), null}) {
            long id = commentTarget(postId, author.id(), "DELETED", null, 0, legacyModerator, "AUTHOR_DELETED", ORIGINAL);
            JsonNode result = ok(moderate(id, body("RESTORE", "Legacy restore")).header("Authorization", token));
            assertComment(result, id, postId, null, 0, "VISIBLE", author, moderator, "Legacy restore", ORIGINAL);
            assertAudit(id, moderator.id(), "RESTORE", "DELETED", "VISIBLE", "Legacy restore");
        }
        long hidden = commentTarget(postId, author.id(), "HIDDEN", null, 0, author.id(), "AUTHOR_DELETED", ORIGINAL);
        ok(moderate(hidden, body("RESTORE", "Hidden marker is not author deletion")).header("Authorization", token));
        long lower = commentTarget(postId, author.id(), "DELETED", null, 0, author.id(), "author_deleted", ORIGINAL);
        ok(moderate(lower, body("RESTORE", "Case-sensitive legacy marker")).header("Authorization", token));
        long actual = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        mvc.perform(delete("/api/community/comments/{id}", actual).header("Authorization", bearer(author, Set.of("LEARNER"), true)))
                .andExpect(status().isNoContent());
        Map<String, Object> before = commentRow(actual);
        assertThat(before.get("status")).isEqualTo("DELETED");
        assertThat(before.get("moderation_reason")).isEqualTo("AUTHOR_DELETED");
        assertThat(((Number) before.get("moderated_by")).longValue()).isEqualTo(author.id());
        error(moderate(actual, body("RESTORE", "Cannot restore author deletion")).header("Authorization", token), 409, "COMMENT_NOT_RESTORABLE");
        assertThat(commentRow(actual)).isEqualTo(before);
        assertThat(auditRows(actual)).isEmpty();
    }

    @Test
    void hideDeleteAndRestoreKeepDatabaseUpdatedAtPublicEditedAtContentAndEveryReplyStable() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long postId = postTarget(author.id(), "APPROVED");
        long id = commentTarget(postId, author.id(), "VISIBLE", null, 0, null, null, ORIGINAL);
        long reply = commentTarget(postId, author.id(), "VISIBLE", id, 1, null, null, ORIGINAL.plusSeconds(1));
        Map<String, Object> before = commentRow(id);
        Map<String, Object> replyBefore = commentRow(reply);
        JsonNode publicBefore = publicComment(postId, id);
        assertThat(Instant.parse(publicBefore.path("editedAt").asText())).isEqualTo(ORIGINAL.plusSeconds(3));
        String previous = "VISIBLE";
        for (String action : List.of("HIDE", "RESTORE", "DELETE", "RESTORE")) {
            String next = nextStatus(previous, action);
            JsonNode result = ok(moderate(id, body(action, "Reason " + action)).header("Authorization", moderatorToken(moderator)));
            assertComment(result, id, postId, null, 0, next, author, moderator, "Reason " + action, ORIGINAL);
            assertPreserved(before, commentRow(id), Set.of("status", "moderated_by", "moderation_reason"));
            assertThat(commentRow(reply)).isEqualTo(replyBefore);
            JsonNode visible = publicComment(postId, id);
            if (next.equals("VISIBLE")) assertThat(visible).isEqualTo(publicBefore);
            else {
                assertThat(visible.path("content").isNull()).isTrue();
                assertThat(visible.path("author").isNull()).isTrue();
                assertThat(visible.path("editedAt").isNull()).isTrue();
            }
            previous = next;
        }
        assertThat(auditRows(id)).extracting(row -> row.get("action")).containsExactly("COMMUNITY_COMMENT_HIDE",
                "COMMUNITY_COMMENT_RESTORE", "COMMUNITY_COMMENT_DELETE", "COMMUNITY_COMMENT_RESTORE");
    }

    @Test
    void listUsesOneCombinedAuthorModeratorBatchAndConstantQueriesForFiveAndTwentyComments() throws Exception {
        Account moderator = register("Moderator");
        List<Account> authors = List.of(register("Author A"), register("Author B"), register("Author C"));
        Account previous = register("Previous moderator");
        String token = moderatorToken(moderator);
        List<Captured> lists = new ArrayList<>();
        for (int size : List.of(5, 20)) {
            long postId = postTarget(authors.get(0).id(), "HIDDEN");
            for (int index = 0; index < size; index++) {
                Account author = authors.get(index % authors.size());
                Account handler = index % 2 == 0 ? moderator : previous;
                commentTarget(postId, author.id(), index % 2 == 0 ? "HIDDEN" : "DELETED", null, 0,
                        handler.id(), "Stored reason", ORIGINAL.plusSeconds(index));
            }
            Captured result = captured(get(LIST_PATH, postId).header("Authorization", token), 200);
            lists.add(result);
            assertThat(result.body()).hasSize(size);
            assertThat(result.sql()).hasSize(3);
            assertThat(result.sql().stream().filter(sql -> sql.contains("community.posts")).toList()).hasSize(1);
            assertThat(result.sql().stream().filter(sql -> sql.contains("community.comments")).toList()).singleElement()
                    .satisfies(sql -> assertThat(sql).contains("order by").matches(".*order by.*created_at.*id.*"));
            assertThat(result.sql().stream().filter(sql -> sql.contains("auth.users")).toList()).singleElement()
                    .satisfies(sql -> assertThat(sql).contains(" in "));
            assertThat(result.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select").doesNotContain("for update", "community.reactions"));
            for (int index = 0; index < size; index++) {
                assertRef(result.body().get(index).path("author"), authors.get(index % authors.size()));
                assertRef(result.body().get(index).path("moderatedBy"), index % 2 == 0 ? moderator : previous);
            }
        }
        assertThat(lists.get(0).sql()).hasSameSizeAs(lists.get(1).sql());
        long emptyPost = postTarget(authors.get(0).id(), "DRAFT");
        Captured empty = captured(get(LIST_PATH, emptyPost).header("Authorization", token), 200);
        assertThat(empty.body()).isEmpty();
        assertThat(empty.sql()).hasSize(2).allSatisfy(sql -> assertThat(sql).doesNotContain("auth.users"));
        Captured missing = captured(get(LIST_PATH, Long.MAX_VALUE).header("Authorization", token), 404);
        assertThat(missing.body().path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
        assertThat(missing.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("community.posts").doesNotContain("auth.users"));
        System.out.println("COMMENT_MODERATION_LIST_SQL_EVIDENCE rows5=3 rows20=3 combinedNameBatch=1 empty=2 missing=1");
    }

    @Test
    void generatedOpenApiMatchesLockedContract() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        String snapshot = System.getProperty("community.openapi.snapshot");
        if (snapshot != null) Files.writeString(Path.of(snapshot), document.toPrettyString(), StandardCharsets.UTF_8);
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        JsonNode source = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        Map<String, OperationShape> expected = operations(source);
        Map<String, OperationShape> actual = operations(document);
        assertThat(expected).hasSize(24);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        Map<String, OperationShape> previous = new LinkedHashMap<>(actual);
        previous.remove("get " + LIST_PATH);
        previous.remove("patch " + PATCH_PATH);
        Map<String, OperationShape> previousLocked = new LinkedHashMap<>(expected);
        previousLocked.remove("get " + LIST_PATH);
        previousLocked.remove("patch " + PATCH_PATH);
        assertThat(previous).hasSize(22).containsExactlyInAnyOrderEntriesOf(previousLocked);
        assertThat(fields(document.path("paths").path(LIST_PATH))).containsExactly("get");
        assertThat(fields(document.path("paths").path(PATCH_PATH))).containsExactly("patch");
        JsonNode list = assertOperation(document, LIST_PATH, "get", "listPostCommentsForModeration", List.of("200", "401", "403", "404"));
        JsonNode moderate = assertOperation(document, PATCH_PATH, "patch", "moderateComment", List.of("200", "401", "403", "404", "409", "422"));
        assertThat(list.has("requestBody")).isFalse();
        JsonNode array = list.path("responses").path("200").path("content").path("application/json").path("schema");
        assertThat(types(array)).containsExactly("array");
        ref(array.path("items"), "ModerationComment");
        ref(moderate.path("responses").path("200").path("content").path("application/json").path("schema"), "ModerationComment");
        JsonNode request = moderate.path("requestBody");
        assertThat(request.path("required").asBoolean()).isTrue();
        assertThat(fields(request.path("content"))).containsExactly("application/json");
        ref(request.path("content").path("application/json").path("schema"), "CommentModeration");
        JsonNode schemas = document.path("components").path("schemas");
        JsonNode write = schemas.path("CommentModeration");
        schema(write, List.of("action", "reason"), List.of("action", "reason"));
        assertThat(types(write)).containsExactly("object");
        JsonNode action = write.path("properties").path("action");
        assertThat(action.has("$ref")).isFalse();
        assertThat(types(action)).containsExactly("string");
        assertThat(strings(action.path("enum"))).containsExactlyInAnyOrderElementsOf(ACTIONS);
        JsonNode reason = write.path("properties").path("reason");
        assertThat(types(reason)).containsExactly("string");
        assertThat(reason.path("minLength").asInt()).isEqualTo(1);
        assertThat(reason.path("maxLength").asInt()).isEqualTo(1000);
        JsonNode response = schemas.path("ModerationComment");
        schema(response, COMMENT_FIELDS, COMMENT_FIELDS);
        assertThat(types(response)).containsExactly("object");
        JsonNode properties = response.path("properties");
        for (String name : List.of("id", "postId")) integer64(properties.path(name), false);
        integer64(properties.path("parentId"), true);
        assertThat(types(properties.path("depth"))).containsExactly("integer");
        assertThat(properties.path("depth").path("minimum").asInt()).isZero();
        assertThat(properties.path("depth").path("maximum").asInt()).isEqualTo(3);
        ref(properties.path("status"), "CommentStatus");
        assertThat(fields(properties.path("status"))).containsExactly("$ref");
        assertThat(strings(schemas.path("CommentStatus").path("enum"))).containsExactlyInAnyOrder("VISIBLE", "HIDDEN", "DELETED");
        assertThat(types(schemas.path("CommentStatus"))).containsExactly("string");
        assertThat(types(properties.path("content"))).containsExactly("string");
        assertThat(types(properties.path("createdAt"))).containsExactly("string");
        assertThat(properties.path("createdAt").path("format").asText()).isEqualTo("date-time");
        ref(properties.path("author"), "UserRef");
        assertThat(fields(properties.path("author"))).containsExactly("$ref");
        assertThat(types(properties.path("author"))).isEmpty();
        assertNullableUserRef(properties.path("moderatedBy"));
        assertThat(types(properties.path("moderationReason"))).containsExactlyInAnyOrder("string", "null");
        schema(schemas.path("UserRef"), List.of("id", "displayName"), List.of("id", "displayName"));
        integer64(schemas.path("UserRef").path("properties").path("id"), false);
        assertThat(types(schemas.path("UserRef").path("properties").path("displayName"))).containsExactlyInAnyOrder("string", "null");
        JsonNode security = document.path("components").path("securitySchemes");
        assertThat(security.path("cookieAuth").path("type").asText()).isEqualTo("apiKey");
        assertThat(security.path("cookieAuth").path("in").asText()).isEqualTo("cookie");
        assertThat(security.path("cookieAuth").path("name").asText()).isEqualTo("access_token");
        assertThat(security.path("bearerAuth").path("type").asText()).isEqualTo("http");
        assertThat(security.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(security.path("bearerAuth").path("bearerFormat").asText()).isEqualTo("JWT");
    }

    private JsonNode assertOperation(JsonNode document, String path, String method, String id, List<String> statuses) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.path("operationId").asText()).isEqualTo(id);
        assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrderElementsOf(statuses);
        for (String code : statuses) {
            JsonNode response = operation.path("responses").path(code);
            assertThat(fields(response.path("content"))).containsExactly("application/json");
            if (!code.equals("200")) ref(response.path("content").path("application/json").path("schema"), "ErrorResponse");
        }
        assertThat(operation.path("parameters")).hasSize(1);
        JsonNode parameter = operation.path("parameters").get(0);
        assertThat(parameter.path("name").asText()).isEqualTo("id");
        assertThat(parameter.path("in").asText()).isEqualTo("path");
        assertThat(parameter.path("required").asBoolean()).isTrue();
        integer64(parameter.path("schema"), false);
        assertThat(securityAlternatives(document, operation)).containsExactlyInAnyOrder(Set.of("cookieAuth"), Set.of("bearerAuth"));
        return operation;
    }

    private static Map<String, OperationShape> operations(JsonNode document) {
        Map<String, OperationShape> result = new LinkedHashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if (!path.getKey().startsWith("/api/community/") && !path.getKey().startsWith("/api/public/community/")) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (!HTTP_METHODS.contains(method.getKey())) return;
                JsonNode operation = method.getValue();
                boolean moderationCommentOperation = ("get".equals(method.getKey()) && LIST_PATH.equals(path.getKey()))
                        || ("patch".equals(method.getKey()) && PATCH_PATH.equals(path.getKey()));
                Map<String, String> responses = new LinkedHashMap<>();
                operation.path("responses").fields().forEachRemaining(entry -> {
                    JsonNode response = resolve(document, entry.getValue());
                    JsonNode content = response.path("content");
                    String mediaType = "application/json";
                    if (!moderationCommentOperation && !content.has(mediaType)) {
                        mediaType = content.has("application/*") ? "application/*" : "*/*";
                    }
                    responses.put(entry.getKey(), schemaIdentity(content.path(mediaType).path("schema")));
                });
                JsonNode body = operation.path("requestBody");
                result.put(method.getKey() + " " + path.getKey(), new OperationShape(operation.path("operationId").asText(),
                        responses, schemaIdentity(body.path("content").path("application/json").path("schema")),
                        body.path("required").asBoolean()));
            });
        });
        return result;
    }

    private static Set<Set<String>> securityAlternatives(JsonNode document, JsonNode operation) {
        JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
        assertThat(security.isArray()).isTrue();
        Set<Set<String>> alternatives = new HashSet<>();
        security.forEach(alternative -> {
            assertThat(alternative.isObject()).isTrue();
            alternative.forEach(scopes -> { assertThat(scopes.isArray()).isTrue(); assertThat(scopes).isEmpty(); });
            alternatives.add(Set.copyOf(fields(alternative)));
        });
        assertThat(alternatives).hasSize(security.size());
        return alternatives;
    }

    private static String schemaIdentity(JsonNode schema) {
        if (schema.has("$ref")) return schema.path("$ref").asText();
        if (schema.path("type").asText().equals("array")) return "array:" + schemaIdentity(schema.path("items"));
        return schema.isMissingNode() ? "" : schema.toString();
    }

    private static JsonNode resolve(JsonNode document, JsonNode value) {
        return value.has("$ref") ? document.at(value.path("$ref").asText().substring(1)) : value;
    }

    private static void assertNullableUserRef(JsonNode schema) {
        String reference = "#/components/schemas/UserRef";
        if (schema.has("$ref")) {
            // Springdoc currently emits a null type beside UserRef; preserve this documented backlog shape only.
            ref(schema, "UserRef");
            assertThat(types(schema)).containsExactly("null");
            assertThat(fields(schema)).containsExactlyInAnyOrder("$ref", "type");
        } else {
            assertThat(fields(schema)).containsExactly("oneOf");
            assertThat(schema.path("oneOf")).hasSize(2);
            List<String> references = new ArrayList<>();
            int nulls = 0;
            for (JsonNode part : schema.path("oneOf")) {
                if (part.has("$ref")) {
                    assertThat(fields(part)).containsExactly("$ref");
                    references.add(part.path("$ref").asText());
                } else {
                    assertThat(fields(part)).containsExactly("type");
                    assertThat(types(part)).containsExactly("null");
                    nulls++;
                }
            }
            assertThat(references).containsExactly(reference);
            assertThat(nulls).isEqualTo(1);
        }
    }

    private static void schema(JsonNode schema, List<String> properties, List<String> required) {
        assertThat(fields(schema.path("properties"))).containsExactlyInAnyOrderElementsOf(properties);
        assertThat(strings(schema.path("required"))).containsExactlyInAnyOrderElementsOf(required);
    }

    private static void integer64(JsonNode schema, boolean nullable) {
        assertThat(types(schema)).containsExactlyInAnyOrderElementsOf(nullable ? Set.of("integer", "null") : Set.of("integer"));
        assertThat(schema.path("format").asText()).isEqualTo("int64");
    }

    private static void ref(JsonNode schema, String name) {
        assertThat(schema.path("$ref").asText()).isEqualTo("#/components/schemas/" + name);
    }

    private void assertComment(JsonNode result, long id, long postId, Long parentId, int depth, String state,
            Account author, Account moderator, String reason, Instant created) {
        assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(COMMENT_FIELDS);
        assertThat(result.path("id").asLong()).isEqualTo(id);
        assertThat(result.path("postId").asLong()).isEqualTo(postId);
        if (parentId == null) assertThat(result.path("parentId").isNull()).isTrue();
        else assertThat(result.path("parentId").asLong()).isEqualTo(parentId);
        assertThat(result.path("depth").asInt()).isEqualTo(depth);
        assertThat(result.path("status").asText()).isEqualTo(state);
        assertThat(result.path("content").asText()).isEqualTo(CONTENT);
        assertRef(result.path("author"), author);
        assertThat(Instant.parse(result.path("createdAt").asText())).isEqualTo(created);
        if (moderator == null) assertThat(result.path("moderatedBy").isNull()).isTrue();
        else assertRef(result.path("moderatedBy"), moderator);
        if (reason == null) assertThat(result.path("moderationReason").isNull()).isTrue();
        else assertThat(result.path("moderationReason").asText()).isEqualTo(reason);
        assertPrivacy(result);
    }

    private static void assertRef(JsonNode ref, Account account) {
        assertThat(fields(ref)).containsExactlyInAnyOrder("id", "displayName");
        assertThat(ref.path("id").asLong()).isEqualTo(account.id());
        assertThat(ref.path("displayName").asText()).isEqualTo(account.name());
    }

    private static void assertPrivacy(JsonNode node) {
        if (node.isObject()) assertThat(fields(node)).doesNotContain("userId", "email", "roles", "password", "passwordHash",
                "authorId", "moderatedById", "updatedAt", "editedAt", "children");
        if (node.isContainerNode()) node.forEach(CommentModerationControllerIntegrationTest::assertPrivacy);
    }

    private void assertAudit(long id, long actor, String action, String before, String after, String reason) throws Exception {
        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actor);
        assertThat(row.get("action")).isEqualTo("COMMUNITY_COMMENT_" + action);
        assertThat(row.get("target_type")).isEqualTo("COMMENT");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(id);
        assertThat(mapper.readTree(row.get("detail").toString())).isEqualTo(mapper.valueToTree(Map.of("before", before, "after", after, "reason", reason)));
    }

    private List<Map<String, Object>> auditRows(long id) {
        return jdbc.queryForList("SELECT * FROM shared.audit_logs WHERE target_type='COMMENT' AND target_id=? ORDER BY id", id);
    }

    private Map<String, Object> commentRow(long id) { return jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", id); }
    private Map<String, Object> postRow(long id) { return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id); }
    private static void assertPreserved(Map<String, Object> before, Map<String, Object> after, Set<String> changed) {
        before.forEach((key, value) -> { if (!changed.contains(key)) assertThat(after.get(key)).as("preserved column %s", key).isEqualTo(value); });
    }

    private static String nextStatus(String before, String action) {
        if (before.equals("DELETED") && !action.equals("RESTORE")) return "DELETED";
        return switch (action) { case "HIDE" -> "HIDDEN"; case "DELETE" -> "DELETED"; case "RESTORE" -> "VISIBLE"; default -> throw new AssertionError(action); };
    }

    private static List<String> commentUpdates(List<String> sql) { return sql.stream().filter(statement -> statement.contains("update community.comments")).toList(); }
    private static void assertNoWrites(List<String> sql) {
        assertThat(sql).noneMatch(statement -> statement.startsWith("update ") || statement.startsWith("insert into ") || statement.startsWith("delete from "));
    }

    private long postTarget(long author, String state) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) VALUES (?,'QUESTION','Synthetic moderation title','Synthetic post body',?,'Stored post reason',?,?,?) RETURNING id",
                Long.class, author, state, Timestamp.from(ORIGINAL.minusSeconds(1)), Timestamp.from(ORIGINAL), Timestamp.from(ORIGINAL.plusSeconds(3)));
    }

    private long commentTarget(long postId, long author, String state, Long parent, int depth, Long moderator, String reason, Instant created) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,status,moderated_by,moderation_reason,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, postId, parent, author, CONTENT, depth, state, moderator, reason, Timestamp.from(created), Timestamp.from(created.plusSeconds(3)));
    }

    private Account register(String name) throws Exception {
        String email = "comment-moderation-" + UUID.randomUUID() + "@example.test";
        JsonNode result = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", name, "acceptTerms", true)))).andExpect(status().isCreated()).andReturn());
        assertThat(result.path("userId").asLong()).isPositive();
        return new Account(result.path("userId").asLong(), email, name);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode found = null;
        for (JsonNode message : mail("/api/v1/messages").path("messages")) {
            JsonNode full = mail("/api/v1/message/" + message.path("ID").asText());
            if (full.path("To").toString().contains(account.email())) { found = full; break; }
        }
        assertThat(found != null).as("verification email delivered to registered fixture account").isTrue();
        var matcher = VERIFY_TOKEN.matcher(found.path("Text").asText());
        assertThat(matcher.find()).as("verification token exists").isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", matcher.group(1))))).andExpect(status().isOk());
    }

    private JsonNode mail(String path) throws Exception {
        HttpResponse<String> result = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        return mapper.readTree(result.body());
    }

    private Session login(Account account) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", account.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        Cookie access = result.getResponse().getCookie("access_token");
        Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(access).isNotNull();
        assertThat(csrf).isNotNull();
        return new Session(access, csrf);
    }

    private JsonNode publicComment(long postId, long id) throws Exception {
        for (JsonNode item : ok(get("/api/public/community/posts/{id}/comments", postId))) if (item.path("id").asLong() == id) return item;
        throw new AssertionError("Comment missing from public flat array: " + id);
    }

    private String moderatorToken(Account account) { return bearer(account, Set.of("MODERATOR"), false); }
    private String bearer(Account account, Set<String> roles, boolean verified) { return "Bearer " + jwt.issueAccessToken(account.id(), roles, verified, UUID.randomUUID()); }
    private String body(String action, String reason) throws Exception { return mapper.writeValueAsString(Map.of("action", action, "reason", reason)); }
    private MockHttpServletRequestBuilder moderate(long id, String body) { return moderate(Long.toString(id), body); }
    private MockHttpServletRequestBuilder moderate(String id, String body) { return patch(COMMENTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body); }
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Session session) { return request.cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()); }
    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return read(mvc.perform(request).andExpect(status().isOk()).andReturn()); }
    private JsonNode error(MockHttpServletRequestBuilder request, int expected, String code) throws Exception {
        JsonNode body = read(mvc.perform(request).andExpect(status().is(expected)).andReturn());
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace", "Exception", "org.hibernate", "java.lang", "SELECT ", "UPDATE ");
        assertPrivacy(body);
        return body;
    }

    private JsonNode read(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private Captured captured(MockHttpServletRequestBuilder request, int expected) throws Exception {
        capture.start();
        try { return new Captured(read(mvc.perform(request).andExpect(status().is(expected)).andReturn()), capture.statements()); }
        finally { capture.stop(); }
    }

    private static List<Long> ids(JsonNode array) { List<Long> result = new ArrayList<>(); array.forEach(item -> result.add(item.path("id").asLong())); return result; }
    private static List<String> fields(JsonNode node) { return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList(); }
    private static List<String> strings(JsonNode array) { List<String> result = new ArrayList<>(); array.forEach(item -> result.add(item.asText())); return result; }
    private static Set<String> types(JsonNode schema) {
        Set<String> result = new HashSet<>();
        JsonNode type = schema.path("type");
        if (type.isTextual()) result.add(type.asText());
        else if (type.isArray()) type.forEach(item -> result.add(item.asText()));
        for (String key : List.of("oneOf", "anyOf", "allOf")) schema.path(key).forEach(part -> result.addAll(types(part)));
        return result;
    }

    private record Account(long id, String email, String name) { }
    private record Session(Cookie access, Cookie csrf) { }
    private record Captured(JsonNode body, List<String> sql) { }
    private record OperationShape(String operationId, Map<String, String> responses, String requestSchema,
            boolean requestRequired) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class CommentModerationConfiguration {
        @Bean SqlCapture commentModerationSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer commentModerationStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        void stop() { active.remove(); }
        List<String> statements() { return List.copyOf(active.get()); }
        @Override public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
