package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.KnowledgeEdgeRowWriter;
import com.cnhsk.learning.content.importer.write.KnowledgeNodeRowWriter;
import com.cnhsk.learning.content.importer.write.RowWriteRejectedException;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class KnowledgeEdgeConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";
    private static final String LOCK_KEY = "knowledge:edge-graph";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private KnowledgePointRepository points;
    @Autowired private KnowledgeEdgeRowWriter writer;
    @Autowired private KnowledgeNodeRowWriter nodeWriter;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private AuditLogRepository auditLogs;
    private Set<Long> baseline;
    private User user;

    @BeforeEach
    void setup() {
        baseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        for (int id = 901; id <= 903; id++) points.saveAndFlush(new KnowledgePoint("TV1-" + id,
                "Nút đồng thời tự soạn", KnowledgeDomain.TV, "示例", (short) 1));
        user = users.saveAndFlush(new User("edge-concurrent-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người nhập đồng thời"));
    }

    @AfterEach
    void cleanup() {
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(run -> user.getId().equals(run.getUserId())).toList());
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId).filter(id -> !baseline.contains(id)).toList());
        users.deleteById(user.getId());
    }

    @ParameterizedTest
    @CsvSource({"TV1-902,TV1-901,PREREQ,EDGE_CYCLE", "TV1-901,TV1-902,SUPPORT,EDGE_TYPE_CONFLICT",
            "TV1-901,TV1-902,PREREQ,UNCHANGED", "TV1-903,TV1-902,SUPPORT,CREATE"})
    void AT07_AT08_concurrentTransactionsSerializeBeforeReadingOrLockingTheirTarget(
            String from, String to, String type, String outcome) throws Exception {
        race(row("TV1-901", "TV1-902", "PREREQ"), row(from, to, type), outcome);
        assertThat(point("TV1-902").getPrereqIds()).containsExactly(point("TV1-901").getId());
        assertThat(point("TV1-901").getPrereqIds()).isEmpty();
        if (outcome.equals("CREATE")) assertThat(point("TV1-902").getSupportIds()).containsExactly(point("TV1-903").getId());
        else assertThat(point("TV1-902").getSupportIds()).isEmpty();
    }

    @Test
    void AT07_threeNodeCycleIsAlsoRejectedAcrossDifferentTargets() throws Exception {
        new TransactionTemplate(manager).executeWithoutResult(status -> writer.write(row("TV1-903", "TV1-901", "PREREQ")));
        race(row("TV1-901", "TV1-902", "PREREQ"), row("TV1-902", "TV1-903", "PREREQ"), "EDGE_CYCLE");
        assertThat(point("TV1-903").getPrereqIds()).isEmpty();
    }

    @Test
    void nodeWriterCanWaitForTheTargetWithoutTakingTheGraphLockOrLosingTheNewEdge() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                var result = writer.write(row("TV1-901", "TV1-902", "PREREQ"));
                written.countDown(); await(release); return result;
            }));
            await(written);
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> nodeWriter.write(
                    new ValidatedRow(2, Map.of("kid", "TV1-902", "name_vi", "Tên sau cạnh", "name_zh", "示例"), null, null))));
            awaitCondition(() -> jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%knowledge_points%'", Long.class) >= 1);
            assertThat(waitingGraphLocks()).isZero();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.CREATE);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.UPDATE);
            assertThat(point("TV1-902").getPrereqIds()).containsExactly(point("TV1-901").getId());
            assertThat(point("TV1-902").getTitle()).isEqualTo("Tên sau cạnh");
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twoApiConfirmsKeepConcurrentImport409AndRetryRechecksTheCommittedGraph() throws Exception {
        long firstId = upload(row("TV1-901", "TV1-902", "PREREQ"));
        long secondId = upload(row("TV1-902", "TV1-901", "PREREQ"));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.query(LOCK_SQL, result -> { }, LOCK_KEY);
                held.countDown(); await(release);
            }));
            await(held);
            var first = pool.submit(() -> confirm(firstId, 200));
            awaitCondition(() -> requests.findById(firstId).orElseThrow().getStatus() == ServiceRequestStatus.RUNNING);
            assertThat(confirm(secondId, 409).path("error_code").asText()).isEqualTo("CONCURRENT_IMPORT");
            assertThat(requests.findById(secondId).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertThat(first.get(10, TimeUnit.SECONDS).path("result").path("created").asInt()).isEqualTo(1);
            assertThat(confirm(secondId, 200).path("result").path("failed").asInt()).isEqualTo(1);
            assertThat(requests.findById(secondId).orElseThrow().getErrorRows()).singleElement()
                    .satisfies(error -> assertThat(error).containsEntry("code", "EDGE_CYCLE"));
            assertThat(point("TV1-901").getPrereqIds()).isEmpty();
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void race(ValidatedRow firstRow, ValidatedRow secondRow, String outcome) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                RowAction result = writer.write(firstRow); written.countDown(); await(release); return result;
            }));
            await(written);
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> writer.write(secondRow)));
            awaitCondition(() -> waitingGraphLocks() == 1);
            assertThat(second.isDone()).isFalse();
            if (!firstRow.values().get("to_kid").equals(secondRow.values().get("to_kid"))) {
                Long id = new TransactionTemplate(manager).execute(status -> jdbc.queryForObject(
                        "SELECT id FROM learning.knowledge_points WHERE code=? FOR UPDATE NOWAIT", Long.class,
                        secondRow.values().get("to_kid")));
                assertThat(id).isEqualTo(point(secondRow.values().get("to_kid")).getId());
            }
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.CREATE);
            if (outcome.equals("CREATE") || outcome.equals("UNCHANGED")) {
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.valueOf(outcome));
            } else {
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RowWriteRejectedException.class)
                        .satisfies(error -> assertThat(((RowWriteRejectedException) error.getCause()).code()).isEqualTo(outcome));
            }
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long waitingGraphLocks() {
        long hash = jdbc.queryForObject("SELECT hashtextextended(?, 0)", Long.class, LOCK_KEY);
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted AND objsubid=1
                    AND classid::bigint=? AND objid::bigint=?
                """, Long.class, hash >>> 32, hash & 0xffffffffL);
    }
    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Expected PostgreSQL lock or import state within 10 seconds");
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private KnowledgePoint point(String kid) { return points.findByCode(kid).orElseThrow(); }
    private static ValidatedRow row(String from, String to, String type) {
        return new ValidatedRow(2, Map.of("from_kid", from, "to_kid", to, "edge_type", type), null, null);
    }
    private long upload(ValidatedRow row) throws Exception {
        String csv = "from_kid,to_kid,edge_type\n" + row.values().get("from_kid") + "," + row.values().get("to_kid")
                + "," + row.values().get("edge_type") + "\n";
        return mapper.readTree(mvc.perform(multipart("/api/admin/imports").file(new MockMultipartFile("file", "synthetic.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8))).param("dataType", "KNOWLEDGE_EDGE")
                .header("Authorization", token())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .path("runId").asLong();
    }
    private JsonNode confirm(long id, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/admin/imports/" + id + "/confirm").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"acknowledgeRerun\":true}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    private String token() { return "Bearer " + jwt.issueAccessToken(user.getId(), List.of("CONTENT_EDITOR"), true, UUID.randomUUID()); }
}
