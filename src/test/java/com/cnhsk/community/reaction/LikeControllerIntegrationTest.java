package com.cnhsk.community.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(LikeControllerIntegrationTest.SqlConfiguration.class)
class LikeControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/posts";
    private static final String PUBLIC = "/api/public/community/posts";
    private static final String PASSWORD = "LikeFixturePass123";
    private static final Instant TIME = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private SqlCapture capture;

    @BeforeEach
    @AfterEach
    void cleanCommunityFixtures() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void FR001_FR002_bothMethodsRequireLearnerAndAllowUnverifiedCookieAndBearerSessions() throws Exception {
        Account account = register();
        long id = seed(account.id(), "APPROVED", TIME);
        Session session = login(account);
        for (boolean like : List.of(true, false)) {
            error(mutation(like, id), 401, "UNAUTHENTICATED");
            for (String role : List.of("ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
                error(mutation(like, id).header("Authorization", bearer(account.id(), Set.of(role))), 403, "FORBIDDEN");
            }
            error(mutation(like, id).cookie(session.access()), 403, "CSRF_TOKEN_INVALID");
            error(mutation(like, id).cookie(session.access(), session.csrf())
                    .header("X-XSRF-TOKEN", "mismatched-token"), 403, "CSRF_TOKEN_INVALID");
            assertLike(ok(mutation(like, id).cookie(session.access(), session.csrf())
                    .header("X-XSRF-TOKEN", session.csrf().getValue())), like, like ? 1 : 0);
            assertLike(ok(mutation(like, id).header("Authorization", bearer(account.id(), Set.of("LEARNER")))), like, like ? 1 : 0);
            assertLike(ok(mutation(like, id).header("Authorization", bearer(account.id(), Set.of("ADMIN", "LEARNER")))), like, like ? 1 : 0);
        }
    }

    @Test
    void FR003_FR028_FR029_FR030_FR056_idempotentSelfLikesPreserveTimestampOtherReactionsAndReadModels() throws Exception {
        Account owner = register();
        Account other = register();
        long id = seed(owner.id(), "APPROVED", TIME);
        long another = seed(other.id(), "APPROVED", TIME.minusSeconds(1));
        reaction(other.id(), "POST", id, "LIKE");
        reaction(owner.id(), "POST", id, "REPORT");
        reaction(owner.id(), "POST", id, "FOLLOW");
        reaction(owner.id(), "COMMENT", id, "LIKE");
        reaction(owner.id(), "USER", id, "LIKE");
        reaction(owner.id(), "COLLECTION", id, "LIKE");
        reaction(owner.id(), "POST", another, "LIKE");
        List<Map<String, Object>> untouched = reactionRows();
        Map<String, Object> postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id);
        String token = bearer(owner.id(), Set.of("LEARNER"));

        assertReadModels(id, token, false, true, 1);
        assertLike(ok(mutation(true, id).header("Authorization", token)), true, 2);
        Timestamp created = likeTimestamp(owner.id(), id);
        for (int index = 0; index < 5; index++) {
            assertLike(ok(mutation(true, id).header("Authorization", token)), true, 2);
            assertThat(likeTimestamp(owner.id(), id)).isEqualTo(created);
            assertThat(likeRows(owner.id(), id)).isEqualTo(1);
        }
        assertReadModels(id, token, true, true, 2);
        for (int index = 0; index < 5; index++) {
            assertLike(ok(mutation(false, id).header("Authorization", token)), false, 1);
            assertThat(likeRows(owner.id(), id)).isZero();
            assertThat(reactionRows()).isEqualTo(untouched);
        }
        assertReadModels(id, token, false, true, 1);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", id)).isEqualTo(postBefore);

        assertLike(ok(mutation(true, id).header("Authorization", token)), true, 2);
        assertThat(likeRows(owner.id(), id)).isEqualTo(1);
        assertLike(ok(mutation(false, id).header("Authorization", token)), false, 1);
    }

    @Test
    void FR028_FR029_missingNonpublicAndInvalidIdsShareSafeNotFoundWithoutChangingRows() throws Exception {
        Account viewer = register();
        Account other = register();
        String token = bearer(viewer.id(), Set.of("LEARNER"));
        List<String> ids = new ArrayList<>(List.of(Long.toString(Long.MAX_VALUE), "0", "-1", "abc",
                "9223372036854775808", "-9223372036854775809"));
        for (String state : List.of("DRAFT", "PENDING", "REJECTED", "HIDDEN")) {
            for (long author : List.of(viewer.id(), other.id())) {
                long id = seed(author, state, TIME);
                ids.add(Long.toString(id));
                reaction(viewer.id(), "POST", id, "LIKE");
            }
        }
        List<Map<String, Object>> before = reactionRows();
        List<Map<String, Object>> posts = jdbc.queryForList("SELECT * FROM community.posts ORDER BY id");
        JsonNode expected = error(mutation(true, Long.MAX_VALUE).header("Authorization", token), 404, "POST_NOT_FOUND");
        for (boolean like : List.of(true, false)) {
            for (String id : ids) {
                assertThat(error(mutation(like, id).header("Authorization", token), 404, "POST_NOT_FOUND")).isEqualTo(expected);
            }
        }
        assertThat(reactionRows()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM community.posts ORDER BY id")).isEqualTo(posts);
    }

    @Test
    void FR028_FR029_FR031_successfulMutationsUseOnePostReadOneDmlAndOneGroupedCountWithoutLocks() throws Exception {
        Account viewer = register();
        long id = seed(viewer.id(), "APPROVED", TIME);
        String token = bearer(viewer.id(), Set.of("LEARNER"));
        for (boolean like : List.of(true, true, false, false)) {
            Captured response = captured(mutation(like, id).header("Authorization", token));
            assertLike(response.body(), like, like ? 1 : 0);
            List<String> community = response.communitySql();
            assertThat(community).hasSize(3);
            assertThat(community.stream().filter(sql -> sql.startsWith("select") && sql.contains("community.posts")).toList())
                    .singleElement().satisfies(sql -> assertThat(sql).contains("status"));
            assertThat(community.stream().filter(sql -> sql.startsWith(like ? "insert" : "delete")).toList())
                    .singleElement().satisfies(sql -> {
                        assertThat(sql).contains("community.reactions", "user_id", "target_type", "target_id", "kind");
                        if (like) assertThat(sql).contains("on conflict", "do nothing");
                    });
            assertThat(community.stream().filter(sql -> sql.startsWith("select") && sql.contains("community.reactions")).toList())
                    .singleElement().satisfies(sql -> assertThat(sql).contains("count(", "group by", "target_type", "kind"));
            assertThat(response.sql()).allSatisfy(sql -> assertThat(sql).doesNotContain("for update", "for no key update", "pg_advisory"));
            assertThat(response.sql().stream().filter(sql -> !sql.contains("community.")).toList())
                    .allSatisfy(sql -> assertThat(sql).startsWith("select").contains("auth."));
            System.out.printf(Locale.ROOT, "LIKE_SQL_EVIDENCE method=%s community=%d auth=%d%n",
                    like ? "POST" : "DELETE", community.size(), response.authSql().size());
        }
    }

    @Test
    void FR031_publicPagesKeepGroupedCountsAndConstantSqlBudgetAfterHttpLikes() throws Exception {
        List<Account> authors = new ArrayList<>();
        for (int index = 0; index < 5; index++) authors.add(register());
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            long id = seed(authors.get(index % authors.size()).id(), "APPROVED", TIME.plusSeconds(index));
            ids.add(id);
            reaction(authors.get(0).id(), "POST", id, "LIKE");
            reaction(authors.get(1).id(), "POST", id, "LIKE");
            reaction(authors.get(0).id(), "COMMENT", id, "LIKE");
            reaction(authors.get(0).id(), "POST", id, "REPORT");
            reaction(authors.get(0).id(), "POST", id, "FOLLOW");
        }
        long changed = ids.get(24);
        String token = bearer(authors.get(2).id(), Set.of("LEARNER"));
        assertLike(ok(mutation(true, changed).header("Authorization", token)), true, 3);
        Captured five = captured(get(PUBLIC).param("size", "5"));
        Captured twenty = captured(get(PUBLIC).param("size", "20"));
        assertThat(five.body().path("items")).hasSize(5);
        assertThat(twenty.body().path("items")).hasSize(20);
        assertThat(five.sql()).hasSameSizeAs(twenty.sql()).hasSizeLessThanOrEqualTo(5);
        for (Captured page : List.of(five, twenty)) {
            assertThat(page.body().path("totalElements").asLong()).isEqualTo(25);
            for (JsonNode item : page.body().path("items")) {
                assertThat(item.path("likeCount").asLong()).isEqualTo(item.path("id").asLong() == changed ? 3 : 2);
                assertThat(item.has("liked")).isFalse();
                assertThat(item.has("reported")).isFalse();
            }
            assertThat(page.sql().stream().filter(sql -> sql.contains("group by")).toList()).hasSize(2);
            assertThat(page.sql().stream().filter(sql -> sql.contains("auth.users")).toList()).hasSize(1);
            assertThat(page.sql().stream().filter(sql -> sql.contains("community.reactions")).toList())
                    .singleElement().satisfies(sql -> assertThat(sql).contains("group by", " in ", "target_type", "kind"));
        }
        assertThat(ok(get(PUBLIC + "/{id}", changed)).path("likeCount").asLong()).isEqualTo(3);
        assertLike(ok(mutation(false, changed).header("Authorization", token)), false, 2);
        assertThat(ok(get(PUBLIC + "/{id}", changed)).path("likeCount").asLong()).isEqualTo(2);
    }

    @Test
    void FR028_FR029_runtimeOpenApiMatchesLockedMethodsSchemaPathSecurityAndResponses() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        JsonNode path = document.path("paths").path(BASE + "/{id}/like");
        assertThat(fields(path)).containsExactlyInAnyOrder("post", "delete");
        for (String method : List.of("post", "delete")) {
            JsonNode operation = path.path(method);
            assertThat(operation.path("operationId").asText()).isEqualTo(method.equals("post") ? "likePost" : "unlikePost");
            assertThat(operation.has("requestBody")).isFalse();
            assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrder("200", "401", "403", "404");
            for (String code : List.of("200", "401", "403", "404")) {
                assertThat(operation.path("responses").path(code).path("content").path("application/json")
                        .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/"
                                + (code.equals("200") ? "LikeState" : "ErrorResponse"));
            }
            assertThat(operation.path("parameters")).hasSize(1);
            JsonNode id = operation.path("parameters").get(0);
            assertThat(id.path("name").asText()).isEqualTo("id");
            assertThat(id.path("in").asText()).isEqualTo("path");
            assertThat(id.path("required").asBoolean()).isTrue();
            assertThat(id.path("schema").path("type").asText()).isEqualTo("integer");
            assertThat(id.path("schema").path("format").asText()).isEqualTo("int64");
            JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
            assertThat(security).hasSize(2);
            List<String> schemes = new ArrayList<>();
            for (JsonNode alternative : security) {
                assertThat(fields(alternative)).hasSize(1);
                schemes.add(fields(alternative).get(0));
            }
            assertThat(schemes).containsExactlyInAnyOrder("cookieAuth", "bearerAuth");
        }
        JsonNode schema = document.path("components").path("schemas").path("LikeState");
        assertThat(fields(schema.path("properties"))).containsExactlyInAnyOrder("liked", "likeCount");
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(field -> required.add(field.asText()));
        assertThat(required).containsExactlyInAnyOrder("liked", "likeCount");
        assertThat(schema.path("properties").path("liked").path("type").asText()).isEqualTo("boolean");
        assertThat(schema.path("properties").path("likeCount").path("type").asText()).isEqualTo("integer");
        assertThat(schema.path("properties").path("likeCount").path("format").asText()).isEqualTo("int64");
    }

    @Test
    void FR028_FR029_FR056_likeBindingDoesNotChangeExistingOwnerViewerAndUpdateErrors() throws Exception {
        Account viewer = register();
        String token = bearer(viewer.id(), Set.of("LEARNER"));
        error(get(BASE + "/mine/abc").header("Authorization", token), 403, "FORBIDDEN");
        error(get(BASE + "/abc/viewer-state").header("Authorization", token), 404, "POST_NOT_FOUND");
        error(put(BASE + "/abc").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("title", "Synthetic", "content", "Body", "category", "QUESTION", "submit", false))),
                422, "VALIDATION_FAILED");
        for (boolean like : List.of(true, false)) {
            MvcResult result = mvc.perform(mutation(like, "mine").header("Authorization", token))
                    .andExpect(status().isNotFound()).andReturn();
            assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
            assertThat(((HandlerMethod) result.getHandler()).getMethod().getName()).isEqualTo(like ? "likePost" : "unlikePost");
            assertThat(mapper.readTree(result.getResponse().getContentAsByteArray()).path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
        }
    }

    private Account register() throws Exception {
        String email = "like-fixture-" + UUID.randomUUID() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", "Synthetic Like User", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsByteArray()).path("userId").asLong();
        assertThat(id).isPositive();
        return new Account(id, email);
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

    private long seed(long author, String state, Instant published) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at) "
                + "VALUES (?,'QUESTION','Synthetic like fixture','Synthetic body',?,'Synthetic reason',?) RETURNING id",
                Long.class, author, state, Timestamp.from(published));
    }

    private void reaction(long user, String target, long id, String kind) {
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind,created_at) VALUES (?,?,?,?,?)",
                user, target, id, kind, Timestamp.from(TIME));
    }

    private List<Map<String, Object>> reactionRows() {
        return jdbc.queryForList("SELECT * FROM community.reactions ORDER BY user_id,target_type,target_id,kind");
    }

    private long likeRows(long user, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? AND target_type='POST' AND target_id=? AND kind='LIKE'",
                Long.class, user, id);
    }

    private Timestamp likeTimestamp(long user, long id) {
        return jdbc.queryForObject("SELECT created_at FROM community.reactions WHERE user_id=? AND target_type='POST' AND target_id=? AND kind='LIKE'",
                Timestamp.class, user, id);
    }

    private String bearer(long id, Set<String> roles) {
        return "Bearer " + jwt.issueAccessToken(id, roles, false, UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder mutation(boolean like, Object id) {
        return like ? post(BASE + "/{id}/like", id) : delete(BASE + "/{id}/like", id);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int code, String errorCode) throws Exception {
        JsonNode body = mapper.readTree(mvc.perform(request).andExpect(status().is(code)).andReturn().getResponse().getContentAsByteArray());
        assertThat(body.path("error_code").asText()).isEqualTo(errorCode);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("Exception", "stackTrace", "java.lang", "org.springframework", "SELECT ");
        if (code != 422) assertThat(fields(body)).containsExactlyInAnyOrder("error_code", "message", "request_id");
        ((ObjectNode) body).remove("request_id");
        return body;
    }

    private void assertLike(JsonNode body, boolean liked, long count) {
        assertThat(fields(body)).containsExactlyInAnyOrder("liked", "likeCount");
        assertThat(body.path("liked").isBoolean()).isTrue();
        assertThat(body.path("liked").asBoolean()).isEqualTo(liked);
        assertThat(body.path("likeCount").isIntegralNumber()).isTrue();
        assertThat(body.path("likeCount").asLong()).isEqualTo(count);
    }

    private void assertReadModels(long id, String token, boolean liked, boolean reported, long count) throws Exception {
        JsonNode state = ok(get(BASE + "/{id}/viewer-state", id).header("Authorization", token));
        assertThat(fields(state)).containsExactlyInAnyOrder("liked", "reported");
        assertThat(state.path("liked").asBoolean()).isEqualTo(liked);
        assertThat(state.path("reported").asBoolean()).isEqualTo(reported);
        JsonNode detail = ok(get(PUBLIC + "/{id}", id));
        assertThat(detail.path("likeCount").asLong()).isEqualTo(count);
        assertThat(detail.has("liked")).isFalse();
        assertThat(detail.has("reported")).isFalse();
        List<JsonNode> found = new ArrayList<>();
        ok(get(PUBLIC)).path("items").forEach(item -> {
            if (item.path("id").asLong() == id) found.add(item);
        });
        assertThat(found).singleElement().satisfies(item -> assertThat(item.path("likeCount").asLong()).isEqualTo(count));
    }

    private List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private Captured captured(MockHttpServletRequestBuilder request) throws Exception {
        capture.start();
        try {
            return new Captured(ok(request), capture.statements());
        } finally {
            capture.stop();
        }
    }

    private record Account(long id, String email) { }
    private record Session(Cookie access, Cookie csrf) { }
    private record Captured(JsonNode body, List<String> sql) {
        List<String> communitySql() { return sql.stream().filter(statement -> statement.contains("community.")).toList(); }
        List<String> authSql() { return sql.stream().filter(statement -> statement.contains("auth.")).toList(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlCapture likeSqlCapture() { return new SqlCapture(); }

        @Bean
        HibernatePropertiesCustomizer likeStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }

        @Override
        public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip());
            return sql;
        }
    }
}
