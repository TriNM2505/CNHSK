package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.cnhsk.auth.api.AuthApi;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(ModerationControllerIntegrationTest.ModerationConfiguration.class)
class ModerationControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/moderation/posts";
    private static final String PASSWORD = "ModerationFixturePass123";
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private static final List<String> STATES = List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN");
    private static final List<String> SUMMARY_FIELDS = List.of("id", "title", "category", "excerpt", "author", "createdAt", "updatedAt");
    private static final List<String> DETAIL_FIELDS = List.of("id", "title", "content", "category", "status", "author",
            "reviewReason", "reviewedBy", "reviewedAt", "publishedAt", "createdAt", "updatedAt");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ModerationClock clock;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private AuthApi auth;

    @BeforeEach
    void prepareFixtures() {
        cleanCommunity();
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789));
    }

    @AfterEach
    void restoreFixtures() {
        capture.stop();
        clock.reset();
        cleanCommunity();
    }

    private void cleanCommunity() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.moderation_cases");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void moderationOperationsRequireOnlyModeratorAndEnforceCookieCsrfBeforeBodyParsing() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long id = seed(author.id(), "PENDING", ORIGINAL, null);
        List<MockHttpServletRequestBuilder> operations = List.of(get(BASE), get(BASE + "/{id}", id),
                patch(BASE + "/{id}/approve", id), reject(id, "not-json"));
        for (MockHttpServletRequestBuilder request : operations) error(request, 401, "UNAUTHENTICATED");
        for (String role : List.of("LEARNER", "ADMIN", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
            String token = bearer(moderator, Set.of(role), true);
            error(get(BASE).header("Authorization", token), 403, "FORBIDDEN");
            error(get(BASE + "/{id}", id).header("Authorization", token), 403, "FORBIDDEN");
            error(patch(BASE + "/{id}/approve", id).header("Authorization", token), 403, "FORBIDDEN");
            error(reject(id, "not-json").header("Authorization", token), 403, "FORBIDDEN");
        }
        String token = moderatorToken(moderator);
        clearInvocations(auth);
        ok(get(BASE).header("Authorization", token));
        ok(get(BASE + "/{id}", id).header("Authorization", token));
        error(reject(id, "{} ").header("Authorization", token), 422, "VALIDATION_FAILED");
        verify(auth, never()).requireVerifiedEmail(anyLong());

        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", moderator.id());
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'MODERATOR')", moderator.id());
        Session session = login(moderator);
        ok(get(BASE).cookie(session.access()));
        ok(get(BASE + "/{id}", id).cookie(session.access()));
        error(reject(id, "not-json").cookie(session.access()), 403, "CSRF_TOKEN_INVALID");
        error(reject(id, reason("Reason")).cookie(session.access(), session.csrf())
                .header("X-XSRF-TOKEN", "wrong"), 403, "CSRF_TOKEN_INVALID");
        long rejected = seed(author.id(), "PENDING", ORIGINAL, null);
        ok(withCsrf(reject(rejected, reason("Cookie reason")), session));
        ok(withCsrf(patch(BASE + "/{id}/approve", id), session));
        long bearerRejected = seed(author.id(), "PENDING", ORIGINAL, null);
        ok(reject(bearerRejected, reason("Bearer reason")).header("Authorization", token));
        long bearerApproved = seed(author.id(), "PENDING", ORIGINAL, null);
        ok(patch(BASE + "/{id}/approve", bearerApproved).header("Authorization", token));
    }

    @Test
    void queueContainsOnlyPendingPostsInCreatedTimeAndIdOrderWithExactPrivateSummaryShape() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        for (String state : STATES) if (!state.equals("PENDING")) seed(author.id(), state, ORIGINAL.minusSeconds(100), ORIGINAL);
        long second = seed(author.id(), "PENDING", ORIGINAL.plusSeconds(1), null);
        long first = seed(author.id(), "PENDING", ORIGINAL, null);
        long third = seed(author.id(), "PENDING", ORIGINAL.plusSeconds(1), null);
        jdbc.update("UPDATE community.posts SET content=? WHERE id=?", "😀".repeat(201), first);
        String token = moderatorToken(moderator);
        JsonNode page = ok(get(BASE).header("Authorization", token));
        assertThat(fields(page)).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
        assertThat(ids(page)).containsExactly(first, second, third);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        for (JsonNode item : page.path("items")) {
            assertThat(fields(item)).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
            assertUserRef(item.path("author"), author.id(), "Author");
        }
        assertThat(page.path("items").get(0).path("excerpt").asText()).isEqualTo("😀".repeat(200));
        assertThat(ids(ok(get(BASE).header("Authorization", token).param("page", "1").param("size", "2"))))
                .containsExactly(third);
        assertThat(ok(get(BASE).header("Authorization", token).param("page", "").param("size", ""))).isEqualTo(page);
        assertThat(ok(get(BASE).header("Authorization", token).param("status", "APPROVED")
                .param("authorId", Long.toString(moderator.id())))).isEqualTo(page);
        for (String pageValue : List.of("-1", "x", "2147483648")) {
            error(get(BASE).header("Authorization", token).param("page", pageValue), 422, "VALIDATION_FAILED");
        }
        for (String sizeValue : List.of("0", "51", "x", "2147483648")) {
            error(get(BASE).header("Authorization", token).param("size", sizeValue), 422, "VALIDATION_FAILED");
        }
        assertThat(ok(get(BASE).header("Authorization", token).param("size", "50")).path("size").asInt()).isEqualTo(50);
    }

    @Test
    void detailReadsAllStatesIncludingOwnPostAndIncludesOnlyTheLockedFieldsAndUserReferences() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        for (String state : STATES) {
            long id = seed(author.id(), state, ORIGINAL, ORIGINAL.minusSeconds(1));
            jdbc.update("UPDATE community.posts SET reviewed_by=?,reviewed_at=? WHERE id=?",
                    moderator.id(), Timestamp.from(ORIGINAL.plusSeconds(2)), id);
            var before = row(id);
            JsonNode detail = ok(get(BASE + "/{id}", id).header("Authorization", token));
            assertThat(fields(detail)).containsExactlyInAnyOrderElementsOf(DETAIL_FIELDS);
            assertThat(detail.path("status").asText()).isEqualTo(state);
            assertThat(detail.path("reviewReason").asText()).isEqualTo("Stored synthetic reason");
            assertThat(detail.path("content").asText()).isEqualTo("Literal <b>body</b> **text**");
            assertUserRef(detail.path("author"), author.id(), "Author");
            assertUserRef(detail.path("reviewedBy"), moderator.id(), "Moderator");
            assertThat(Instant.parse(detail.path("reviewedAt").asText())).isEqualTo(ORIGINAL.plusSeconds(2));
            assertThat(row(id)).isEqualTo(before);
        }
        long own = seed(moderator.id(), "PENDING", ORIGINAL, null);
        JsonNode detail = ok(get(BASE + "/{id}", own).header("Authorization", token));
        assertThat(detail.path("reviewedBy").isNull()).isTrue();
        assertThat(detail.path("reviewedAt").isNull()).isTrue();
        assertThat(detail.path("publishedAt").isNull()).isTrue();
        assertUserRef(detail.path("author"), moderator.id(), "Moderator");
    }

    @Test
    void missingAndMalformedIdsReturn404AndBodyValidationPrecedesMissingOrSelfChecks() throws Exception {
        Account moderator = register("Moderator");
        String token = moderatorToken(moderator);
        long own = seed(moderator.id(), "PENDING", ORIGINAL, null);
        var ownBefore = row(own);
        for (String id : List.of(Long.toString(Long.MAX_VALUE), "not-a-number", "9223372036854775808", "-1", "0")) {
            error(get(BASE + "/" + id).header("Authorization", token), 404, "POST_NOT_FOUND");
            error(patch(BASE + "/" + id + "/approve").header("Authorization", token), 404, "POST_NOT_FOUND");
            error(patch(BASE + "/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content(reason("Reason"))
                    .header("Authorization", token), 404, "POST_NOT_FOUND");
            error(patch(BASE + "/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header("Authorization", token), 422, "VALIDATION_FAILED");
        }
        error(reject(own, "{}").header("Authorization", token), 422, "VALIDATION_FAILED");
        error(patch(BASE + "/{id}/approve", own).header("Authorization", token), 403, "SELF_MODERATION");
        error(reject(own, reason("Reason")).header("Authorization", token), 403, "SELF_MODERATION");
        assertThat(row(own)).isEqualTo(ownBefore);
        assertThat(auditRows(own)).isEmpty();
    }

    @Test
    void approvalReloadsFreshDatabaseStateKeepsPreviousMetadataAndImmediatelyAppearsPublicly() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        for (Instant publication : new Instant[] {null, ORIGINAL.minusSeconds(20)}) {
            long id = seed(author.id(), "PENDING", ORIGINAL, publication);
            jdbc.update("UPDATE community.posts SET comments_locked=true WHERE id=?", id);
            var before = row(id);
            JsonNode result = ok(patch(BASE + "/{id}/approve", id).header("Authorization", token));
            Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
            assertDecision(result, id, "APPROVED", moderator, author, at);
            assertThat(Instant.parse(result.path("publishedAt").asText())).isEqualTo(publication == null ? at : publication);
            assertThat(result.path("reviewReason").asText()).isEqualTo("Stored synthetic reason");
            assertPreserved(before, row(id), Set.of("status", "reviewed_by", "reviewed_at", "published_at"));
            assertAudit(id, moderator.id(), "COMMUNITY_POST_APPROVE", Map.of());
            JsonNode publicPost = ok(get("/api/public/community/posts/{id}", id));
            assertThat(publicPost.path("id").asLong()).isEqualTo(id);
            assertThat(fields(publicPost)).doesNotContain("reviewReason", "reviewedBy", "reviewedAt", "commentsLocked");
            assertThat(ids(ok(get("/api/public/community/posts")))).contains(id);
            assertThat(ok(get(BASE + "/{id}", id).header("Authorization", token))).isEqualTo(result);
        }
    }

    @Test
    void rejectionStripsReasonPreservesPublicationAndTimestampsAndOwnerImmediatelySeesReason() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long id = seed(author.id(), "PENDING", ORIGINAL, ORIGINAL.minusSeconds(20));
        var before = row(id);
        String reason = "Lý do hợp lệ 举报 \"quoted\" \\ line\n第二行 😀";
        ObjectNode request = mapper.createObjectNode().put("reason", "\u3000 \t" + reason + " \u3000");
        request.put("status", "APPROVED").put("reviewedBy", author.id()).put("reviewedAt", "2000-01-01T00:00:00Z")
                .put("publishedAt", "2000-01-01T00:00:00Z").put("authorId", moderator.id()).put("reasonValid", true);
        JsonNode result = ok(reject(id, request.toString()).header("Authorization", moderatorToken(moderator)));
        assertDecision(result, id, "REJECTED", moderator, author, clock.instant().truncatedTo(ChronoUnit.MICROS));
        assertThat(result.path("reviewReason").asText()).isEqualTo(reason);
        assertThat(Instant.parse(result.path("publishedAt").asText())).isEqualTo(ORIGINAL.minusSeconds(20));
        assertPreserved(before, row(id), Set.of("status", "review_reason", "reviewed_by", "reviewed_at"));
        assertAudit(id, moderator.id(), "COMMUNITY_POST_REJECT", Map.of("reason", reason));
        JsonNode own = ok(get("/api/community/posts/mine/{id}", id).header("Authorization", bearer(author, Set.of("LEARNER"), false)));
        assertThat(own.path("status").asText()).isEqualTo("REJECTED");
        assertThat(own.path("reviewReason").asText()).isEqualTo(reason);
        error(get("/api/public/community/posts/{id}", id), 404, "POST_NOT_FOUND");
    }

    @Test
    void everyNonPendingStateRejectsBothDecisionsWithoutOverwritingPostOrAudit() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        for (String state : STATES) {
            if (state.equals("PENDING")) continue;
            long id = seed(author.id(), state, ORIGINAL, ORIGINAL);
            var before = row(id);
            error(patch(BASE + "/{id}/approve", id).header("Authorization", token), 409, "POST_NOT_PENDING");
            error(reject(id, reason("Replacement reason")).header("Authorization", token), 409, "POST_NOT_PENDING");
            assertThat(row(id)).isEqualTo(before);
            assertThat(auditRows(id)).isEmpty();
        }
    }

    @Test
    void invalidReasonsAreSafe422WithoutWritesAndUnicodeCodePointBoundariesAreAccepted() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        long id = seed(author.id(), "PENDING", ORIGINAL, null);
        var before = row(id);
        String token = moderatorToken(moderator);
        List<String> invalid = new ArrayList<>(List.of("", "not-json", "null", "[]", "42", "true", "{}",
                "{\"reason\":null}", "{\"reason\":42}", "{\"reason\":true}", "{\"reason\":[]}", "{\"reason\":{}}",
                "{\"reason\":\"Reason\"", "{\"reason\":\"Reason\"} {}", "{\"reason\":\"Reason\"} true"));
        for (String value : List.of("", " \t\n\u3000", "x".repeat(1001), "😀".repeat(1001))) invalid.add(reason(value));
        for (String body : invalid) {
            error(reject(id, body).header("Authorization", token), 422, "VALIDATION_FAILED");
            assertThat(row(id)).isEqualTo(before);
            assertThat(auditRows(id)).isEmpty();
        }
        for (String value : List.of("x", "😀", "x".repeat(1000), "😀".repeat(1000))) {
            long accepted = seed(author.id(), "PENDING", ORIGINAL, null);
            JsonNode result = ok(reject(accepted, reason("\u3000 " + value + " \t")).header("Authorization", token));
            assertThat(result.path("reviewReason").asText()).isEqualTo(value);
            assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(DETAIL_FIELDS);
            assertAudit(accepted, moderator.id(), "COMMUNITY_POST_REJECT", Map.of("reason", value));
        }
    }

    @Test
    void realQueueQueriesStayConstantForFiveAndTwentyPostsAndBatchDistinctNamesOnce() throws Exception {
        Account moderator = register("Moderator");
        List<Account> authors = new ArrayList<>();
        for (int index = 0; index < 5; index++) authors.add(register("Author " + index));
        for (int index = 0; index < 25; index++) seed(authors.get(index % 5).id(), "PENDING", ORIGINAL.plusSeconds(index), null);
        Set<Long> authorIds = new HashSet<>();
        authors.forEach(author -> authorIds.add(author.id()));
        String token = moderatorToken(moderator);
        for (int size : List.of(5, 20)) {
            clearInvocations(auth);
            Captured result = captured(BASE + "?size=" + size, token, 200);
            assertThat(result.body().path("items")).hasSize(size);
            assertThat(result.body().path("totalElements").asLong()).isEqualTo(25);
            assertThat(result.sql()).hasSize(3);
            assertThat(result.sql().get(0)).contains("count(", "community.posts", "pending");
            assertThat(result.sql().get(1)).contains("community.posts", "pending", "order by")
                    .matches(".*order by.*created_at.*id.*");
            assertThat(result.sql().get(2)).contains("auth.users", " in ");
            assertThat(result.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select")
                    .doesNotContain("for update", "community.comments", "community.reactions", "group by"));
            verify(auth).displayNames(authorIds);
        }
        for (String page : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            clearInvocations(auth);
            Captured empty = captured(BASE + "?page=" + page + "&size=50", token, 200);
            assertThat(empty.body().path("items")).isEmpty();
            assertThat(empty.body().path("totalElements").asLong()).isEqualTo(25);
            assertThat(empty.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("count(", "community.posts"));
            verify(auth, never()).displayNames(org.mockito.ArgumentMatchers.anySet());
        }
        System.out.println("MODERATION_QUEUE_SQL_EVIDENCE rows=25 authors=5 page5=3 page20=3 outOfRange=1 batchCalls=1");
    }

    @Test
    void emptyQueueOnlyCountsAndDetailBatchesAuthorReviewerWithNoLookupForMissingPost() throws Exception {
        Account moderator = register("Moderator");
        Account author = register("Author");
        String token = moderatorToken(moderator);
        clearInvocations(auth);
        Captured empty = captured(BASE, token, 200);
        assertThat(empty.body().path("items")).isEmpty();
        assertThat(empty.body().path("totalElements").asLong()).isZero();
        assertThat(empty.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("count(", "community.posts"));
        verify(auth, never()).displayNames(org.mockito.ArgumentMatchers.anySet());
        long id = seed(author.id(), "HIDDEN", ORIGINAL, ORIGINAL);
        jdbc.update("UPDATE community.posts SET reviewed_by=?,reviewed_at=? WHERE id=?", moderator.id(), Timestamp.from(ORIGINAL), id);
        clearInvocations(auth);
        Captured detail = captured(BASE + "/" + id, token, 200);
        assertThat(detail.sql()).hasSize(2);
        assertThat(detail.sql().get(0)).contains("community.posts");
        assertThat(detail.sql().get(1)).contains("auth.users", " in ");
        verify(auth).displayNames(Set.of(author.id(), moderator.id()));
        long unreviewed = seed(author.id(), "PENDING", ORIGINAL, null);
        clearInvocations(auth);
        Captured pending = captured(BASE + "/" + unreviewed, token, 200);
        assertThat(pending.sql()).hasSize(2);
        assertThat(pending.body().path("reviewedBy").isNull()).isTrue();
        verify(auth).displayNames(Set.of(author.id()));
        clearInvocations(auth);
        Captured missing = captured(BASE + "/" + Long.MAX_VALUE, token, 404);
        assertThat(missing.sql()).singleElement().satisfies(sql -> assertThat(sql).contains("community.posts"));
        verify(auth, never()).displayNames(org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void generatedOpenApiMatchesLockedOperationsStatusesSecurityAndExactSchemas() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        assertOperation(document, BASE, "get", "listPendingPosts", "ModerationPostPage", List.of("200", "401", "403", "422"));
        assertOperation(document, BASE + "/{id}", "get", "getModerationPost", "ModerationPost", List.of("200", "401", "403", "404"));
        JsonNode approve = assertOperation(document, BASE + "/{id}/approve", "patch", "approvePost", "ModerationPost",
                List.of("200", "401", "403", "404", "409"));
        JsonNode reject = assertOperation(document, BASE + "/{id}/reject", "patch", "rejectPost", "ModerationPost",
                List.of("200", "401", "403", "404", "409", "422"));
        assertThat(approve.has("requestBody")).isFalse();
        assertThat(reject.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(fields(reject.path("requestBody").path("content"))).containsExactly("application/json");
        assertThat(reject.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/RejectWrite");
        JsonNode schemas = document.path("components").path("schemas");
        assertSchema(schemas.path("ModerationPostSummary"), SUMMARY_FIELDS, SUMMARY_FIELDS);
        assertSchema(schemas.path("ModerationPostPage"), List.of("items", "page", "size", "totalElements"),
                List.of("items", "page", "size", "totalElements"));
        assertSchema(schemas.path("ModerationPost"), DETAIL_FIELDS,
                List.of("id", "title", "content", "category", "status", "author", "createdAt", "updatedAt"));
        assertSchema(schemas.path("RejectWrite"), List.of("reason"), List.of("reason"));
        JsonNode reason = schemas.path("RejectWrite").path("properties").path("reason");
        assertThat(schemaTypes(reason)).containsExactly("string");
        assertThat(reason.path("minLength").asInt()).isEqualTo(1);
        assertThat(reason.path("maxLength").asInt()).isEqualTo(1000);
        assertSchema(schemas.path("UserRef"), List.of("id", "displayName"), List.of("id", "displayName"));
        assertThat(schemaTypes(schemas.path("UserRef").path("properties").path("displayName"))).containsExactlyInAnyOrder("string", "null");
        for (String schema : List.of("ModerationPostSummary", "ModerationPost", "UserRef")) {
            assertInteger64(schemas.path(schema).path("properties").path("id"));
        }
        assertInteger64(schemas.path("ModerationPostPage").path("properties").path("totalElements"));
        assertThat(schemas.path("ModerationPostPage").path("properties").path("items").path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/ModerationPostSummary");
        for (String schema : List.of("ModerationPostSummary", "ModerationPost")) {
            JsonNode properties = schemas.path(schema).path("properties");
            assertThat(properties.path("author").path("$ref").asText()).isEqualTo("#/components/schemas/UserRef");
            for (String timestamp : List.of("createdAt", "updatedAt")) {
                assertThat(schemaTypes(properties.path(timestamp))).containsExactly("string");
                assertThat(properties.path(timestamp).path("format").asText()).isEqualTo("date-time");
            }
        }
        JsonNode detail = schemas.path("ModerationPost").path("properties");
        assertThat(schemaTypes(detail.path("reviewReason"))).containsExactlyInAnyOrder("string", "null");
        for (String timestamp : List.of("reviewedAt", "publishedAt")) {
            assertThat(schemaTypes(detail.path(timestamp))).containsExactlyInAnyOrder("string", "null");
            assertThat(detail.path(timestamp).path("format").asText()).isEqualTo("date-time");
        }
        JsonNode reviewer = detail.path("reviewedBy");
        assertThat(hasReference(reviewer, "#/components/schemas/UserRef")).as("reviewedBy retains the UserRef schema").isTrue();
        assertThat(schemaTypes(reviewer).contains("null") || reviewer.path("nullable").asBoolean())
                .as("reviewedBy accepts an explicit null reference").isTrue();
    }

    private JsonNode assertOperation(JsonNode document, String path, String method, String id, String responseSchema,
            List<String> responses) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.path("operationId").asText()).isEqualTo(id);
        assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrderElementsOf(responses);
        for (String code : responses) {
            assertThat(operation.path("responses").path(code).path("content").path("application/json").path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/" + (code.equals("200") ? responseSchema : "ErrorResponse"));
        }
        JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
        assertThat(security).hasSize(2);
        List<List<String>> alternatives = new ArrayList<>();
        security.forEach(alternative -> {
            assertThat(alternative).hasSize(1);
            alternative.forEach(scopes -> assertThat(scopes).isEmpty());
            alternatives.add(fields(alternative));
        });
        assertThat(alternatives).containsExactlyInAnyOrder(List.of("cookieAuth"), List.of("bearerAuth"));
        if (path.contains("{id}")) {
            JsonNode parameter = null;
            for (JsonNode candidate : operation.path("parameters")) if (candidate.path("name").asText().equals("id")) parameter = candidate;
            assertThat(parameter).isNotNull();
            assertThat(parameter.path("in").asText()).isEqualTo("path");
            assertThat(parameter.path("required").asBoolean()).isTrue();
            assertInteger64(parameter.path("schema"));
        } else {
            for (String name : List.of("page", "size")) {
                JsonNode parameter = null;
                for (JsonNode candidate : operation.path("parameters")) if (candidate.path("name").asText().equals(name)) parameter = candidate;
                assertThat(parameter).isNotNull();
                assertThat(parameter.path("in").asText()).isEqualTo("query");
                assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
                assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(name.equals("page") ? 0 : 20);
                assertThat(parameter.path("schema").path("minimum").asInt()).isEqualTo(name.equals("page") ? 0 : 1);
                if (name.equals("size")) assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(50);
            }
        }
        return operation;
    }

    private static void assertSchema(JsonNode schema, List<String> properties, List<String> required) {
        assertThat(fields(schema.path("properties"))).containsExactlyInAnyOrderElementsOf(properties);
        assertThat(strings(schema.path("required"))).containsExactlyInAnyOrderElementsOf(required);
    }

    private static void assertInteger64(JsonNode schema) {
        assertThat(schema.path("type").asText()).isEqualTo("integer");
        assertThat(schema.path("format").asText()).isEqualTo("int64");
    }

    private void assertDecision(JsonNode result, long id, String state, Account moderator, Account author, Instant at) {
        assertThat(fields(result)).containsExactlyInAnyOrderElementsOf(DETAIL_FIELDS);
        assertThat(result.path("id").asLong()).isEqualTo(id);
        assertThat(result.path("status").asText()).isEqualTo(state);
        assertUserRef(result.path("author"), author.id(), author.name());
        assertUserRef(result.path("reviewedBy"), moderator.id(), moderator.name());
        assertThat(Instant.parse(result.path("reviewedAt").asText())).isEqualTo(at);
        assertThat(Instant.parse(result.path("createdAt").asText())).isEqualTo(ORIGINAL);
        assertThat(Instant.parse(result.path("updatedAt").asText())).isEqualTo(ORIGINAL.plusSeconds(3));
        Map<String, Object> stored = row(id);
        assertThat(stored.get("status")).isEqualTo(state);
        assertThat(((Number) stored.get("reviewed_by")).longValue()).isEqualTo(moderator.id());
        assertThat(((Timestamp) stored.get("reviewed_at")).toInstant()).isEqualTo(at);
    }

    private static void assertUserRef(JsonNode node, long id, String name) {
        assertThat(fields(node)).containsExactlyInAnyOrder("id", "displayName");
        assertThat(node.path("id").asLong()).isEqualTo(id);
        assertThat(node.path("displayName").asText()).isEqualTo(name);
    }

    private static void assertPreserved(Map<String, Object> before, Map<String, Object> after, Set<String> changed) {
        before.forEach((key, value) -> {
            if (!changed.contains(key)) assertThat(after.get(key)).as("preserved column %s", key).isEqualTo(value);
        });
    }

    private void assertAudit(long id, long actor, String action, Map<String, String> detail) throws Exception {
        List<Map<String, Object>> rows = auditRows(id);
        assertThat(rows).hasSize(1);
        Map<String, Object> audit = rows.get(0);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(actor);
        assertThat(audit.get("action")).isEqualTo(action);
        assertThat(audit.get("target_type")).isEqualTo("POST");
        assertThat(((Number) audit.get("target_id")).longValue()).isEqualTo(id);
        assertThat(mapper.readTree(audit.get("detail").toString())).isEqualTo(mapper.valueToTree(detail));
    }

    private List<Map<String, Object>> auditRows(long id) {
        return jdbc.queryForList("SELECT * FROM shared.audit_logs WHERE target_type='POST' AND target_id=? "
                + "AND action IN ('COMMUNITY_POST_APPROVE','COMMUNITY_POST_REJECT') ORDER BY id", id);
    }

    private Map<String, Object> row(long id) { return jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id); }

    private long seed(long author, String state, Instant created, Instant publication) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) "
                + "VALUES (?,'QUESTION','Synthetic moderation title','Literal <b>body</b> **text**',?,'Stored synthetic reason',?,?,?) RETURNING id",
                Long.class, author, state, publication == null ? null : Timestamp.from(publication),
                Timestamp.from(created), Timestamp.from(created.plusSeconds(3)));
    }

    private Account register(String name) throws Exception {
        String email = "moderation-" + UUID.randomUUID() + "@example.test";
        JsonNode result = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", name, "acceptTerms", true)))).andExpect(status().isCreated()).andReturn());
        long id = result.path("userId").asLong();
        assertThat(id).isPositive();
        return new Account(id, email, name);
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

    private String moderatorToken(Account account) { return bearer(account, Set.of("MODERATOR"), false); }
    private String bearer(Account account, Set<String> roles, boolean verified) {
        return "Bearer " + jwt.issueAccessToken(account.id(), roles, verified, UUID.randomUUID());
    }
    private MockHttpServletRequestBuilder reject(long id, String body) {
        return patch(BASE + "/{id}/reject", id).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Session session) {
        return request.cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue());
    }
    private String reason(String value) throws Exception { return mapper.writeValueAsString(Map.of("reason", value)); }
    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return read(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }
    private JsonNode error(MockHttpServletRequestBuilder request, int expected, String code) throws Exception {
        JsonNode body = read(mvc.perform(request).andExpect(status().is(expected)).andReturn());
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace", "Exception", "org.hibernate", "java.lang", "SELECT ", "UPDATE ");
        assertThat(fields(body)).doesNotContain("author", "reviewedBy", "email", "password", "content");
        return body;
    }
    private JsonNode read(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private Captured captured(String path, String token, int expected) throws Exception {
        capture.start();
        try {
            JsonNode body = read(mvc.perform(get(path).header("Authorization", token)).andExpect(status().is(expected)).andReturn());
            return new Captured(body, capture.statements());
        } finally { capture.stop(); }
    }
    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }
    private static List<String> fields(JsonNode node) {
        return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList();
    }
    private static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }
    private static Set<String> schemaTypes(JsonNode schema) {
        Set<String> result = new HashSet<>();
        JsonNode type = schema.path("type");
        if (type.isTextual()) result.add(type.asText());
        else if (type.isArray()) type.forEach(value -> result.add(value.asText()));
        for (String keyword : List.of("oneOf", "anyOf", "allOf")) schema.path(keyword).forEach(part -> result.addAll(schemaTypes(part)));
        return result;
    }
    private static boolean hasReference(JsonNode schema, String reference) {
        if (schema.path("$ref").asText().equals(reference)) return true;
        for (String keyword : List.of("oneOf", "anyOf", "allOf")) {
            for (JsonNode part : schema.path(keyword)) if (hasReference(part, reference)) return true;
        }
        return false;
    }

    private record Account(long id, String email, String name) { }
    private record Session(Cookie access, Cookie csrf) { }
    private record Captured(JsonNode body, List<String> sql) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModerationConfiguration {
        @Bean @Primary ModerationClock moderationIntegrationClock() { return new ModerationClock(); }
        @Bean SqlCapture moderationSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer moderationStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class ModerationClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void set(Instant value) { instant.set(value); }
        void reset() { instant.set(null); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { Instant value = instant.get(); return value == null ? Instant.now() : value; }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }
}
