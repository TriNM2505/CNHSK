package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@Transactional
class KnowledgeTreeMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void AT30_migrationsRunInOrderWithoutAddingBusinessTables() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3", "4", "5");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='learning' "
                        + "AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'",
                Long.class)).isEqualTo(23L);
    }

    @Test
    void AT30_newColumnTypesNullabilityAndIndexesMatchTheLockedSql() {
        assertColumn("knowledge_points", "domain", "character varying", "NO", 2);
        assertColumn("knowledge_points", "name_zh", "character varying", "NO", 200);
        assertColumn("knowledge_points", "can_do", "text", "YES", null);
        assertColumn("knowledge_points", "updated_at", "timestamp with time zone", "NO", null);
        for (String column : List.of("prereq_ids", "support_ids")) {
            assertColumn("knowledge_points", column, "ARRAY", "NO", null);
            assertThat(jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns WHERE table_schema='learning' "
                            + "AND table_name='knowledge_points' AND column_name=?", String.class, column))
                    .isEqualTo("_int8");
        }
        assertColumn("knowledge_points", "skill_type", "character varying", "YES", 20);
        assertColumn("question_knowledge_points", "role", "character varying", "NO", 10);
        assertColumn("questions", "code", "character varying", "YES", 50);
        assertColumn("questions", "difficulty", "smallint", "NO", null);
        assertColumn("questions", "correct_key", "character varying", "YES", 50);
        assertColumn("questions", "options", "jsonb", "NO", null);
        for (String table : List.of("topics", "lexemes")) {
            assertColumn(table, "knowledge_point_id", "bigint", "YES", null);
        }
        assertThat(index("ix_kp_domain")).contains("(domain, hsk_level)");
        assertThat(index("uq_qkp_primary")).contains("UNIQUE", "(question_id)", "WHERE", "PRIMARY");
        assertThat(index("ix_topic_kp")).contains("knowledge_point_id", "IS NOT NULL");
        assertThat(index("ix_lexeme_kp")).contains("knowledge_point_id", "IS NOT NULL");
        assertThat(jdbc.queryForList(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid='learning.knowledge_points'::regclass AND contype='c'",
                String.class)).anySatisfy(definition -> assertThat(definition)
                        .contains("domain", "'NA'", "'CH'", "'TV'", "'NP'", "'NL'", "'VB'"));
    }

    @ParameterizedTest
    @EnumSource(KnowledgeDomain.class)
    void AT30_allSixDomainsAllowNullableSkillAndInitializeNodeDefaults(KnowledgeDomain domain) {
        long id = node(domain.name() + "1-901", domain.name(), 1);
        assertThat(jdbc.queryForObject(
                "SELECT skill_type FROM learning.knowledge_points WHERE id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForMap(
                "SELECT cardinality(prereq_ids) AS prereqs, cardinality(support_ids) AS supports, "
                        + "updated_at IS NOT NULL AS timestamp_set FROM learning.knowledge_points WHERE id=?", id))
                .containsEntry("prereqs", 0).containsEntry("supports", 0).containsEntry("timestamp_set", true);
    }

    @ParameterizedTest
    @CsvSource({"NP3-04,NP,3", "np3-901,NP,3", "XX1-901,NA,1", "NP2-901,TV,2",
            "NP2-901,NP,1", "NP8-901,NP,8", "NP0-901,NP,1"})
    void AT30_kidCheckRejectsInvalidFormatDomainAndLevel(String code, String domain, int level) {
        assertThatThrownBy(() -> node(code, domain, level))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_kp_kid");
    }

    @Test
    void AT30_domainOutsideTheSixValuesIsRejected() {
        assertThatThrownBy(() -> node("NP1-901", "XX", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"domain", "name_zh"})
    void AT30_requiredNodeFieldsCannotBeNull(String field) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO learning.knowledge_points (code, title, hsk_level, domain, name_zh) VALUES (?, ?, ?, ?, ?)",
                "NP1-901", "Nút tự soạn", 1, field.equals("domain") ? null : "NP",
                field.equals("name_zh") ? null : "示例"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(field);
    }

    @Test
    void AT30_nodeCannotReferenceALexemeEvenWhenTheLexemeExists() {
        long lexemeId = jdbc.queryForObject(
                "INSERT INTO learning.lexemes (kind, text, meaning_vi) VALUES ('CHAR', ?, ?) RETURNING id",
                Long.class, "水", "nước tự soạn");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO learning.knowledge_points (code, title, hsk_level, domain, name_zh, lexeme_id) "
                        + "VALUES ('NP1-901', ?, 1, 'NP', ?, ?)", "Nút tự soạn", "示例", lexemeId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_kp_no_lexeme");
    }

    @Test
    void AT30_questionAndLabelDefaultsMatchTheMigration() {
        long questionId = question("AT30_DEFAULT");
        long nodeId = node("NP1-901", "NP", 1);
        jdbc.update("INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id) VALUES (?, ?)",
                questionId, nodeId);
        assertThat(jdbc.queryForObject("SELECT difficulty FROM learning.questions WHERE id=?", Short.class, questionId))
                .isEqualTo((short) 2);
        assertThat(jdbc.queryForMap(
                "SELECT source, status, reusable, options::text AS options FROM learning.questions WHERE id=?", questionId))
                .containsEntry("source", "IMPORT").containsEntry("status", "PENDING_REVIEW")
                .containsEntry("reusable", true).containsEntry("options", "[]");
        assertThat(jdbc.queryForObject(
                "SELECT role FROM learning.question_knowledge_points WHERE question_id=?", String.class, questionId))
                .isEqualTo("PRIMARY");
    }

    @Test
    void AT30_importRequiresCodeButOtherSourcesMayHaveNullCodes() {
        for (String source : List.of("AI", "MANUAL")) {
            jdbc.update("INSERT INTO learning.questions (qtype, hsk_level, stem, source) VALUES ('FILL_BLANK', 1, ?, ?)",
                    "Câu ___ tự soạn", source);
        }
        assertThatThrownBy(() -> question(null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_q_import_code");
    }

    @Test
    void AT30_questionCodeIsUnique() {
        question("AT30_UNIQUE");
        assertThatThrownBy(() -> question("AT30_UNIQUE"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("questions_code_key");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void AT30_difficultyOutsideOneToThreeIsRejected(int difficulty) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO learning.questions (code, qtype, hsk_level, stem, difficulty) "
                        + "VALUES ('AT30_DIFFICULTY', 'FILL_BLANK', 1, ?, ?)", "Câu ___ tự soạn", difficulty))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("questions_difficulty_check");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void AT30_allSupportedDifficultiesAreAccepted(int difficulty) {
        long id = question("AT30_DIFFICULTY");
        jdbc.update("UPDATE learning.questions SET difficulty=? WHERE id=?", difficulty, id);
        assertThat(jdbc.queryForObject("SELECT difficulty FROM learning.questions WHERE id=?", Integer.class, id))
                .isEqualTo(difficulty);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TERTIARY", "primary"})
    void AT30_invalidLabelRoleIsRejected(String role) {
        long questionId = question("AT30_ROLE");
        long nodeId = node("NP1-901", "NP", 1);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) VALUES (?, ?, ?)",
                questionId, nodeId, role))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("question_knowledge_points_role_check");
    }

    @Test
    void AT30_twoPrimaryLabelsForOneQuestionAreRejected() {
        long questionId = question("AT30_PRIMARY");
        long first = node("NP1-901", "NP", 1);
        long second = node("NP1-902", "NP", 1);
        jdbc.update("INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) "
                + "VALUES (?, ?, 'PRIMARY')", questionId, first);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) "
                        + "VALUES (?, ?, 'PRIMARY')", questionId, second))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_qkp_primary");
    }

    @Test
    void AT30_secondaryLabelsDoNotConflictWithThePartialUniqueIndex() {
        long questionId = question("AT30_SECONDARY");
        for (int index = 1; index <= 3; index++) {
            long nodeId = node("NP1-90" + index, "NP", 1);
            jdbc.update("INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) VALUES (?, ?, ?)",
                    questionId, nodeId, index == 1 ? "PRIMARY" : "SECONDARY");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.question_knowledge_points "
                + "WHERE question_id=? AND role='SECONDARY'", Long.class, questionId)).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC", "LEXEME"})
    void AT30_newContentLinksRejectMissingNodes(String target) {
        String sql = target.equals("TOPIC")
                ? "INSERT INTO learning.topics (code, name, hsk_level, knowledge_point_id) VALUES ('AT30_FK', ?, 1, -1)"
                : "INSERT INTO learning.lexemes (kind, text, meaning_vi, knowledge_point_id) VALUES ('CHAR', '水', ?, -1)";
        assertThatThrownBy(() -> jdbc.update(sql, "Nội dung tự soạn"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("knowledge_point_id_fkey");
    }

    @Test
    void AT30_upgradeFromV4PreservesContentAndAddsExactlyTheApprovedColumns() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("cnhsk_knowledge_upgrade_test")
                .withUsername("cnhsk_test_owner")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            flyway(database, "4").migrate();
            JdbcTemplate upgrade = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            Map<String, List<String>> additions = Map.of(
                    "knowledge_points", List.of("domain", "name_zh", "can_do", "prereq_ids", "support_ids", "updated_at"),
                    "question_knowledge_points", List.of("role"),
                    "questions", List.of("code", "difficulty"),
                    "topics", List.of("knowledge_point_id"),
                    "lexemes", List.of("knowledge_point_id"));
            Map<String, List<String>> before = new LinkedHashMap<>();
            additions.keySet().forEach(table -> before.put(table, columns(upgrade, table)));
            List<String> tables = businessTables(upgrade);
            long topicId = upgrade.queryForObject(
                    "INSERT INTO learning.topics (code, name, hsk_level) VALUES ('AT30_LEGACY', ?, 1) RETURNING id",
                    Long.class, "Chủ đề tự soạn");
            long lexemeId = upgrade.queryForObject(
                    "INSERT INTO learning.lexemes (kind, text, meaning_vi) VALUES ('CHAR', '水', ?) RETURNING id",
                    Long.class, "nước tự soạn");

            Flyway migration = flyway(database, "5");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(migration.validateWithResult().validationSuccessful).isTrue();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(businessTables(upgrade)).isEqualTo(tables);
            additions.forEach((table, added) -> assertThat(columns(upgrade, table))
                    .containsExactlyElementsOf(java.util.stream.Stream.concat(before.get(table).stream(), added.stream()).toList()));
            assertThat(upgrade.queryForMap("SELECT code, name, hsk_level, knowledge_point_id FROM learning.topics WHERE id=?", topicId))
                    .containsEntry("code", "AT30_LEGACY").containsEntry("name", "Chủ đề tự soạn")
                    .containsEntry("knowledge_point_id", null);
            assertThat(upgrade.queryForMap("SELECT text, meaning_vi, knowledge_point_id FROM learning.lexemes WHERE id=?", lexemeId))
                    .containsEntry("text", "水").containsEntry("meaning_vi", "nước tự soạn")
                    .containsEntry("knowledge_point_id", null);
        }
    }

    private void assertColumn(String table, String column, String type, String nullable, Integer length) {
        assertThat(jdbc.queryForMap("SELECT data_type, is_nullable, character_maximum_length "
                + "FROM information_schema.columns WHERE table_schema='learning' AND table_name=? AND column_name=?", table, column))
                .containsEntry("data_type", type).containsEntry("is_nullable", nullable)
                .containsEntry("character_maximum_length", length);
    }

    private String index(String name) {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname=?", String.class, name);
    }

    private long node(String code, String domain, int level) {
        return jdbc.queryForObject("INSERT INTO learning.knowledge_points (code, title, domain, name_zh, hsk_level) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, code, "Nút tự soạn", domain, "示例", level);
    }

    private long question(String code) {
        return jdbc.queryForObject("INSERT INTO learning.questions (code, qtype, hsk_level, stem) "
                + "VALUES (?, 'FILL_BLANK', 1, ?) RETURNING id", Long.class, code, "Câu ___ tự soạn");
    }

    private Flyway flyway(PostgreSQLContainer<?> database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("learning", "auth", "community", "shared").defaultSchema("learning").target(target).load();
    }

    private List<String> columns(JdbcTemplate database, String table) {
        return database.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name=? ORDER BY ordinal_position", String.class, table);
    }

    private List<String> businessTables(JdbcTemplate database) {
        return database.queryForList("SELECT table_schema || '.' || table_name FROM information_schema.tables "
                + "WHERE table_schema IN ('auth','learning','community','shared') AND table_type='BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY table_schema, table_name", String.class);
    }
}
