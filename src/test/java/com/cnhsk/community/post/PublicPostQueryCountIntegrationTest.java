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
import java.util.UUID;

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
@Import(PublicPostQueryCountIntegrationTest.SqlConfiguration.class)
class PublicPostQueryCountIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/public/community/posts";
    private static final String PASSWORD = "PublicCountTestPass123";
    private static final Instant PUBLISHED = Instant.parse("2026-10-30T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlCapture capture;

    @BeforeEach
    @AfterEach
    void cleanCommunityFixtures() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void FR014_listAndDetailUseBoundedGroupedQueriesWithCorrectCountsAsPostsAndAuthorsGrow() throws Exception {
        List<Long> authors = new ArrayList<>();
        for (int index = 0; index < 5; index++) authors.add(register(index));
        List<Long> posts = seedPosts(authors, 25, 0);

        Captured five = request(BASE + "?size=5", 200);
        Captured twenty = request(BASE + "?size=20", 200);
        assertPage(five, 5, 25);
        assertPage(twenty, 20, 25);
        assertThat(five.sql()).hasSameSizeAs(twenty.sql());
        assertGroupedBatchQueries(five, 5);
        assertGroupedBatchQueries(twenty, 5);
        assertThat(twenty.body().path("items").findValues("id")).isNotEmpty();
        List<Long> pageAuthors = new ArrayList<>();
        twenty.body().path("items").forEach(item -> pageAuthors.add(item.path("author").path("id").asLong()));
        assertThat(pageAuthors).containsAll(authors);

        Captured detail = request(BASE + "/" + posts.get(0), 200);
        assertThat(detail.sql()).hasSize(4);
        assertThat(detail.body().path("content").asText()).hasSize(50_000);
        assertThat(detail.body().path("excerpt").asText()).hasSize(200);
        assertThat(detail.body().path("commentCount").asLong()).isEqualTo(3);
        assertThat(detail.body().path("likeCount").asLong()).isEqualTo(2);
        assertGroupedBatchQueries(detail, 4);

        for (String page : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            Captured absent = request(BASE + "?page=" + page + "&size=50", 200);
            assertThat(absent.sql()).hasSize(1);
            assertThat(absent.sql().get(0)).contains("count(", "community.posts");
            assertThat(absent.body().path("items")).isEmpty();
            assertThat(absent.body().path("totalElements").asLong()).isEqualTo(25);
        }
        Captured missing = request(BASE + "/" + Long.MAX_VALUE, 404);
        assertThat(missing.sql()).hasSize(1);
        assertThat(missing.body().path("error_code").asText()).isEqualTo("POST_NOT_FOUND");

        for (int index = 5; index < 10; index++) authors.add(register(index));
        seedPosts(authors, 25, 25);
        Captured grown = request(BASE + "?size=20", 200);
        assertPage(grown, 20, 50);
        assertThat(grown.sql()).hasSameSizeAs(twenty.sql());
        assertGroupedBatchQueries(grown, 5);
        List<Long> grownAuthors = new ArrayList<>();
        grown.body().path("items").forEach(item -> grownAuthors.add(item.path("author").path("id").asLong()));
        assertThat(grownAuthors).containsAll(authors);
        System.out.printf(Locale.ROOT, "PUBLIC_POST_SQL_EVIDENCE posts=50 authors=10 page5=%d page20=%d grown20=%d detail=%d missing=%d%n",
                five.sql().size(), twenty.sql().size(), grown.sql().size(), detail.sql().size(), missing.sql().size());
    }

    @Test
    void FR012_twentyPostPageMeetsLatencyBudgetWithLongContentAfterWarmup() throws Exception {
        List<Long> authors = new ArrayList<>();
        for (int index = 0; index < 5; index++) authors.add(register(index));
        seedPosts(authors, 25, 0);
        for (int index = 0; index < 5; index++) assertPage(request(BASE + "?size=20", 200), 20, 25);
        long[] elapsed = new long[20];
        int maxSql = 0;
        for (int index = 0; index < elapsed.length; index++) {
            Captured response = request(BASE + "?size=20", 200);
            assertPage(response, 20, 25);
            assertGroupedBatchQueries(response, 5);
            elapsed[index] = response.elapsedNanos();
            maxSql = Math.max(maxSql, response.sql().size());
        }
        Arrays.sort(elapsed);
        double p95Millis = elapsed[(int) Math.ceil(elapsed.length * 0.95) - 1] / 1_000_000.0;
        double totalMillis = Arrays.stream(elapsed).sum() / 1_000_000.0;
        System.out.printf(Locale.ROOT,
                "PUBLIC_POST_LATENCY_EVIDENCE warmup=5 samples=20 posts=25 authors=5 contentChars=50000 pageSize=20 p95Ms=%.3f totalMs=%.3f maxSql=%d%n",
                p95Millis, totalMillis, maxSql);
        assertThat(p95Millis).as("20-post HTTP pipeline p95 in milliseconds").isLessThan(300.0);
    }

    @Test
    void FR014_emptyDatasetIssuesOnlyTotalCountAndNoAuthorOrGroupedInQuery() throws Exception {
        Captured response = request(BASE, 200);
        assertThat(response.sql()).hasSize(1);
        assertThat(response.sql().get(0)).contains("count(", "community.posts").doesNotContain("group by", "auth.users");
        assertThat(response.body().path("items")).isEmpty();
        assertThat(response.body().path("totalElements").asLong()).isZero();
    }

    private long register(int number) throws Exception {
        String email = "public-count-" + UUID.randomUUID() + "@example.test";
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "confirmPassword", PASSWORD, "displayName", "Count Author " + number, "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(registered.getResponse().getContentAsString()).path("userId").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private List<Long> seedPosts(List<Long> authors, int count, int offset) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long author = authors.get(index % authors.size());
            long id = jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,published_at) "
                            + "VALUES (?,'QUESTION',?,?,'APPROVED',?) RETURNING id", Long.class,
                    author, "Count fixture " + (index + offset), "x".repeat(50_000),
                    Timestamp.from(PUBLISHED.plusSeconds(index + offset)));
            ids.add(id);
            for (int comment = 0; comment < 3; comment++) {
                jdbc.update("INSERT INTO community.comments(post_id,author_id,content,status) VALUES (?,?,'Visible fixture','VISIBLE')",
                        id, author);
            }
            for (String state : List.of("HIDDEN", "DELETED")) {
                jdbc.update("INSERT INTO community.comments(post_id,author_id,content,status,moderation_reason) "
                        + "VALUES (?,?,'Excluded fixture',?,'Synthetic reason')", id, author, state);
            }
            for (int reactor = 0; reactor < 2; reactor++) {
                jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')",
                        authors.get(reactor), id);
            }
            for (String kind : List.of("FOLLOW", "REPORT")) {
                jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,?)",
                        author, id, kind);
            }
            jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'COMMENT',?,'LIKE')",
                    author, id);
        }
        return ids;
    }

    private Captured request(String path, int expectedStatus) throws Exception {
        capture.start();
        long started = System.nanoTime();
        try {
            MvcResult result = mvc.perform(get(path)).andExpect(status().is(expectedStatus)).andReturn();
            byte[] serialized = result.getResponse().getContentAsByteArray();
            long elapsed = System.nanoTime() - started;
            return new Captured(mapper.readTree(serialized), capture.statements(), elapsed);
        } finally {
            capture.stop();
        }
    }

    private void assertPage(Captured response, int size, long total) {
        assertThat(response.body().path("items")).hasSize(size);
        assertThat(response.body().path("totalElements").asLong()).isEqualTo(total);
        for (JsonNode item : response.body().path("items")) {
            assertThat(item.path("commentCount").asLong()).isEqualTo(3);
            assertThat(item.path("likeCount").asLong()).isEqualTo(2);
            assertThat(item.path("excerpt").asText()).hasSize(200);
            assertThat(item.has("content")).isFalse();
            assertThat(item.path("author").path("displayName").asText()).startsWith("Count Author ");
        }
    }

    private void assertGroupedBatchQueries(Captured response, int maximum) {
        assertThat(response.sql()).hasSizeLessThanOrEqualTo(maximum);
        assertThat(response.sql().stream().filter(sql -> sql.contains("group by")).toList()).hasSize(2);
        assertThat(response.sql().stream().filter(sql -> sql.contains("from auth.users")).toList()).hasSize(1);
        assertThat(response.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select").doesNotContain("for update"));
        assertThat(response.sql().stream().filter(sql -> sql.contains("community.comments")).toList())
                .singleElement().satisfies(sql -> assertThat(sql).contains("group by", " in ", "status"));
        assertThat(response.sql().stream().filter(sql -> sql.contains("community.reactions")).toList())
                .singleElement().satisfies(sql -> assertThat(sql).contains("group by", " in ", "target_type", "kind"));
    }

    private record Captured(JsonNode body, List<String> sql, long elapsedNanos) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        SqlCapture publicPostSqlCapture() { return new SqlCapture(); }

        @Bean
        HibernatePropertiesCustomizer publicPostStatementInspector(SqlCapture capture) {
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
