package com.cnhsk.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@Import(PublicCommentQueryCountIntegrationTest.SqlConfiguration.class)
class PublicCommentQueryCountIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private CommunityAuthors authors;
    private PublicCommentControllerIntegrationTest.Fixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new PublicCommentControllerIntegrationTest.Fixtures(mvc, mapper, jdbc);
        capture.beforeComments.set(null);
    }

    @AfterEach
    void cleanup() {
        capture.stop();
        capture.beforeComments.set(null);
        fixtures.cleanup();
    }

    @Test
    void FR018_fiftyAndTwoHundredMixedCommentsUseOneFlatSelectAndOneVisibleAuthorBatch() throws Exception {
        List<Long> visibleAuthors = new ArrayList<>();
        for (int index = 0; index < 5; index++) visibleAuthors.add(fixtures.register());
        long hiddenAuthor = fixtures.register();
        long post = fixtures.post(visibleAuthors.get(0), PostStatus.APPROVED);
        List<Long> expected = seedTree(post, visibleAuthors, hiddenAuthor, 50, true);
        Captured fifty = request(post, 200);
        assertThat(PublicCommentControllerIntegrationTest.ids(fifty.body())).containsExactlyElementsOf(expected);
        assertTreeQueries(fifty, 3);
        verify(authors).resolve(Set.copyOf(visibleAuthors));
        assertThat(fifty.body()).hasSize(50);
        assertThat(fifty.body().findValues("depth")).extracting(JsonNode::asInt).contains(0, 1, 2, 3);
        long largerPost = fixtures.post(visibleAuthors.get(0), PostStatus.APPROVED);
        seedTree(largerPost, visibleAuthors, hiddenAuthor, 200, true);
        Captured larger = request(largerPost, 200);
        assertTreeQueries(larger, 3);
        assertThat(larger.sql()).hasSameSizeAs(fifty.sql());
        verify(authors).resolve(Set.copyOf(visibleAuthors));
        System.out.println("COMMENT_TREE_SQL_EVIDENCE nodes=50,200 depth=3 postSelect=1 commentSelect=1 authorSelect=1 totalSql=3 authorBatch=1 paginationCount=0");
    }

    @Test
    void FR018_emptyPrunedAndNotFoundTreesSkipAllUnnecessaryReads() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        for (int iteration = 0; iteration < 2; iteration++) {
            Captured result = request(post, 200);
            assertThat(result.body()).isEmpty();
            assertTreeQueries(result, 2);
            verify(authors, never()).resolve(anySet());
            if (iteration == 0) fixtures.comment(post, null, author, 0, CommentStatus.DELETED, "redacted", PublicCommentControllerIntegrationTest.TIME);
        }
        for (PostStatus status : List.of(PostStatus.DRAFT, PostStatus.PENDING, PostStatus.REJECTED, PostStatus.HIDDEN)) {
            Captured missing = request(fixtures.post(author, status), 404);
            assertTreeQueries(missing, 1);
            verify(authors, never()).resolve(anySet());
        }
        assertTreeQueries(request(Long.MAX_VALUE, 404), 1);
        System.out.println("COMMENT_TREE_EMPTY_SQL_EVIDENCE empty=2 pruned=2 nonpublic=1 missing=1 authorBatch=0 paginationCount=0");
    }

    @Test
    void FR018_twoHundredFiveHundredCharacterCommentsMeetTheHttpPipelineP95Budget() throws Exception {
        List<Long> owners = new ArrayList<>();
        for (int i = 0; i < 5; i++) owners.add(fixtures.register());
        long post = fixtures.post(owners.get(0), PostStatus.APPROVED);
        seedTree(post, owners, owners.get(0), 200, false, 500);
        for (int i = 0; i < 5; i++) assertTreeQueries(request(post, 200), 3);
        long[] elapsed = new long[20];
        for (int i = 0; i < elapsed.length; i++) {
            Captured response = request(post, 200);
            assertThat(response.body()).hasSize(200);
            assertThat(response.body().get(0).path("content").asText()).hasSize(500);
            assertTreeQueries(response, 3);
            verify(authors).resolve(Set.copyOf(owners));
            elapsed[i] = response.elapsedNanos();
        }
        Arrays.sort(elapsed);
        double p95 = elapsed[(int) Math.ceil(elapsed.length * 0.95) - 1] / 1_000_000.0;
        System.out.printf(Locale.ROOT,
                "COMMENT_TREE_LATENCY_EVIDENCE warmup=5 samples=20 nodes=200 depth=3 authors=5 contentChars=500 p95Ms=%.3f commentSql=1 totalSql=3%n", p95);
        assertThat(p95).as("200-comment HTTP pipeline p95 in milliseconds").isLessThan(300.0);
    }

    @Test
    void FR018_publicHttpReadUsesOneRepeatableReadSnapshotWithoutBlockingConcurrentPostWrites() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        long original = fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "snapshot original", PublicCommentControllerIntegrationTest.TIME);
        AtomicReference<String> isolation = new AtomicReference<>();
        TransactionTemplate writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        capture.beforeComments.set(() -> {
            isolation.set(jdbc.queryForObject("show transaction_isolation", String.class));
            writer.executeWithoutResult(transaction -> {
                jdbc.update("update community.posts set status='PENDING' where id=?", post);
                fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "committed after snapshot", PublicCommentControllerIntegrationTest.TIME.plusSeconds(1));
            });
        });
        Captured read = request(post, 200);
        assertThat(isolation.get()).isEqualTo("repeatable read");
        assertThat(PublicCommentControllerIntegrationTest.ids(read.body())).containsExactly(original);
        assertThat(jdbc.queryForObject("select status from community.posts where id=?", String.class, post)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from community.comments where post_id=?", Long.class, post)).isEqualTo(2);
        assertTreeQueries(request(post, 404), 1);
        System.out.println("COMMENT_TREE_SNAPSHOT_EVIDENCE isolation=repeatable_read concurrentWriterCommitted=true initialSnapshotComments=1 committedComments=2 nextRequest=404 writeLock=false");
    }

    private List<Long> seedTree(long post, List<Long> visibleAuthors, long hiddenAuthor, int count, boolean mixed) {
        return seedTree(post, visibleAuthors, hiddenAuthor, count, mixed, 5000);
    }

    private List<Long> seedTree(long post, List<Long> visibleAuthors, long hiddenAuthor, int count, boolean mixed, int contentChars) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int depth = index % 4;
            CommentStatus status = mixed && depth < 2 && index < count - 1
                    ? (depth == 0 ? CommentStatus.DELETED : CommentStatus.HIDDEN) : CommentStatus.VISIBLE;
            long author = status == CommentStatus.VISIBLE ? visibleAuthors.get(index % visibleAuthors.size()) : hiddenAuthor;
            ids.add(fixtures.comment(post, depth == 0 ? null : ids.get(index - 1), author, depth, status,
                    "字".repeat(contentChars), PublicCommentControllerIntegrationTest.TIME.plusSeconds(index)));
        }
        return ids;
    }

    private Captured request(long post, int expectedStatus) throws Exception {
        clearInvocations(authors);
        capture.start();
        long start = System.nanoTime();
        try {
            byte[] serialized = mvc.perform(get(PublicCommentControllerIntegrationTest.path(post)))
                    .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray();
            long elapsed = System.nanoTime() - start;
            return new Captured(mapper.readTree(serialized), capture.statements(), elapsed);
        } finally { capture.stop(); }
    }

    private void assertTreeQueries(Captured response, int total) {
        assertThat(response.sql()).hasSize(total);
        assertThat(response.sql()).allSatisfy(sql -> assertThat(sql).startsWith("select")
                .doesNotContain("count(", "group by", "for update", "for no key update", "community.reactions"));
        List<String> postQueries = response.sql().stream().filter(sql -> sql.contains("from community.posts")).toList();
        assertThat(postQueries).singleElement().satisfies(sql -> assertThat(sql).contains("status='approved'"));
        List<String> commentQueries = response.sql().stream().filter(sql -> sql.contains("from community.comments")).toList();
        assertThat(commentQueries).hasSize(total == 1 ? 0 : 1);
        commentQueries.forEach(sql -> assertThat(sql).contains("post_id", "order by").doesNotContain("fetch first", "offset"));
        assertThat(response.sql().stream().filter(sql -> sql.contains("from auth.users")).toList()).hasSize(total == 3 ? 1 : 0);
    }

    private record Captured(JsonNode body, List<String> sql, long elapsedNanos) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture commentTreeSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer commentTreeStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        private final AtomicReference<Runnable> beforeComments = new AtomicReference<>();
        void start() { active.set(new ArrayList<>()); }
        void stop() { active.remove(); }
        List<String> statements() { return List.copyOf(active.get()); }
        @Override public String inspect(String sql) {
            List<String> current = active.get();
            if (current != null) {
                String normalized = sql.toLowerCase(Locale.ROOT).strip();
                current.add(normalized);
                if (normalized.contains("from community.comments")) {
                    Runnable callback = beforeComments.getAndSet(null);
                    if (callback != null) callback.run();
                }
            }
            return sql;
        }
    }
}
