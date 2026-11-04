package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.NodeLinkRowValidator;
import com.cnhsk.learning.content.importer.write.NodeLinkRowWriter;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.NodeLinkRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.knowledge.KnowledgeTreeService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(NodeLinkImportIntegrationTest.SqlConfiguration.class)
class NodeLinkImportIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/imports";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private TopicItemRepository items;
    @Autowired private KnowledgePointRepository points;
    @Autowired private KnowledgeTreeService tree;
    @Autowired private NodeLinkRepository targets;
    @Autowired private NodeLinkRowValidator validator;
    @Autowired private CsvReader reader;
    @Autowired private ImportChangePlanner planner;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private NodeLinkRowWriter writer;
    private NodeLinkRowWriter writerTarget;
    private User user;
    private Topic topic;
    private Lexeme word;
    private Set<Long> topicBaseline;
    private Set<Long> lexemeBaseline;
    private Set<Long> itemBaseline;
    private Set<Long> pointBaseline;

    @BeforeEach
    void setup() {
        writerTarget = AopTestUtils.getUltimateTargetObject(writer);
        topicBaseline = topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet());
        lexemeBaseline = lexemes.findAll().stream().map(Lexeme::getId).collect(Collectors.toSet());
        itemBaseline = items.findAll().stream().map(TopicItem::getId).collect(Collectors.toSet());
        pointBaseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        user = users.saveAndFlush(new User("node-link-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người nhập liên kết"));
        for (String kid : List.of("TV1-801", "TV1-802", "CH1-002", "NP2-803")) {
            points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.valueOf(kid.substring(0, 2)),
                    "示例", (short) Character.digit(kid.charAt(2), 10)));
        }
        topic = topics.saveAndFlush(new Topic("T09_TOPIC_A", "Chủ đề tự soạn A", (short) 1));
        word = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "水杯", "Cốc nước tự soạn"));
        lexemes.saveAndFlush(new Lexeme(LexemeKind.CHAR, "木", "Cây tự soạn"));
        lexemes.saveAndFlush(new Lexeme(LexemeKind.GRAMMAR, "是……的", "Cấu trúc tự soạn"));
    }

    @AfterEach
    void cleanup() {
        capture.stop(); reset(writerTarget);
        jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id = ?)", user.getId());
        jdbc.update("DELETE FROM learning.attempts WHERE user_id = ?", user.getId());
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(run -> user.getId().equals(run.getUserId())).toList());
        items.deleteAllByIdInBatch(items.findAll().stream().map(TopicItem::getId).filter(id -> !itemBaseline.contains(id)).toList());
        topics.deleteAllByIdInBatch(topics.findAll().stream().map(Topic::getId).filter(id -> !topicBaseline.contains(id)).toList());
        lexemes.deleteAllByIdInBatch(lexemes.findAll().stream().map(Lexeme::getId).filter(id -> !lexemeBaseline.contains(id)).toList());
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId).filter(id -> !pointBaseline.contains(id)).toList());
        users.deleteById(user.getId());
    }

    @Test
    void FR013_FR014_AT13_AT14_AT24_previewDoesNotWriteAndConfirmLinksAllFourTargetTypes() throws Exception {
        JsonNode before = contentSnapshot();
        assertThat(tree.kidOfTopic(topic.getId())).isEmpty(); assertThat(tree.kidOfLexeme(word.getId())).isEmpty();
        var preview = upload(fixture("node-links-valid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(4);
        assertThat(preview.path("totals").path("create").asInt()).isZero();
        assertThat(preview.path("totals").path("update").asInt()).isEqualTo(4);
        assertThat(preview.path("preview").get(0).path("values").path("kid").asText()).isEqualTo("TV1-801");
        assertThat(contentSnapshot()).isEqualTo(before);
        assertResult(confirm(preview), 4, 0, 0);
        assertThat(tree.kidOfTopic(topic.getId())).contains("TV1-801");
        for (var entry : List.of(List.of("WORD", "水杯", "TV1-801"), List.of("CHAR", "木", "CH1-002"), List.of("GRAMMAR", "是……的", "NP2-803"))) {
            var lexeme = lexemes.findByKindAndText(LexemeKind.valueOf(entry.get(0)), entry.get(1)).orElseThrow();
            assertThat(tree.kidOfLexeme(lexeme.getId())).contains(entry.get(2));
        }
        assertThat(topics.count()).isEqualTo(topicBaseline.size() + 1);
        assertThat(lexemes.count()).isEqualTo(lexemeBaseline.size() + 3);
        assertThat(mapper.<JsonNode>valueToTree(topics.findById(topic.getId()).orElseThrow())).isEqualTo(before.path("topics").get(0));
        assertThat(mapper.<JsonNode>valueToTree(lexemes.findById(word.getId()).orElseThrow())).isEqualTo(before.path("words").get(0));
    }

    @Test
    void FR013_AT13_invalidFixturePreservesSpecificCodesAndPhysicalLines() throws Exception {
        var preview = upload(fixture("node-links-invalid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
        assertThat(preview.path("totals").path("invalid").asInt()).isEqualTo(6);
        assertThat(codes(preview.path("errors"))).containsExactly("DOMAIN_MISMATCH", "REFERENCE_NOT_FOUND", "DUPLICATE_IN_FILE",
                "REFERENCE_NOT_FOUND", "INVALID_VALUE", "REQUIRED_MISSING");
        List<Integer> lines = new ArrayList<>(); preview.path("errors").forEach(error -> lines.add(error.path("line").asInt()));
        assertThat(lines).containsExactly(2, 3, 5, 6, 7, 8);
        assertResult(confirm(preview), 1, 0, 0);
        assertThat(tree.kidOfLexeme(lexemes.findByKindAndText(LexemeKind.CHAR, "木").orElseThrow().getId())).contains("CH1-002");
        assertThat(tree.kidOfTopic(topic.getId())).isEmpty();
    }

    @Test
    void FR014_AT14_sameLinksAreUnchangedAndDifferentLinksAreOverwritten() throws Exception {
        confirm(upload(fixture("node-links-valid.csv")));
        var repeated = upload(fixture("node-links-valid.csv"));
        assertThat(repeated.path("totals").path("unchanged").asInt()).isEqualTo(4);
        capture.start();
        var result = confirm(repeated);
        var sql = capture.statements(); capture.stop();
        assertResult(result, 0, 4, 0);
        assertThat(sql).filteredOn(value -> value.startsWith("select") && value.contains("for update")
                && (value.contains("from learning.topics") || value.contains("from learning.lexemes"))).hasSize(4);
        assertThat(sql).noneMatch(value -> value.startsWith("update learning.topics") || value.startsWith("update learning.lexemes"));
        var changed = upload(csv("TOPIC,T09_TOPIC_A,TV1-802\nWORD,水杯,TV1-802\n"));
        assertThat(changed.path("totals").path("update").asInt()).isEqualTo(2);
        assertResult(confirm(changed), 2, 0, 0);
        assertThat(tree.kidOfTopic(topic.getId())).contains("TV1-802");
        assertThat(tree.kidOfLexeme(word.getId())).contains("TV1-802");
    }

    @Test
    void FR014_AT14_getTopicsReturnsTheEntireSameJsonBeforeAndAfterLinkingAndRelinking() throws Exception {
        items.saveAndFlush(new TopicItem(topic.getId(), word.getId()));
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id) VALUES (?,'PRACTICE','TOPIC_STUDY',?) RETURNING id",
                Long.class, user.getId(), topic.getId());
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,order_index) VALUES (?,?,'STUDY_CARD','VOCAB',1)", attempt, word.getId());
        JsonNode before = topicList();
        assertThat(before).isNotEmpty();
        JsonNode own = java.util.stream.StreamSupport.stream(before.spliterator(), false)
                .filter(value -> value.path("id").asLong() == topic.getId()).findFirst().orElseThrow();
        assertThat(own.path("learnedWords").asInt()).isEqualTo(1);
        assertThat(own.path("percent").asInt()).isEqualTo(100);
        confirm(upload(fixture("node-links-valid.csv")));
        assertThat(topicList()).isEqualTo(before);
        confirm(upload(csv("TOPIC,T09_TOPIC_A,TV1-802\nWORD,水杯,TV1-802\n")));
        assertThat(topicList()).isEqualTo(before);
        assertThat(tree.kidOfTopic(topic.getId())).contains("TV1-802");
        assertThat(tree.kidOfLexeme(word.getId())).contains("TV1-802");
    }

    @Test
    void FR013_uniqueKindAndTextConstraintAllowsTheSameTextInDifferentKindsWithoutAmbiguity() throws Exception {
        String constraint = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='learning.lexemes'::regclass AND conname='uq_lexeme'", String.class);
        assertThat(constraint).isEqualTo("UNIQUE (kind, text)");
        Lexeme otherKind = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "木", "Từ cùng chữ tự soạn"));
        assertThatThrownBy(() -> lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "木", "Trùng cùng kind")))
                .isInstanceOf(DataIntegrityViolationException.class);
        confirm(upload(csv("WORD,木,TV1-801\nCHAR,木,CH1-002\n")));
        assertThat(tree.kidOfLexeme(otherKind.getId())).contains("TV1-801");
        assertThat(tree.kidOfLexeme(lexemes.findByKindAndText(LexemeKind.CHAR, "木").orElseThrow().getId())).contains("CH1-002");
        var missingKind = upload(csv("GRAMMAR,木,NP2-803\n"));
        assertThat(codes(missingKind.path("errors"))).containsExactly("REFERENCE_NOT_FOUND");
    }

    @Test
    void FR014_eachNativeUpdateRollsBackWithItsRowAndLaterRowsStillCommit() throws Exception {
        doAnswer(call -> {
            Object result = call.callRealMethod();
            var row = (com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow) call.getArgument(0);
            if (row.values().get("target_type").equals("TOPIC")) throw new IllegalStateException("synthetic private SQL detail");
            return result;
        }).when(writerTarget).write(any());
        var preview = upload(fixture("node-links-valid.csv"));
        var result = confirm(preview);
        assertResult(result, 3, 0, 1);
        assertThat(tree.kidOfTopic(topic.getId())).isEmpty();
        assertThat(tree.kidOfLexeme(word.getId())).contains("TV1-801");
        assertThat(codes(errors(preview).path("items"))).containsExactly("ROW_WRITE_FAILED");
        assertThat(errors(preview).toString()).doesNotContain("synthetic", "SQL", "IllegalStateException");
    }

    @Test
    void FR014_targetRemovedAfterPreviewProducesReferenceNotFoundAndDoesNotRecreateIt() throws Exception {
        var preview = upload(fixture("node-links-valid.csv"));
        topics.deleteById(topic.getId());
        assertResult(confirm(preview), 3, 0, 1);
        assertThat(topics.findByCode("T09_TOPIC_A")).isEmpty();
        assertThat(codes(errors(preview).path("items"))).containsExactly("REFERENCE_NOT_FOUND");
    }

    @Test
    void FR014_nodeRemovedAfterPreviewProducesReferenceNotFoundBeforeAnyUpdate() throws Exception {
        var preview = upload(csv("TOPIC,T09_TOPIC_A,TV1-801\nCHAR,木,CH1-002\n"));
        points.deleteById(points.findByCode("TV1-801").orElseThrow().getId());
        assertResult(confirm(preview), 1, 0, 1);
        assertThat(tree.kidOfTopic(topic.getId())).isEmpty();
        assertThat(codes(errors(preview).path("items"))).containsExactly("REFERENCE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC,T09_TOPIC_A,", "WORD,水杯,"})
    void FR013_FR014_blankKidCannotUnlinkContent(String line) throws Exception {
        confirm(upload(fixture("node-links-valid.csv")));
        var preview = upload(csv(line + "\n"));
        assertThat(codes(preview.path("errors"))).containsExactly("REQUIRED_MISSING");
        assertThat(tree.kidOfTopic(topic.getId())).contains("TV1-801");
        assertThat(tree.kidOfLexeme(word.getId())).contains("TV1-801");
    }

    @Test
    void FR013_nativeLookupsUseLiteralParametersAndEmptySetsExecuteNoSql() {
        assertThat(targets.topics(List.of("T09_TOPIC_A' OR '1'='1"))).isEmpty();
        assertThat(targets.lexemes(LexemeKind.WORD, List.of("水杯' OR '1'='1"))).isEmpty();
        capture.start();
        assertThat(targets.topics(List.of())).isEmpty();
        for (LexemeKind kind : LexemeKind.values()) assertThat(targets.lexemes(kind, List.of())).isEmpty();
        assertThat(capture.statements()).isEmpty(); capture.stop();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1201, 5000})
    void FR013_FR014_batchValidationAndPlanningHaveBoundedRealSelectCountsAndNoContentDml(int size) {
        var parsed = reader.read(ImportDataType.NODE_LINK, "synthetic.csv", batchCsv(size));
        capture.start();
        var checked = validator.validate(parsed);
        assertThat(checked.validRows()).hasSize(size); assertThat(checked.errors()).isEmpty();
        int batches = (size + 499) / 500;
        assertThat(capture.statements()).hasSize(size == 1 ? 2 : 5 * batches)
                .allSatisfy(sql -> assertThat(sql).startsWith("select").doesNotContain("for update"));
        capture.start();
        assertThat(planner.plan(ImportDataType.NODE_LINK, checked.validRows())).hasSize(size)
                .allSatisfy(row -> assertThat(row.action()).isEqualTo(RowAction.UPDATE));
        assertThat(capture.statements()).hasSize(size == 1 ? 1 : 4 * batches)
                .allSatisfy(sql -> assertThat(sql).startsWith("select").doesNotContain("for update"));
        capture.stop();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.topics WHERE knowledge_point_id IS NOT NULL AND code LIKE 'T09_BATCH_%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.lexemes WHERE knowledge_point_id IS NOT NULL", Long.class)).isZero();
    }

    private byte[] batchCsv(int size) {
        List<Integer> indices = IntStream.range(0, size).boxed().toList();
        jdbc.batchUpdate("INSERT INTO learning.topics(code,name,hsk_level) VALUES (?, 'Chủ đề tự soạn', 1)",
                indices.stream().filter(index -> index % 4 == 0).toList(), 500, (statement, index) -> statement.setString(1, batchKey(index)));
        for (String type : List.of("WORD", "CHAR", "GRAMMAR")) {
            jdbc.batchUpdate("INSERT INTO learning.lexemes(kind,text,meaning_vi,hsk_level) VALUES (?,?,'Nội dung tự soạn',1)",
                    indices.stream().filter(index -> batchType(index).equals(type)).toList(), 500,
                    (statement, index) -> { statement.setString(1, type); statement.setString(2, batchKey(index)); });
        }
        StringBuilder rows = new StringBuilder();
        indices.forEach(index -> rows.append(batchType(index)).append(',').append(batchKey(index)).append(',')
                .append(index % 4 == 2 ? "CH1-002" : index % 4 == 3 ? "NP2-803" : "TV1-801").append('\n'));
        return csv(rows.toString());
    }

    private static String batchType(int index) { return List.of("TOPIC", "WORD", "CHAR", "GRAMMAR").get(index % 4); }
    private static String batchKey(int index) {
        String character = Character.toString(0x4E00 + index);
        return switch (index % 4) { case 0 -> "T09_BATCH_" + index; case 1 -> "词" + character; case 2 -> character; default -> "是" + character + "的"; };
    }
    private JsonNode contentSnapshot() {
        return mapper.valueToTree(java.util.Map.of("topics", List.of(topics.findById(topic.getId()).orElseThrow()),
                "words", List.of(lexemes.findById(word.getId()).orElseThrow()),
                "links", jdbc.queryForList("SELECT code,knowledge_point_id FROM learning.topics ORDER BY id"),
                "lexemeLinks", jdbc.queryForList("SELECT kind,text,knowledge_point_id FROM learning.lexemes ORDER BY id")));
    }
    private JsonNode topicList() throws Exception {
        return mapper.readTree(mvc.perform(get("/api/topics").header("Authorization", token("LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode upload(byte[] csv) throws Exception {
        return mapper.readTree(mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "synthetic.csv", "text/csv", csv))
                        .param("dataType", "NODE_LINK").header("Authorization", token("CONTENT_EDITOR")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode confirm(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(post(ROOT + "/" + preview.path("runId").asLong() + "/confirm")
                        .header("Authorization", token("CONTENT_EDITOR")).contentType(MediaType.APPLICATION_JSON).content("{\"acknowledgeRerun\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode errors(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(get(ROOT + "/" + preview.path("runId").asLong() + "/errors").header("Authorization", token("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private String token(String role) { return "Bearer " + jwt.issueAccessToken(user.getId(), List.of(role), true, UUID.randomUUID()); }
    private static byte[] csv(String rows) { return ("target_type,target_key,kid\n" + rows).getBytes(StandardCharsets.UTF_8); }
    private static byte[] fixture(String name) throws Exception { return new ClassPathResource("import/knowledge/" + name).getContentAsByteArray(); }
    private static List<String> codes(JsonNode errors) { List<String> result = new ArrayList<>(); errors.forEach(error -> result.add(error.path("code").asText())); return result; }
    private static void assertResult(JsonNode result, int updated, int unchanged, int failed) {
        assertThat(result.path("result").path("created").asInt()).isZero();
        assertThat(result.path("result").path("updated").asInt()).isEqualTo(updated);
        assertThat(result.path("result").path("unchanged").asInt()).isEqualTo(unchanged);
        assertThat(result.path("result").path("failed").asInt()).isEqualTo(failed);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture nodeLinkSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer nodeLinkStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override public String inspect(String sql) { if (active.get() != null) active.get().add(sql.toLowerCase(Locale.ROOT).strip()); return sql; }
    }
}
