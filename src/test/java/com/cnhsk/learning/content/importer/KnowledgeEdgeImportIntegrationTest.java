package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.KnowledgeEdgeRowValidator;
import com.cnhsk.learning.content.importer.write.KnowledgeEdgeRowWriter;
import com.cnhsk.learning.content.importer.write.RowWriteRejectedException;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(KnowledgeEdgeImportIntegrationTest.SqlConfiguration.class)
class KnowledgeEdgeImportIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/imports";
    private static final String HEADER = "from_kid,to_kid,edge_type\n";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final String EDGES = """
            SELECT p.id AS target, unnest(p.prereq_ids) AS source FROM learning.knowledge_points p
            """;
    private static final List<String> INVARIANTS = List.of(
            """
            WITH RECURSIVE edges AS (%s), paths AS (
                SELECT source, target, ARRAY[source,target] AS visited, source=target AS cycle FROM edges
                UNION ALL
                SELECT p.source, e.target, p.visited || e.target, e.target=ANY(p.visited)
                FROM paths p JOIN edges e ON e.source=p.target WHERE NOT p.cycle
            ) SELECT source, target FROM paths WHERE cycle
            """.formatted(EDGES),
            """
            SELECT p.code FROM learning.knowledge_points p
            JOIN learning.knowledge_points source ON source.id=ANY(p.prereq_ids)
            WHERE source.hsk_level>p.hsk_level
            """,
            """
            WITH RECURSIVE edges AS (%s), paths AS (
                SELECT source, target, 1 AS depth, ARRAY[source,target] AS visited FROM edges
                UNION ALL
                SELECT p.source, e.target, p.depth+1, p.visited || e.target
                FROM paths p JOIN edges e ON e.source=p.target WHERE NOT e.target=ANY(p.visited)
            ) SELECT DISTINCT e.source,e.target FROM edges e JOIN paths p
                ON p.source=e.source AND p.target=e.target WHERE p.depth>=2
            """.formatted(EDGES),
            "SELECT code FROM learning.knowledge_points WHERE cardinality(prereq_ids)>4");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private KnowledgePointRepository points;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CsvReader reader;
    @Autowired private KnowledgeEdgeRowValidator validator;
    @Autowired private ImportChangePlanner planner;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private KnowledgeEdgeRowWriter writer;
    private KnowledgeEdgeRowWriter writerTarget;
    private User user;
    private Set<Long> baseline;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(new User("edge-import-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người nhập cạnh"));
        baseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        writerTarget = AopTestUtils.getUltimateTargetObject(writer);
    }

    @AfterEach
    void cleanup() {
        reset(writerTarget);
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(run -> user.getId().equals(run.getUserId())).toList());
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId).filter(id -> !baseline.contains(id)).toList());
        users.deleteById(user.getId());
    }

    @Test
    void AT05_AT06_invalidFixtureKeepsSpecificErrorsAndDoesNotWriteDuringPreview() throws Exception {
        smallTree();
        node("TV2-902"); node("TV3-903");
        var before = snapshot();
        JsonNode preview = upload("KNOWLEDGE_EDGE", fixture("edges-invalid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
        assertThat(preview.path("totals").path("invalid").asInt()).isEqualTo(6);
        assertThat(errorCodes(preview.path("errors"))).containsExactly("REFERENCE_NOT_FOUND", "EDGE_SELF",
                "PREREQ_LEVEL_HIGHER", "EDGE_CYCLE", "DUPLICATE_IN_FILE", "INVALID_VALUE");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(confirm(preview).path("result").path("created").asInt()).isEqualTo(1);
        assertThat(point("TV1-802").getPrereqIds()).containsExactly(point("TV1-801").getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_CYCLE", "FILE_CYCLE", "FOUR", "REDUNDANT_DB", "REDUNDANT_FILE"})
    void AT06_checksDatabaseIntermediateNodesAndEarlierValidFileRows(String scenario) throws Exception {
        smallTree();
        String rows;
        String code;
        int valid = 0;
        switch (scenario) {
            case "DB_CYCLE" -> {
                edge("TV1-802", "TV1-801", "PREREQ");
                rows = "TV1-801,TV1-802,PREREQ\n"; code = "EDGE_CYCLE";
            }
            case "FILE_CYCLE" -> {
                rows = "TV1-801,TV1-802,PREREQ\nTV1-802,TV1-801,PREREQ\n"; code = "EDGE_CYCLE"; valid = 1;
            }
            case "FOUR" -> {
                for (int id = 803; id <= 806; id++) edge("TV1-" + id, "TV1-802", "PREREQ");
                rows = "TV1-801,TV1-802,PREREQ\n"; code = "TOO_MANY_PREREQS";
            }
            case "REDUNDANT_DB" -> {
                edge("TV1-801", "TV1-803", "PREREQ"); edge("TV1-803", "TV1-802", "PREREQ");
                rows = "TV1-801,TV1-802,PREREQ\n"; code = "EDGE_REDUNDANT";
            }
            default -> {
                rows = "TV1-801,TV1-803,PREREQ\nTV1-803,TV1-802,PREREQ\nTV1-801,TV1-802,PREREQ\n";
                code = "EDGE_REDUNDANT"; valid = 2;
            }
        }
        var before = snapshot();
        JsonNode preview = upload("KNOWLEDGE_EDGE", bytes(HEADER + rows));
        assertThat(errorCodes(preview.path("errors"))).containsExactly(code);
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(valid);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void AT07_changedGraphRejectsOneRowAtConfirmAndContinuesWithTheNext() throws Exception {
        smallTree();
        JsonNode preview = upload("KNOWLEDGE_EDGE", bytes(HEADER + "TV1-801,TV1-802,PREREQ\nTV1-803,TV1-804,PREREQ\n"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(2);
        edge("TV1-802", "TV1-801", "PREREQ");
        JsonNode completed = confirm(preview);
        assertThat(completed.path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(completed.path("result").path("created").asInt()).isEqualTo(1);
        assertThat(point("TV1-802").getPrereqIds()).isEmpty();
        assertThat(point("TV1-804").getPrereqIds()).containsExactly(point("TV1-803").getId());
        JsonNode errors = errors(preview);
        assertThat(errors.path("items")).hasSize(1);
        assertThat(errors.path("items").get(0).path("line").asInt()).isEqualTo(2);
        assertThat(errors.path("items").get(0).path("column").asText()).isEqualTo("to_kid");
        assertThat(errors.path("items").get(0).path("code").asText()).isEqualTo("EDGE_CYCLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOO_MANY_PREREQS", "EDGE_REDUNDANT", "EDGE_TYPE_CONFLICT"})
    void AT07_rechecksEveryMutableConstraintAfterPreview(String expected) throws Exception {
        smallTree();
        JsonNode preview = upload("KNOWLEDGE_EDGE", bytes(HEADER + "TV1-801,TV1-802,PREREQ\n"));
        if (expected.equals("TOO_MANY_PREREQS")) {
            for (int id = 803; id <= 806; id++) edge("TV1-" + id, "TV1-802", "PREREQ");
        } else if (expected.equals("EDGE_REDUNDANT")) {
            edge("TV1-801", "TV1-803", "PREREQ"); edge("TV1-803", "TV1-802", "PREREQ");
        } else edge("TV1-801", "TV1-802", "SUPPORT");
        var before = snapshot();
        assertThat(confirm(preview).path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(errorCodes(errors(preview).path("items"))).containsExactly(expected);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void AT08_supportAndReimportKeepArraysAndTimestampsUnchangedWhenNothingChanges() throws Exception {
        smallTree();
        JsonNode first = upload("KNOWLEDGE_EDGE", fixture("edges-valid.csv"));
        assertThat(confirm(first).path("result").path("created").asInt()).isEqualTo(3);
        assertThat(point("TV1-803").getPrereqIds()).containsExactly(point("TV1-802").getId());
        assertThat(point("TV1-803").getSupportIds()).containsExactly(point("TV1-804").getId());
        var before = snapshot();
        JsonNode repeat = upload("KNOWLEDGE_EDGE", fixture("edges-valid.csv"));
        assertThat(repeat.path("totals").path("unchanged").asInt()).isEqualTo(3);
        assertThat(confirm(repeat).path("result").path("unchanged").asInt()).isEqualTo(3);
        assertThat(snapshot()).isEqualTo(before);
        JsonNode conflict = upload("KNOWLEDGE_EDGE", bytes(HEADER + "TV1-801,TV1-802,SUPPORT\n"));
        assertThat(errorCodes(conflict.path("errors"))).containsExactly("EDGE_TYPE_CONFLICT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TYPED", "TYPED_WITHOUT_FIELD", "UNEXPECTED"})
    void typedAndUnexpectedFailuresAfterSavingRollBackOnlyTheirRow(String failure) throws Exception {
        boolean typed = !failure.equals("UNEXPECTED");
        smallTree();
        JsonNode preview = upload("KNOWLEDGE_EDGE", bytes(HEADER + "TV1-801,TV1-802,PREREQ\nTV1-803,TV1-804,PREREQ\n"));
        doAnswer(call -> {
            var action = call.callRealMethod();
            var row = (com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow) call.getArgument(0);
            if (row.line() == 2) {
                if (typed) throw new RowWriteRejectedException("EDGE_CYCLE",
                        failure.equals("TYPED_WITHOUT_FIELD") ? null : "to_kid", "Quan hệ này tạo vòng lặp tiên quyết");
                throw new IllegalStateException("private SQL diagnostics must remain private");
            }
            return action;
        }).when(writerTarget).write(any());
        assertThat(confirm(preview).path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(point("TV1-802").getPrereqIds()).isEmpty();
        assertThat(point("TV1-804").getPrereqIds()).containsExactly(point("TV1-803").getId());
        JsonNode report = errors(preview);
        assertThat(errorCodes(report.path("items"))).containsExactly(typed ? "EDGE_CYCLE" : "ROW_WRITE_FAILED");
        assertThat(report.toString()).doesNotContain("private SQL", "IllegalStateException");
        if (failure.equals("TYPED_WITHOUT_FIELD")) {
            assertThat(report.path("items").get(0).path("column").isNull()).isTrue();
            assertThat(report.path("items").get(0).path("value").isNull()).isTrue();
        }
    }

    @Test
    void AT29_sixtySelfAuthoredNodesAndEightyEdgesPassFourIndependentPostgresQueries() throws Exception {
        JsonNode nodes = upload("KNOWLEDGE_NODE", fixture("tree-nodes.csv"));
        assertThat(nodes.path("totals").path("valid").asInt()).isEqualTo(60);
        assertThat(confirm(nodes).path("result").path("created").asInt()).isEqualTo(60);
        var before = snapshot();
        JsonNode edges = upload("KNOWLEDGE_EDGE", fixture("tree-edges.csv"));
        assertThat(edges.path("totals").path("valid").asInt()).isEqualTo(80);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(confirm(edges).path("result").path("created").asInt()).isEqualTo(80);
        for (int index = 0; index < INVARIANTS.size(); index++) {
            assertThat(jdbc.queryForList(INVARIANTS.get(index))).as("V0%d", index + 1).isEmpty();
        }
    }

    @Test
    void invariantQueriesActuallyDetectAllFourViolations() {
        smallTree();
        KnowledgePoint lower = node("TV2-902"); KnowledgePoint higher = node("TV3-903");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            setPrereqs(point("TV1-801"), point("TV1-801").getId());
            setPrereqs(lower, higher.getId());
            setPrereqs(point("TV1-803"), point("TV1-804").getId());
            setPrereqs(point("TV1-802"), point("TV1-804").getId(), point("TV1-803").getId());
            setPrereqs(point("TV1-806"), point("TV1-801").getId(), point("TV1-802").getId(),
                    point("TV1-803").getId(), point("TV1-804").getId(), point("TV1-805").getId());
            for (int index = 0; index < INVARIANTS.size(); index++) {
                assertThat(jdbc.queryForList(INVARIANTS.get(index))).as("V0%d negative control", index + 1).isNotEmpty();
            }
            status.setRollbackOnly();
        });
    }

    @Test
    void NFR_P03_twoThousandValidRowsUseOneGraphReadAndBatchedPlanningBelowTenSecondsP95() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int level = 1; level <= 3; level++) for (int id = 1; id <= 200; id++) node("TV%d-%03d".formatted(level, id));
        for (int level = 1; level <= 2; level++) for (int id = 1; id <= 200; id++) {
            for (int offset = 0; offset < 4; offset++) csv.append("TV%d-%03d,TV%d-%03d,PREREQ\n"
                    .formatted(level, (id + offset - 1) % 200 + 1, level + 1, id));
        }
        for (int id = 1; id <= 200; id++) for (int offset = 0; offset < 2; offset++) {
            csv.append("TV3-%03d,TV1-%03d,SUPPORT\n".formatted((id + offset - 1) % 200 + 1, id));
        }
        byte[] input = bytes(csv.toString());
        var parsed = reader.read(ImportDataType.KNOWLEDGE_EDGE, "synthetic.csv", input);
        capture.start();
        try {
            var validated = validator.validate(parsed);
            assertThat(validated.validRows()).hasSize(2000);
            assertThat(validated.errors()).isEmpty();
            assertThat(capture.statements()).hasSize(1).allSatisfy(sql -> assertThat(sql).startsWith("select"));
            capture.start();
            assertThat(planner.plan(ImportDataType.KNOWLEDGE_EDGE, validated.validRows())).hasSize(2000);
            assertThat(capture.statements()).hasSize(4).allSatisfy(sql -> assertThat(sql).startsWith("select"));
        } finally { capture.stop(); }
        upload("KNOWLEDGE_EDGE", input);
        long[] samples = new long[20];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            JsonNode preview = upload("KNOWLEDGE_EDGE", input);
            samples[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(2000);
            assertThat(preview.path("totals").path("create").asInt()).isEqualTo(2000);
        }
        long[] sorted = samples.clone(); Arrays.sort(sorted);
        long p95 = sorted[(int) Math.ceil(samples.length * .95) - 1];
        System.out.println("T07_EDGE_PREVIEW_BENCHMARK rows=2000 samples_ms=" + Arrays.toString(samples) + " p95_ms=" + p95);
        assertThat(p95).isLessThan(10_000);
        assertThat(points.findAll()).allSatisfy(point -> {
            assertThat(point.getPrereqIds()).isEmpty(); assertThat(point.getSupportIds()).isEmpty();
        });
    }

    private void setPrereqs(KnowledgePoint target, Long... ids) {
        jdbc.update("UPDATE learning.knowledge_points SET prereq_ids=? WHERE id=?", ids, target.getId());
    }
    private void smallTree() { for (int id = 801; id <= 806; id++) node("TV1-" + id); }
    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid,
                KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) (kid.charAt(2) - '0')));
    }
    private KnowledgePoint point(String kid) { return points.findByCode(kid).orElseThrow(); }
    private void edge(String from, String to, String type) {
        new TransactionTemplate(manager).executeWithoutResult(status -> writer.write(
                new com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow(2,
                        Map.of("from_kid", from, "to_kid", to, "edge_type", type), null, null)));
    }
    private JsonNode snapshot() {
        return mapper.valueToTree(points.findAll().stream()
                .sorted(java.util.Comparator.comparing(KnowledgePoint::getId)).toList());
    }
    private JsonNode upload(String type, byte[] csv) throws Exception {
        return mapper.readTree(mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "synthetic.csv", "text/csv", csv))
                .param("dataType", type).header("Authorization", token())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode confirm(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(post(ROOT + "/" + preview.path("runId").asLong() + "/confirm")
                .header("Authorization", token()).contentType(MediaType.APPLICATION_JSON).content("{\"acknowledgeRerun\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode errors(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(get(ROOT + "/" + preview.path("runId").asLong() + "/errors")
                .header("Authorization", token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private String token() { return "Bearer " + jwt.issueAccessToken(user.getId(), List.of("CONTENT_EDITOR"), true, UUID.randomUUID()); }
    private static byte[] bytes(String csv) { return csv.getBytes(StandardCharsets.UTF_8); }
    private static byte[] fixture(String name) throws Exception {
        return new ClassPathResource("import/knowledge/" + name).getContentAsByteArray();
    }
    private static List<String> errorCodes(JsonNode errors) {
        List<String> codes = new ArrayList<>(); errors.forEach(error -> codes.add(error.path("code").asText())); return codes;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture edgeSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer edgeStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override public String inspect(String sql) {
            if (active.get() != null) active.get().add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }
}
