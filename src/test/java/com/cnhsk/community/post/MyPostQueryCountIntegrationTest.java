package com.cnhsk.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MyPostQueryCountIntegrationTest.SqlConfiguration.class)
class MyPostQueryCountIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/posts";
    private static final String PASSWORD = "OwnCountTestPass123";
    private static final Instant TIME = Instant.parse("2026-10-30T12:00:00Z");
    private static final List<String> STATES = List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN");
    private static final Pattern OWNER_PREDICATE = Pattern.compile("\\bauthor_id\\s*=\\s*\\?");
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
    void FR016_realOwnerQueriesConstrainCountListAndDetailAtTheRepositoryAndRemainBoundedAsDataGrows() throws Exception {
        Account a = register();
        Account b = register();
        List<Long> ownIds = new ArrayList<>();
        List<Long> otherIds = new ArrayList<>();
        for (String state : STATES) {
            ownIds.add(seed(a.id(), state, TIME));
            otherIds.add(seed(b.id(), state, TIME.plusSeconds(1)));
        }
        Captured page = request(BASE + "/mine?authorId=" + b.id(), a, 200);
        assertOwnerPage(page, ownIds, 5);
        assertOwnerSql(page, 2);
        assertThat(page.sql().get(0)).contains("count(");
        assertThat(page.sql().get(1)).contains("order by");
        assertThat(page.sql().get(1).substring(page.sql().get(1).indexOf("order by")))
                .matches(".*updated_at desc.*id desc.*");
        for (int index = 0; index < STATES.size(); index++) {
            Captured filtered = request(BASE + "/mine?status=" + STATES.get(index), a, 200);
            assertOwnerPage(filtered, List.of(ownIds.get(index)), 1);
            assertOwnerSql(filtered, 2);
            filtered.sql().forEach(sql -> assertThat(where(sql)).contains("status"));
            Captured detail = request(BASE + "/mine/" + ownIds.get(index), a, 200);
            assertOwnerSql(detail, 1);
            assertThat(where(detail.sql().get(0))).contains(".id=?");
            assertThat(detail.body().path("id").asLong()).isEqualTo(ownIds.get(index));
        }
        Captured nonowner = request(BASE + "/mine/" + otherIds.get(0), a, 403);
        Captured missing = request(BASE + "/mine/" + Long.MAX_VALUE, a, 403);
        assertOwnerSql(nonowner, 1);
        assertOwnerSql(missing, 1);
        assertThat(nonowner.body().path("error_code").asText()).isEqualTo("FORBIDDEN");
        assertThat(missing.body().path("error_code").asText()).isEqualTo("FORBIDDEN");
        for (String number : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            Captured absent = request(BASE + "/mine?page=" + number + "&size=50", a, 200);
            assertOwnerSql(absent, 1);
            assertThat(absent.sql().get(0)).contains("count(");
            assertThat(absent.body().path("items")).isEmpty();
            assertThat(absent.body().path("totalElements").asLong()).isEqualTo(5);
        }
        for (int index = 0; index < 25; index++) {
            ownIds.add(seed(a.id(), STATES.get(index % 5), TIME.plusSeconds(index + 2)));
            seed(b.id(), STATES.get(index % 5), TIME.plusSeconds(index + 2));
        }
        Captured grown = request(BASE + "/mine?size=20&authorId=" + b.id(), a, 200);
        assertOwnerPage(grown, ownIds, 30);
        assertOwnerSql(grown, 2);
        assertThat(grown.body().path("items")).hasSize(20);
        System.out.printf(Locale.ROOT, "OWN_POST_SQL_EVIDENCE authors=2 rows=60 list=%d grownList=%d detail=1 missing=%d nonowner=%d%n",
                page.sql().size(), grown.sql().size(), missing.sql().size(), nonowner.sql().size());
    }

    @Test
    void FR016_emptyOwnerDatasetIssuesOnlyAnOwnershipScopedCount() throws Exception {
        Account a = register();
        Account b = register();
        for (String state : STATES) seed(b.id(), state, TIME);
        Captured empty = request(BASE + "/mine", a, 200);
        assertOwnerSql(empty, 1);
        assertThat(empty.sql().get(0)).contains("count(");
        assertThat(empty.body().path("items")).isEmpty();
        assertThat(empty.body().path("totalElements").asLong()).isZero();
    }

    @Test
    void FR056_realViewerLookupScopesUserTypeTargetAndKindsWithoutAggregatesOrAuthLookups() throws Exception {
        Account viewer = register();
        Account other = register();
        for (int flags = 0; flags < 4; flags++) {
            long id = seed(other.id(), "APPROVED", TIME);
            reaction(other.id(), "POST", id, "LIKE");
            reaction(other.id(), "POST", id, "REPORT");
            reaction(viewer.id(), "COMMENT", id, "LIKE");
            reaction(viewer.id(), "COMMENT", id, "REPORT");
            reaction(viewer.id(), "POST", id, "FOLLOW");
            if ((flags & 1) != 0) reaction(viewer.id(), "POST", id, "LIKE");
            if ((flags & 2) != 0) reaction(viewer.id(), "POST", id, "REPORT");
            Captured response = request(BASE + "/" + id + "/viewer-state", viewer, 200);
            assertThat(response.sql()).hasSize(2);
            assertReadOnly(response);
            assertThat(response.sql().get(0)).contains("community.posts");
            assertThat(where(response.sql().get(0))).contains("status", "approved", ".id=?");
            String reaction = response.sql().get(1);
            assertThat(reaction).contains("community.reactions");
            assertThat(where(reaction)).contains("user_id=?", "target_type", "'post'", "target_id=?", "kind", "'like'", "'report'");
            assertThat(response.body().path("liked").asBoolean()).isEqualTo((flags & 1) != 0);
            assertThat(response.body().path("reported").asBoolean()).isEqualTo((flags & 2) != 0);
        }
        Captured missing = request(BASE + "/" + Long.MAX_VALUE + "/viewer-state", viewer, 404);
        assertVisibilityMiss(missing);
        for (String state : STATES) {
            if (state.equals("APPROVED")) continue;
            for (long author : List.of(viewer.id(), other.id())) {
                long id = seed(author, state, TIME);
                reaction(viewer.id(), "POST", id, "LIKE");
                Captured hidden = request(BASE + "/" + id + "/viewer-state", viewer, 404);
                assertVisibilityMiss(hidden);
            }
        }
        System.out.println("VIEWER_STATE_SQL_EVIDENCE combinations=4 approved=2 nonpublic=1 missing=1 authLookups=0");
    }

    @Test
    void FR016_twentyOwnedPostsKeepQueryBudgetThroughHttpSerializationAfterWarmup() throws Exception {
        Account owner = register();
        for (int index = 0; index < 25; index++) seed(owner.id(), STATES.get(index % 5), TIME.plusSeconds(index));
        for (int index = 0; index < 5; index++) assertOwnerSql(request(BASE + "/mine?size=20", owner, 200), 2);
        long[] elapsed = new long[20];
        for (int index = 0; index < elapsed.length; index++) {
            Captured response = request(BASE + "/mine?size=20", owner, 200);
            assertOwnerSql(response, 2);
            assertThat(response.body().path("items")).hasSize(20);
            elapsed[index] = response.elapsedNanos();
        }
        Arrays.sort(elapsed);
        double p95Millis = elapsed[(int) Math.ceil(elapsed.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(Locale.ROOT, "OWN_POST_LATENCY_EVIDENCE warmup=5 samples=20 rows=25 contentChars=50000 pageSize=20 p95Ms=%.3f maxSql=2%n", p95Millis);
    }

    private Account register() throws Exception {
        String email = "own-count-" + UUID.randomUUID() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", "Synthetic Count Owner", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).path("userId").asLong();
        assertThat(id).isPositive();
        String token = jwt.issueAccessToken(id, Set.of("LEARNER"), false, UUID.randomUUID());
        return new Account(id, token);
    }

    private long seed(long author, String state, Instant updated) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) "
                + "VALUES (?,'QUESTION','Synthetic count fixture',?,?,?,?,?,?) RETURNING id", Long.class,
                author, "x".repeat(50_000), state, "Stored synthetic reason", Timestamp.from(TIME),
                Timestamp.from(TIME.minusSeconds(10)), Timestamp.from(updated));
    }

    private void reaction(long user, String type, long id, String kind) {
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,?,?,?)", user, type, id, kind);
    }

    private Captured request(String path, Account account, int expectedStatus) throws Exception {
        capture.start();
        long started = System.nanoTime();
        try {
            MvcResult result = mvc.perform(get(path).header("Authorization", "Bearer " + account.token()))
                    .andExpect(status().is(expectedStatus)).andReturn();
            byte[] serialized = result.getResponse().getContentAsByteArray();
            long elapsed = System.nanoTime() - started;
            return new Captured(mapper.readTree(serialized), capture.statements(), elapsed);
        } finally {
            capture.stop();
        }
    }

    private void assertOwnerPage(Captured response, List<Long> allowed, long total) {
        assertThat(response.body().path("totalElements").asLong()).isEqualTo(total);
        assertThat(response.body().path("items")).hasSize((int) Math.min(20, total));
        for (JsonNode item : response.body().path("items")) {
            assertThat(allowed).contains(item.path("id").asLong());
            assertThat(item.path("content").asText()).hasSize(50_000);
        }
    }

    private void assertOwnerSql(Captured response, int count) {
        assertThat(response.sql()).hasSize(count);
        assertReadOnly(response);
        for (String sql : response.sql()) {
            assertThat(sql).contains("community.posts");
            assertThat(OWNER_PREDICATE.matcher(where(sql)).find()).as("ownership predicate in WHERE").isTrue();
        }
    }

    private void assertReadOnly(Captured response) {
        assertThat(response.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select")
                .doesNotContain("for update", "auth.users", "community.comments", "group by", "moderation_cases"));
    }

    private void assertVisibilityMiss(Captured response) {
        assertThat(response.sql()).hasSize(1);
        assertReadOnly(response);
        assertThat(response.sql().get(0)).contains("community.posts").doesNotContain("community.reactions");
        assertThat(response.body().path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
    }

    private String where(String sql) {
        int start = sql.indexOf(" where ");
        assertThat(start).isGreaterThanOrEqualTo(0);
        String clause = sql.substring(start + 7);
        int order = clause.indexOf(" order by ");
        return order < 0 ? clause : clause.substring(0, order);
    }

    private record Account(long id, String token) { }
    private record Captured(JsonNode body, List<String> sql, long elapsedNanos) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlCapture ownPostSqlCapture() { return new SqlCapture(); }

        @Bean
        HibernatePropertiesCustomizer ownPostStatementInspector(SqlCapture capture) {
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
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }
}
