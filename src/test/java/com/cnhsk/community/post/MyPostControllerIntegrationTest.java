package com.cnhsk.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MyPostControllerIntegrationTest.FixedClockConfiguration.class)
class MyPostControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "PostTestPass123";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern VERIFY_TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final Instant TEST_NOW = Instant.parse("2026-11-01T16:30:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private TestClock testClock;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void createRequiresLearnerVerifiedEmailAndValidCsrf() throws Exception {
        Account unverified = register("unverified");
        Session unverifiedSession = login(unverified.email());
        mockMvc.perform(post("/api/community/posts").cookie(unverifiedSession.access())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Title", "Body", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(post("/api/community/posts").cookie(unverifiedSession.access(), unverifiedSession.csrf())
                        .header("X-XSRF-TOKEN", unverifiedSession.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Title", "Body", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ACCOUNT_UNVERIFIED"));

        verifyEmail(unverified);
        Session verifiedSession = login(unverified.email());
        mockMvc.perform(post("/api/community/posts").contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("Title", "Body", true)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        Cookie wrongRole = new Cookie("access_token", jwtService.issueAccessToken(
                currentUserId(verifiedSession.access()), java.util.List.of("ADMIN"), true, UUID.randomUUID()));
        mockMvc.perform(post("/api/community/posts").cookie(wrongRole, verifiedSession.csrf())
                        .header("X-XSRF-TOKEN", verifiedSession.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Title", "Body", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/community/posts").cookie(verifiedSession.access(), verifiedSession.csrf())
                        .header("X-XSRF-TOKEN", verifiedSession.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Title", "Body", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createStripsTextAndIgnoresForgedAuthorAndStatusWhilePreservingMarkupAsText() throws Exception {
        Account account = register("roundtrip");
        verifyEmail(account);
        Session session = login(account.email());
        String literal = "  <img src=x onerror=alert(1)>\n**đậm**  ";
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "  Exact title  ", "content", literal, "category", "QUESTION", "submit", false,
                "authorId", -42, "status", "APPROVED"));

        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/community/posts")
                        .cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Exact title"))
                .andExpect(jsonPath("$.content").value(literal.strip()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString());
        long id = response.path("id").asLong();
        long expectedAuthorId = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                                .cookie(session.access()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        long authenticatedAuthorId = jdbcTemplate.queryForObject(
                "SELECT author_id FROM community.posts WHERE id = ?", Long.class, id);
        assertThat(authenticatedAuthorId).isEqualTo(expectedAuthorId);
        assertThat(authenticatedAuthorId).isNotEqualTo(-42L);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM community.posts WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM community.posts WHERE id = ?", String.class, id))
                .isEqualTo(literal.strip());

        assertThat(response.path("content").asText()).isEqualTo(literal.strip());
    }

    @Test
    void updateMovesDraftToPendingAndPersistsOnlyAllowedFieldsAndAuditTimes() throws Exception {
        Account account = register("update");
        verifyEmail(account);
        Session session = login(account.email());
        long id = createPost(session, "Initial", "Original", false);
        Instant createdBefore = jdbcTemplate.queryForObject(
                "SELECT created_at FROM community.posts WHERE id = ?", (rs, row) -> rs.getTimestamp(1).toInstant(), id);
        Instant updatedBefore = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM community.posts WHERE id = ?", (rs, row) -> rs.getTimestamp(1).toInstant(), id);

        Thread.sleep(5);
        MvcResult updateResult = mockMvc.perform(put("/api/community/posts/{id}", id).cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", " Revised ", "content", " New body ",
                                "category", "CULTURE", "submit", true, "authorId", 999999, "status", "APPROVED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Revised"))
                .andExpect(jsonPath("$.content").value("New body"))
                .andExpect(jsonPath("$.status").value("PENDING")).andReturn();

        var row = jdbcTemplate.queryForMap("SELECT author_id, category, status, created_at, updated_at, reviewed_by, "
                + "reviewed_at, review_reason, published_at FROM community.posts WHERE id = ?", id);
        assertThat(((Number) row.get("author_id")).longValue()).isNotEqualTo(999999L);
        assertThat(row.get("category")).isEqualTo("CULTURE");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant()).isEqualTo(createdBefore);
        Instant updatedAfter = ((java.sql.Timestamp) row.get("updated_at")).toInstant();
        assertThat(updatedAfter).isAfter(updatedBefore);
        assertThat(objectMapper.readTree(updateResult.getResponse().getContentAsString()).path("updatedAt").asText())
                .isNotBlank();
        assertThat(row.get("reviewed_by")).isNull();
        assertThat(row.get("reviewed_at")).isNull();
        assertThat(row.get("review_reason")).isNull();
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    void bindingAndUtf16ValidationErrorsUseStableEnvelopeAndSameMeaningIgnoringRequestId() throws Exception {
        Account account = register("invalid");
        verifyEmail(account);
        Session session = login(account.email());
        var malformedJson = mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\",\"category\":\"NOT_A_CATEGORY\",\"submit\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andReturn();
        var badPath = mockMvc.perform(put("/api/community/posts/not-a-long").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("x", "y", true)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andReturn();
        ObjectNode enumError = (ObjectNode) objectMapper.readTree(malformedJson.getResponse().getContentAsString());
        ObjectNode pathError = (ObjectNode) objectMapper.readTree(badPath.getResponse().getContentAsString());
        enumError.remove("request_id");
        pathError.remove("request_id");
        assertThat(pathError).isEqualTo(enumError);

        String supplementary = new String(Character.toChars(0x1F600)).repeat(101);
        mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody(supplementary, "Body", true)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void malformedBodiesAndPathIdsReturnSafe422WithoutWritingPosts() throws Exception {
        Account account = register("malformed");
        verifyEmail(account);
        Session session = login(account.email());
        long before = postCount();

        assertInvalidRequest(session, "{\"title\":\"x\",\"content\":\"y\",\"category\":\"NEWS\",\"submit\":true}",
                "Dữ liệu yêu cầu không hợp lệ.");
        assertInvalidRequest(session, "{\"title\":\"x\",\"content\":\"y\",\"category\":\"QUESTION\",\"submit\":\"abc\"}",
                "Dữ liệu yêu cầu không hợp lệ.");
        assertInvalidRequest(session, "{\"title\":\"x\",\"content\":\"y\",\"category\":\"QUESTION\"}",
                "Dữ liệu không hợp lệ.");
        assertInvalidRequest(session, "{\"title\":\"x\",\"content\":\"y\",\"category\":\"QUESTION\",\"submit\":null}",
                "Dữ liệu không hợp lệ.");
        assertInvalidRequest(session, "{not-json", "Dữ liệu yêu cầu không hợp lệ.");
        assertInvalidRequest(session, "", "Dữ liệu yêu cầu không hợp lệ.");

        MvcResult pathFailure = mockMvc.perform(put("/api/community/posts/abc")
                        .cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("x", "y", true)))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertSafeValidationEnvelope(pathFailure);
        assertThat(postCount()).isEqualTo(before);
    }

    @Test
    void stripsUnicodeWhitespaceAndEnforcesUtf16AndPostFieldBoundaries() throws Exception {
        Account account = register("limits");
        verifyEmail(account);
        Session session = login(account.email());

        String title = "\u3000" + "t".repeat(198) + "\u3000";
        String content = "\u3000body\nline\u3000";
        MvcResult created = mockMvc.perform(post("/api/community/posts")
                        .cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody(title, content, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("t".repeat(198)))
                .andExpect(jsonPath("$.content").value("body\nline")).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM community.posts WHERE id=?", String.class, id))
                .isEqualTo("t".repeat(198));

        String maximumTitle = "t".repeat(200);
        createPost(session, maximumTitle, "c", false);
        assertValidationPost(session, "t".repeat(201), "c");
        createPost(session, "t", "c".repeat(50_000), false);
        assertValidationPost(session, "t", "c".repeat(50_001));
        assertValidationPost(session, "\u3000\u3000", "body");
        assertValidationPost(session, "title", "\u3000\u3000");
    }

    @Test
    void updateHidesReviewReasonInResponseButPreservesDatabaseMetadata() throws Exception {
        Account account = register("review-metadata");
        verifyEmail(account);
        Session session = login(account.email());
        long authorId = currentUserId(session.access());
        Instant createdAt = Instant.parse("2026-10-30T01:02:03Z");
        Instant publishedAt = Instant.parse("2026-10-30T02:03:04Z");
        String reviewReason = "Please revise the source. ";
        Long id = jdbcTemplate.queryForObject("INSERT INTO community.posts(author_id, category, title, content, status, "
                        + "review_reason, published_at, created_at, updated_at) VALUES (?, 'QUESTION', 'Old', 'Text', "
                        + "'APPROVED', ?, ?, ?, ?) RETURNING id", Long.class,
                authorId, reviewReason, java.sql.Timestamp.from(publishedAt), java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));

        MvcResult result = mockMvc.perform(put("/api/community/posts/{id}", id)
                        .cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("New", "Updated", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reviewReason").value(org.hamcrest.Matchers.nullValue())).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("reviewReason")).isTrue();
        var row = jdbcTemplate.queryForMap("SELECT author_id, created_at, published_at, review_reason, status "
                + "FROM community.posts WHERE id=?", id);
        assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(authorId);
        assertThat(((java.sql.Timestamp) row.get("created_at")).toInstant()).isEqualTo(createdAt);
        assertThat(((java.sql.Timestamp) row.get("published_at")).toInstant()).isEqualTo(publishedAt);
        assertThat(row.get("review_reason")).isEqualTo(reviewReason);
    }

    @Test
    void nonexistentAndNonOwnedPostsReturnEquivalentForbiddenBodies() throws Exception {
        Account owner = register("owner");
        verifyEmail(owner);
        Session ownerSession = login(owner.email());
        long ownedId = createPost(ownerSession, "Owned", "Body", false);

        Account other = register("other");
        verifyEmail(other);
        Session otherSession = login(other.email());
        MvcResult nonOwner = updateForbidden(otherSession, ownedId);
        MvcResult missing = updateForbidden(otherSession, Long.MAX_VALUE);
        JsonNode nonOwnerBody = objectMapper.readTree(nonOwner.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(missing.getResponse().getContentAsString());
        assertThat(nonOwnerBody.path("request_id").asText()).isNotBlank();
        assertThat(missingBody.path("request_id").asText()).isNotBlank();
        ((ObjectNode) nonOwnerBody).remove("request_id");
        ((ObjectNode) missingBody).remove("request_id");
        assertThat(nonOwnerBody).isEqualTo(missingBody);
    }

    @Test
    void allPostStatusesCountTowardQuotaAndRetryTargetsVietnameseMidnight() throws Exception {
        testClock.set(TEST_NOW);
        try {
            Account account = register("all-status-quota");
            verifyEmail(account);
            Session session = login(account.email());
            long authorId = currentUserId(session.access());
            for (String postStatus : new String[] {"DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN"}) {
                jdbcTemplate.update("INSERT INTO community.posts(author_id, category, title, content, status, "
                                + "review_reason, published_at, created_at, updated_at) "
                                + "VALUES (?, 'QUESTION', ?, 'Quota status fixture', ?, ?, ?, ?, ?)",
                        authorId, "Status " + postStatus, postStatus,
                        "REJECTED".equals(postStatus) ? "Prior review" : null,
                        "APPROVED".equals(postStatus) ? java.sql.Timestamp.from(TEST_NOW) : null,
                        java.sql.Timestamp.from(TEST_NOW), java.sql.Timestamp.from(TEST_NOW));
            }

            MvcResult limited = mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                            .header("X-XSRF-TOKEN", session.csrf().getValue())
                            .contentType(MediaType.APPLICATION_JSON).content(writeBody("Sixth", "Body", false)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error_code").value("TOO_MANY_REQUESTS"))
                    .andExpect(jsonPath("$.retryAfterSeconds").value(1800)).andReturn();
            assertThat(objectMapper.readTree(limited.getResponse().getContentAsString()).path("request_id").asText())
                    .isNotBlank();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM community.posts WHERE author_id=?",
                    Integer.class, authorId)).isEqualTo(5);
        } finally {
            testClock.reset();
        }
    }

    @Test
    void literalMarkupRoundTripsCharacterForCharacterThroughJsonAndPostgres() throws Exception {
        Account account = register("literal-markup");
        verifyEmail(account);
        Session session = login(account.email());
        for (String literal : new String[] {"<img src=x onerror=alert(1)>", "**đậm**"}) {
            String requestValue = "\u3000" + literal + "\u3000";
            MvcResult result = mockMvc.perform(post("/api/community/posts")
                            .cookie(session.access(), session.csrf())
                            .header("X-XSRF-TOKEN", session.csrf().getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(writeBody(requestValue, requestValue, false)))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            var row = jdbcTemplate.queryForMap("SELECT title, content FROM community.posts WHERE id=?",
                    response.path("id").asLong());
            for (String field : new String[] {"title", "content"}) {
                assertThat(response.path(field).asText()).isEqualTo(requestValue.strip()).isEqualTo(literal);
                assertThat(row.get(field)).isEqualTo(requestValue.strip()).isEqualTo(response.path(field).asText());
            }
        }
    }

    @Test
    void invalidBodyPrecedesUnverifiedEmailWithValidAuthenticationAndCsrf() throws Exception {
        Account account = register("unverified-invalid");
        Session session = login(account.email());
        long before = postCount();
        mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("\u3000", "body", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        assertThat(postCount()).isEqualTo(before);
    }

    @Test
    void authorCannotUpdateCommittedHiddenPostAndEveryColumnRemainsUnchanged() throws Exception {
        Account account = register("hidden-owner");
        verifyEmail(account);
        Session session = login(account.email());
        long id = createPost(session, "Original", "Original body", true);
        jdbcTemplate.update("UPDATE community.posts SET status='HIDDEN', review_reason='Hidden by review' WHERE id=?", id);
        var before = jdbcTemplate.queryForMap("SELECT * FROM community.posts WHERE id=?", id);
        mockMvc.perform(put("/api/community/posts/{id}", id).cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Changed", "Changed body", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("POST_NOT_EDITABLE"));
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM community.posts WHERE id=?", id)).isEqualTo(before);
    }

    private long createPost(Session session, String title, String content, boolean submit) throws Exception {
        JsonNode result = objectMapper.readTree(mockMvc.perform(post("/api/community/posts")
                        .cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody(title, content, submit)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return result.path("id").asLong();
    }

    private Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "confirmPassword", PASSWORD, "displayName", "Post Test User", "acceptTerms", true))))
                .andExpect(status().isCreated());
        return new Account(email);
    }

    private void assertInvalidRequest(Session session, String body, String expectedMessage) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertSafeValidationEnvelope(result, expectedMessage);
    }

    private void assertSafeValidationEnvelope(MvcResult result) throws Exception {
        assertSafeValidationEnvelope(result, "Dữ liệu yêu cầu không hợp lệ.");
    }

    private void assertSafeValidationEnvelope(MvcResult result, String expectedMessage) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.has("error_code")).isTrue();
        assertThat(body.has("message")).isTrue();
        assertThat(body.has("request_id")).isTrue();
        assertThat(body.has("details")).isTrue();
        assertThat(body.path("error_code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("message").asText()).isEqualTo(expectedMessage);
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("JsonParseException", "HttpMessageNotReadableException",
                "com.fasterxml.jackson", "stackTrace", "not-json");
    }

    private void assertValidationPost(Session session, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/community/posts").cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody(title, content, true)))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("error_code").asText())
                .isEqualTo("VALIDATION_FAILED");
    }

    private MvcResult updateForbidden(Session session, long id) throws Exception {
        return mockMvc.perform(put("/api/community/posts/{id}", id).cookie(session.access(), session.csrf())
                        .header("X-XSRF-TOKEN", session.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(writeBody("Change", "Body", false)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                .andReturn();
    }

    private long postCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM community.posts", Long.class);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode messages = readMailpitMessages().path("messages");
        JsonNode message = null;
        for (JsonNode candidate : messages) {
            if (candidate.path("To").toString().contains(account.email())) {
                message = candidate;
                break;
            }
            JsonNode detail = readMailpitMessage(candidate.path("ID").asText());
            if (detail.path("To").toString().contains(account.email())) {
                message = detail;
                break;
            }
        }
        assertThat(message).as("verification email for synthetic test user").isNotNull();
        JsonNode detail = message.has("Text") ? message : readMailpitMessage(message.path("ID").asText());
        Matcher matcher = VERIFY_TOKEN.matcher(detail.path("Text").asText());
        assertThat(matcher.find()).isTrue();
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", matcher.group(1)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    private Session login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return new Session(requiredCookie(result, "access_token"), requiredCookie(result, "XSRF-TOKEN"));
    }

    private long currentUserId(Cookie access) throws Exception {
        return objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                                .cookie(access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    private Cookie requiredCookie(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName())) return cookie;
        }
        throw new AssertionError("Missing " + name + " cookie");
    }

    private JsonNode readMailpitMessages() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode readMailpitMessage(String id) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private String writeBody(String title, String content, boolean submit) throws Exception {
        return objectMapper.writeValueAsString(Map.of("title", title, "content", content,
                "category", "EXPERIENCE", "submit", submit));
    }

    private record Account(String email) { }
    private record Session(Cookie access, Cookie csrf) { }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @org.springframework.context.annotation.Primary
        Clock t04IntegrationClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> fixedInstant = new AtomicReference<>();

        void set(Instant instant) {
            fixedInstant.set(instant);
        }

        void reset() {
            fixedInstant.set(null);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            Instant fixed = fixedInstant.get();
            return fixed == null ? Clock.system(zone) : Clock.fixed(fixed, zone);
        }

        @Override
        public Instant instant() {
            Instant fixed = fixedInstant.get();
            return fixed == null ? Instant.now() : fixed;
        }
    }
}
