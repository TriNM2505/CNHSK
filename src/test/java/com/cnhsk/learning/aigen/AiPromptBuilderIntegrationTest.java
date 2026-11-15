package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AiPromptBuilderIntegrationTest.SqlConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AiPromptBuilderIntegrationTest extends AbstractIntegrationTest {
    @Autowired AiPromptBuilder builder;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StubClaudeClient stub;
    @Autowired SqlCapture sql;
    Fixtures fixtures;
    int calls;

    @BeforeEach
    void prepare() { fixtures = new Fixtures(jdbc); calls = stub.callCount(); }

    @AfterEach
    void cleanup() { fixtures.close(); assertThat(stub.callCount()).isEqualTo(calls); }

    @ParameterizedTest
    @ValueSource(ints = {1, 80, 81})
    void selectsOnlyTargetLexemesInIdOrderWithAnEightyItemLimit(int count) throws Exception {
        long node = fixtures.node("Current target", "Can do target"), other = fixtures.node("Other", "Other can do");
        fixtures.lexeme(other, "WORD", "unrelated-lexeme", 1);
        for (int index = 0; index < count; index++) fixtures.lexeme(node, "WORD", "target-" + index, 1);
        sql.clear();
        JsonNode context = context(builder.build(node, 5));
        assertThat(context.path("lexemes").size()).isEqualTo(Math.min(80, count));
        for (int index = 0; index < Math.min(80, count); index++)
            assertThat(context.path("lexemes").get(index).path("text").asText()).isEqualTo("target-" + index);
        assertThat(context.toString()).doesNotContain("unrelated-lexeme", "target-80");
        sql.assertContentReads(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4})
    void choosesAtMostThreeApprovedPrimarySamplesWithoutLosingAnyRoleErrorPairs(int count) throws Exception {
        long node = fixtures.node("Target", "Can do"), other = fixtures.node("Other", "Can do");
        fixtures.lexeme(node, "WORD", "target-only", 1);
        String error = fixtures.kid(other);
        for (int index = 0; index < count; index++)
            fixtures.question(node, "PRIMARY", "APPROVED", "IMPORT", "sample-" + index, fixtureOptions(error));
        fixtures.question(node, "SECONDARY", "APPROVED", "MANUAL", "secondary-not-sample", fixtureOptions(error));
        fixtures.question(node, "PRIMARY", "PENDING_REVIEW", "AI", "pending-not-sample", fixtureOptions(error));
        fixtures.question(other, "PRIMARY", "APPROVED", "IMPORT", "unrelated-not-sample", fixtureOptions(null));
        sql.clear();
        var first = builder.build(node, 5);
        JsonNode context = context(first);
        assertThat(context.path("samples").size()).isEqualTo(Math.min(3, count));
        for (int index = 0; index < Math.min(3, count); index++)
            assertThat(context.path("samples").get(index).path("stem").asText()).isEqualTo("sample-" + index);
        assertThat(context.path("error_patterns").size()).isEqualTo(1);
        assertThat(context.path("error_patterns").get(0).path("error_kid").asText()).isEqualTo(error);
        assertThat(context.toString()).doesNotContain("secondary-not-sample", "pending-not-sample", "unrelated-not-sample", "frequency");
        sql.assertContentReads(3);
        assertThat(builder.build(node, 5)).isEqualTo(first);
    }

    @Test
    void skipsOnlyMalformedLegacyQuestionAndLogsOnlyItsIdentifier(CapturedOutput output) throws Exception {
        long node = fixtures.node("Target", "Can do"), other = fixtures.node("Other", "Can do");
        fixtures.lexeme(node, "WORD", "legacy-case", 1);
        String secret = "legacy-private-" + UUID.randomUUID();
        long malformed = fixtures.question(node, "PRIMARY", "APPROVED", "MANUAL", secret,
                "[{\"key\":\"A\",\"text\":\"" + secret + "\",\"is_correct\":\"true\"},null]");
        for (int index = 0; index < 4; index++)
            fixtures.question(node, "PRIMARY", "APPROVED", "IMPORT", "kept-" + index, fixtureOptions(fixtures.kid(other)));
        sql.clear();
        JsonNode context = context(builder.build(node, 5));
        assertThat(context.path("samples")).hasSize(3);
        for (int index = 0; index < 3; index++) assertThat(context.path("samples").get(index).path("stem").asText()).isEqualTo("kept-" + index);
        assertThat(context.toString()).doesNotContain(secret);
        assertThat(output.getAll()).contains("WARN", Long.toString(malformed)).doesNotContain(secret, "kept-0", "legacy-case");
        sql.assertContentReads(3);
    }

    @Test
    void preservesApprovedContentAndPrerequisiteTitlesWithoutReadingLearnerData(CapturedOutput output) throws Exception {
        long node = fixtures.node("Allowed title", "Allowed can do"), prerequisite = fixtures.node("Allowed prerequisite", "Prerequisite");
        jdbc.update("UPDATE learning.knowledge_points SET prereq_ids=ARRAY[?]::bigint[] WHERE id=?", prerequisite, node);
        fixtures.lexeme(node, "WORD", "allowed-lexeme", 1);
        var learners = fixtures.learners;
        long user = learners.user();
        String canary = "private-learner-" + UUID.randomUUID();
        jdbc.update("UPDATE auth.users SET display_name=?,email=? WHERE id=?", canary, canary + "@example.test", user);
        long question = fixtures.question(node, "PRIMARY", "APPROVED", "IMPORT", "Allowed sample", fixtureOptions(null));
        learners.items(user, question, canary, false, Instant.parse("2026-11-15T06:00:00Z"), 7);
        learners.run(user, "AI_GENERATE", "PENDING", "{\"private\":\"" + canary + "\",\"wrongCount\":7654321}", Instant.now());
        List<Long> before = fixtures.counts();
        sql.clear();
        var prompt = builder.build(node, 5);
        JsonNode context = context(prompt);
        assertThat(context.path("title").asText()).isEqualTo("Allowed title");
        assertThat(context.path("name_zh").asText()).isEqualTo("示例");
        assertThat(context.path("can_do").asText()).isEqualTo("Allowed can do");
        assertThat(context.path("prerequisite_titles").get(0).asText()).isEqualTo("Allowed prerequisite");
        assertThat(context.path("lexemes").get(0).path("text").asText()).isEqualTo("allowed-lexeme");
        String serialized = mapper.writeValueAsString(prompt);
        assertThat(serialized).doesNotContain(canary, "7654321", "wrongCount", "bankCount", "email", "userId");
        assertThat(output.getAll()).doesNotContain(canary, "Allowed sample", "allowed-lexeme");
        sql.assertContentReads(3);
        assertThat(fixtures.counts()).isEqualTo(before);
    }

    @Test
    void refusesMissingNodeBlankCanDoAndZeroLexemes() {
        long blank = fixtures.node("Blank", " \u2003 "), noLexeme = fixtures.node("No lexeme", "Can do");
        assertThatThrownBy(() -> builder.build(Long.MAX_VALUE, 5)).hasMessage("MISSING_NODE");
        assertThatThrownBy(() -> builder.build(blank, 5)).hasMessage("MISSING_CAN_DO");
        assertThatThrownBy(() -> builder.build(noLexeme, 5)).hasMessage("NO_LEXEMES");
    }

    private JsonNode context(AiPromptBuilder.Prompt prompt) throws Exception { return mapper.readTree(prompt.systemBlocks().get(1).text()); }

    static String fixtureOptions(String errorKid) {
        return "[{\"key\":\"A\",\"text\":\"first\",\"is_correct\":true},"
                + "{\"key\":\"B\",\"text\":\"second\",\"is_correct\":false"
                + (errorKid == null ? "" : ",\"error_kid\":\"" + errorKid + "\",\"error_note\":\"Self-authored note\"")
                + "},{\"key\":\"C\",\"text\":\"third\",\"is_correct\":false},{\"key\":\"D\",\"text\":\"fourth\",\"is_correct\":false}]";
    }

    static class Fixtures implements AutoCloseable {
        private static final AtomicInteger CODES = new AtomicInteger(200);
        final JdbcTemplate jdbc;
        final WeakKnowledgeSelectorIntegrationTest.Fixtures learners;
        final List<Long> nodes = new ArrayList<>(), lexemes = new ArrayList<>(), questions = new ArrayList<>();
        Fixtures(JdbcTemplate jdbc) { this.jdbc = jdbc; learners = new WeakKnowledgeSelectorIntegrationTest.Fixtures(jdbc); }
        long node(String title, String canDo) {
            long id = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level,can_do) "
                    + "VALUES (?,?,'VB','示例',1,?) RETURNING id", Long.class,
                    "VB1-" + String.format("%03d", CODES.incrementAndGet()), title, canDo);
            nodes.add(id); return id;
        }
        String kid(long node) { return jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, node); }
        long lexeme(Long node, String kind, String text, Integer hsk) {
            long id = jdbc.queryForObject("INSERT INTO learning.lexemes(knowledge_point_id,kind,text,pinyin,meaning_vi,hsk_level) "
                    + "VALUES (?,?,?,'fixture','nghĩa tự soạn',?) RETURNING id", Long.class, node, kind, text, hsk);
            lexemes.add(id); return id;
        }
        long question(long node, String role, String status, String source, String stem, String options) {
            long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,explanation,source,status,review_reason) "
                    + "VALUES (?,'MULTIPLE_CHOICE',1,?,?::jsonb,'A','Self-authored explanation',?,?,?) RETURNING id",
                    Long.class, "GEN-" + UUID.randomUUID(), stem, options, source, status, status.equals("REJECTED") ? "Fixture rejection" : null);
            questions.add(id);
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,?)", id, node, role);
            return id;
        }
        List<Long> counts() {
            return List.of(jdbc.queryForObject("SELECT count(*) FROM learning.questions", Long.class),
                    jdbc.queryForObject("SELECT count(*) FROM learning.question_knowledge_points", Long.class),
                    jdbc.queryForObject("SELECT count(*) FROM learning.service_requests", Long.class));
        }
        @Override public void close() {
            learners.close();
            for (long question : questions) {
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
            }
            for (long lexeme : lexemes) jdbc.update("DELETE FROM learning.lexemes WHERE id=?", lexeme);
            for (long node : nodes) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
        }
    }

    @TestConfiguration
    static class SqlConfiguration {
        @Bean SqlCapture generationSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer generationInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        final List<String> statements = new CopyOnWriteArrayList<>();
        @Override public String inspect(String statement) { statements.add(statement); return statement; }
        void clear() { statements.clear(); }
        void assertContentReads(int maximum) {
            assertThat(statements).isNotEmpty().hasSizeLessThanOrEqualTo(maximum).allSatisfy(statement -> {
                String text = statement.stripLeading().toLowerCase(Locale.ROOT);
                assertThat(text).matches("(?s)(select|with)\\b.*").doesNotContain("auth.", "attempt", "service_requests", "jsonb_array", "->", "#>");
                assertThat(text).doesNotContain("insert ", "update ", "delete ");
            });
        }
    }
}
