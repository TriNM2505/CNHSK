package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@Transactional
class MasteryProgressMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private JdbcTemplate jdbc;

    @Test
    void AT03_migrationMatchesTheLockedSpecificationVerbatim() throws Exception {
        String specification = Files.readString(Path.of("specs/011-mastery-learning-frontier/spec.md"), StandardCharsets.UTF_8);
        Matcher block = Pattern.compile("(?s)### 0\\.1.*?```sql\\R(.*?)```").matcher(specification);
        assertThat(block.find()).as("spec 011 section 0.1 SQL block").isTrue();
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V6__mastery_progress.sql"), StandardCharsets.UTF_8);
        assertThat(migration.replace("\r\n", "\n").strip()).isEqualTo(block.group(1).replace("\r\n", "\n").strip());
    }

    @Test
    void AT03_flywayRunsV6AfterTheExistingMigrations() {
        assertThat(jdbc.queryForList("SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3", "4", "5", "6");
    }

    @Test
    void AT03_columnTypesNullabilityDefaultsAndIndexMatchTheLockedSql() {
        assertThat(jdbc.queryForMap("SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                + "WHERE table_schema='learning' AND table_name='user_progress' AND column_name='mp_state'"))
                .containsEntry("data_type", "character varying").containsEntry("character_maximum_length", 12)
                .containsEntry("is_nullable", "YES");
        assertThat(jdbc.queryForMap("SELECT data_type, numeric_precision, numeric_scale, is_nullable FROM information_schema.columns "
                + "WHERE table_schema='learning' AND table_name='user_progress' AND column_name='confidence'"))
                .containsEntry("data_type", "numeric").containsEntry("numeric_precision", 6)
                .containsEntry("numeric_scale", 3).containsEntry("is_nullable", "YES");
        assertThat(jdbc.queryForMap("SELECT data_type, udt_name, is_nullable FROM information_schema.columns "
                + "WHERE table_schema='learning' AND table_name='user_progress' AND column_name='evidence_item_ids'"))
                .containsEntry("data_type", "ARRAY").containsEntry("udt_name", "_int8").containsEntry("is_nullable", "NO");
        assertThat(jdbc.queryForObject("SELECT column_default FROM information_schema.columns "
                + "WHERE table_schema='learning' AND table_name='user_progress' AND column_name='evidence_item_ids'", String.class))
                .contains("{}", "bigint[]");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname='ix_prog_kp_state'",
                String.class)).contains("(user_id, mp_state)", "WHERE", "KNOWLEDGE_POINT");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname='learning' AND tablename='user_progress'",
                String.class)).contains("user_progress_pkey", "ix_prog_due", "ix_prog_mastery", "ix_prog_type");
    }

    @Test
    void AT03_checkNamesAndDefinitionsPreserveOldConstraintsAndAddOnlyTheApprovedRules() {
        assertThat(jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid='learning.user_progress'::regclass AND contype='c'",
                String.class)).contains("user_progress_target_type_check", "user_progress_mp_state_check",
                        "user_progress_confidence_check", "ck_prog_mp", "user_progress_mastery_check",
                        "user_progress_stability_check", "user_progress_percent_check", "user_progress_state_check");
        assertThat(check(jdbc, "user_progress_target_type_check"))
                .contains("KNOWLEDGE_POINT", "TOPIC", "PRON_STAGE", "FLASHCARD", "VIDEO", "LEXEME");
        assertThat(check(jdbc, "user_progress_mp_state_check"))
                .contains("notyet", "insufficient", "review", "consolidate", "extend").doesNotContain("inferred");
        assertThat(check(jdbc, "user_progress_confidence_check")).contains("confidence", ">=", "0");
        assertThat(check(jdbc, "ck_prog_mp")).contains("target_type", "KNOWLEDGE_POINT", "mp_state IS NOT NULL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"notyet", "insufficient", "review", "consolidate", "extend"})
    void AT03_allFiveStoredMasteryStatesAreAccepted(String state) {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mp_state) VALUES (?, 'KNOWLEDGE_POINT', 901, ?)",
                userId, state);
        assertThat(jdbc.queryForMap("SELECT mp_state, confidence, cardinality(evidence_item_ids) AS evidence_count "
                + "FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=901", userId))
                .containsEntry("mp_state", state).containsEntry("confidence", null).containsEntry("evidence_count", 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inferred", "unknown"})
    void AT03_inferredAndUnknownStatesCannotBeStored(String state) {
        long userId = user(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mp_state) "
                + "VALUES (?, 'KNOWLEDGE_POINT', 901, ?)", userId, state))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_progress_mp_state_check");
    }

    @Test
    void AT03_knowledgePointInsertRequiresMpState() {
        long userId = user(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) "
                + "VALUES (?, 'KNOWLEDGE_POINT', 901)", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_prog_mp");
    }

    @Test
    void AT03_knowledgePointUpdateCannotRemoveMpState() {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mp_state) VALUES (?, 'KNOWLEDGE_POINT', 901, 'review')", userId);
        assertThatThrownBy(() -> jdbc.update("UPDATE learning.user_progress SET mp_state=NULL "
                + "WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=901", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_prog_mp");
    }

    @Test
    void AT03_changingTargetToKnowledgePointWithoutMpStateIsRejected() {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, 'TOPIC', 901)", userId);
        assertThatThrownBy(() -> jdbc.update("UPDATE learning.user_progress SET target_type='KNOWLEDGE_POINT' "
                + "WHERE user_id=? AND target_type='TOPIC' AND target_id=901", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_prog_mp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC", "PRON_STAGE", "FLASHCARD", "VIDEO", "LEXEME"})
    void AT03_otherTargetsAcceptNullMpStateAndKeepLegacyDefaults(String targetType) {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, ?, 901)", userId, targetType);
        assertThat(jdbc.queryForMap("SELECT mp_state, confidence, cardinality(evidence_item_ids) AS evidence_count, state, "
                + "mastery, stability, difficulty, review_count, lapse_count FROM learning.user_progress "
                + "WHERE user_id=? AND target_type=? AND target_id=901", userId, targetType))
                .containsEntry("mp_state", null).containsEntry("confidence", null).containsEntry("evidence_count", 0)
                .containsEntry("state", "LEARNING").containsEntry("mastery", new BigDecimal("0.000"))
                .containsEntry("stability", new BigDecimal("0.100")).containsEntry("difficulty", new BigDecimal("5.00"))
                .containsEntry("review_count", 0).containsEntry("lapse_count", 0);
    }

    @Test
    void AT03_unknownTargetTypeIsRejected() {
        long userId = user(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, 'WORD', 901)", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_progress_target_type_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.000", "999.999"})
    void AT03_nonNegativeConfidenceFitsTheDeclaredPrecision(String value) {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mp_state, confidence) "
                + "VALUES (?, 'KNOWLEDGE_POINT', 901, 'notyet', ?)", userId, new BigDecimal(value));
        assertThat(jdbc.queryForObject("SELECT confidence FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=901",
                BigDecimal.class, userId)).isEqualByComparingTo(value);
    }

    @Test
    void AT03_negativeConfidenceIsRejected() {
        long userId = user(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, mp_state, confidence) "
                + "VALUES (?, 'KNOWLEDGE_POINT', 901, 'notyet', -0.001)", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_progress_confidence_check");
    }

    @Test
    void AT03_evidenceArrayCannotBeNull() {
        long userId = user(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, evidence_item_ids) "
                + "VALUES (?, 'LEXEME', 901, NULL)", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("evidence_item_ids");
    }

    @Test
    void AT03_compositePrimaryKeyStillRejectsDuplicateTargets() {
        long userId = user(jdbc);
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, 'LEXEME', 901)", userId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, 'LEXEME', 901)", userId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("user_progress_pkey");
    }

    @Test
    void AT03_upgradeFromV5PreservesPronunciationAndAddsExactlyThreeColumns() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("cnhsk_mastery_upgrade_test").withUsername("cnhsk_test_owner")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            flyway(database, "5").migrate();
            JdbcTemplate upgrade = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            assertThat(check(upgrade, "user_progress_target_type_check")).contains("PRON_STAGE").doesNotContain("LEXEME");
            List<String> beforeColumns = columns(upgrade);
            List<String> beforeTables = businessTables(upgrade);
            long userId = user(upgrade);
            upgrade.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state, unlocked_at, last_seen_at, updated_at) "
                    + "VALUES (?, 'PRON_STAGE', 901, 85.00, 'COMPLETED', '2026-11-01T01:00:00Z', '2026-11-02T01:00:00Z', '2026-11-02T01:00:00Z')", userId);
            Map<String, Object> legacy = upgrade.queryForMap("SELECT * FROM learning.user_progress WHERE user_id=?", userId);
            assertThat(upgrade.queryForObject("SELECT count(*) FROM learning.user_progress WHERE target_type='KNOWLEDGE_POINT'", Long.class)).isZero();

            Flyway migration = flyway(database, "6");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(migration.validateWithResult().validationSuccessful).isTrue();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(businessTables(upgrade)).isEqualTo(beforeTables);
            assertThat(columns(upgrade)).containsExactlyElementsOf(Stream.concat(beforeColumns.stream(),
                    Stream.of("mp_state", "confidence", "evidence_item_ids")).toList());
            // Column names originate from information_schema, never from user input.
            assertThat(upgrade.queryForMap("SELECT " + String.join(", ", beforeColumns)
                    + " FROM learning.user_progress WHERE user_id=?", userId)).isEqualTo(legacy);
            assertThat(upgrade.queryForMap("SELECT mp_state, confidence, cardinality(evidence_item_ids) AS evidence_count "
                    + "FROM learning.user_progress WHERE user_id=?", userId))
                    .containsEntry("mp_state", null).containsEntry("confidence", null).containsEntry("evidence_count", 0);
            upgrade.update("INSERT INTO learning.user_progress (user_id, target_type, target_id) VALUES (?, 'LEXEME', 901)", userId);
            assertThat(upgrade.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='LEXEME'",
                    Long.class, userId)).isEqualTo(1L);
        }
    }

    private long user(JdbcTemplate database) {
        return database.queryForObject("INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "at03-" + UUID.randomUUID() + "@example.test", TEST_PASSWORD_HASH, "Người kiểm thử");
    }

    private String check(JdbcTemplate database, String name) {
        return database.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conrelid='learning.user_progress'::regclass AND conname=?", String.class, name);
    }

    private Flyway flyway(PostgreSQLContainer<?> database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("learning", "auth", "community", "shared").defaultSchema("learning").target(target).load();
    }

    private List<String> columns(JdbcTemplate database) {
        return database.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='user_progress' ORDER BY ordinal_position", String.class);
    }

    private List<String> businessTables(JdbcTemplate database) {
        return database.queryForList("SELECT table_schema || '.' || table_name FROM information_schema.tables "
                + "WHERE table_schema IN ('auth','learning','community','shared') AND table_type='BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY table_schema, table_name", String.class);
    }
}
