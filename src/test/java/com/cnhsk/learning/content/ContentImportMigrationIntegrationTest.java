package com.cnhsk.learning.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@Transactional
class ContentImportMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void FR012_migrationsRunInOrderOnAnEmptyDatabase() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3");
    }

    @Test
    void FR011_topicCodeIsLimitedToFiftyCharactersAndRequired() {
        assertThat(jdbcTemplate.queryForMap(
                "SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema='learning' AND table_name='topics' AND column_name='code'"))
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 50)
                .containsEntry("is_nullable", "NO");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO learning.topics (name, hsk_level) VALUES (?, ?)", "Migration test topic", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void FR011_topicCodeMustBeUnique() {
        createTopic("MIGRATION_TOPIC");

        assertThatThrownBy(() -> createTopic("MIGRATION_TOPIC"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("topics_code_key");
    }

    @Test
    void FR011_AT037_topicItemsAcceptALexemeWithoutAKnowledgePoint() {
        long topicId = createTopic("MIGRATION_LEXEME_TOPIC");
        long lexemeId = jdbcTemplate.queryForObject(
                "INSERT INTO learning.lexemes (kind, text, meaning_vi, hsk_level) "
                        + "VALUES ('CHAR', ?, ?, ?) RETURNING id",
                Long.class, "我", "tôi", 1);

        long itemId = jdbcTemplate.queryForObject(
                "INSERT INTO learning.topic_items (topic_id, lexeme_id) VALUES (?, ?) RETURNING id",
                Long.class, topicId, lexemeId);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT lexeme_id, knowledge_point_id FROM learning.topic_items WHERE id=?", itemId))
                .containsEntry("lexeme_id", lexemeId)
                .containsEntry("knowledge_point_id", null);
    }

    @Test
    void FR017_onlyOneImportMayRunAcrossAllUsers() {
        long firstUserId = createUser(jdbcTemplate);
        long secondUserId = createUser(jdbcTemplate);
        createRequest(firstUserId, "IMPORT", "RUNNING", null);
        long pendingId = createRequest(secondUserId, "IMPORT", "PENDING", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE learning.service_requests SET status='RUNNING' WHERE id=?", pendingId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_import_running");
    }

    @Test
    void FR017_otherStatusesAndRequestKindsRemainAllowed() {
        long userId = createUser(jdbcTemplate);
        createRequest(userId, "IMPORT", "RUNNING", null);
        createRequest(userId, "IMPORT", "PENDING", null);
        createRequest(userId, "IMPORT", "PENDING", null);
        createRequest(userId, "TRANSLATE", "RUNNING", null);
        createRequest(userId, "TRANSLATE", "RUNNING", null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, userId))
                .isEqualTo(5L);
    }

    @Test
    void FR021_completedImportsMayReuseTheSameFileHash() {
        long userId = createUser(jdbcTemplate);
        String fileHash = "a".repeat(64);
        createRequest(userId, "IMPORT", "COMPLETED", fileHash);
        createRequest(userId, "IMPORT", "COMPLETED", fileHash);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning.service_requests WHERE user_id=? AND file_hash=?",
                Long.class, userId, fileHash)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname='learning' AND indexname='uq_import_file'",
                Long.class)).isZero();
    }

    @Test
    void FR029_importHistoryIndexUsesDescendingCreationTimeAndOnlyImportRows() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname='ix_req_import_hist'",
                String.class);

        assertThat(definition)
                .contains("(created_at DESC)")
                .contains("WHERE ((kind)::text = 'IMPORT'::text)")
                .doesNotContain("UNIQUE", "status");
    }

    @Test
    void FR034_pronunciationUnitsDefaultToAnEmptyNonNullJsonArray() {
        long stageId = jdbcTemplate.queryForObject(
                "INSERT INTO learning.pron_stages (stage_index, name) VALUES (?, ?) RETURNING id",
                Long.class, 1, "Migration test stage");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT units::text FROM learning.pron_stages WHERE id=?", String.class, stageId))
                .isEqualTo("[]");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema='learning' AND table_name='pron_stages' AND column_name='units'"))
                .containsEntry("data_type", "jsonb")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    void FR012_upgradeFromV2_1PreservesExistingDataAndInitializesUnits() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("cnhsk_import_upgrade_test")
                .withUsername("cnhsk_test_owner")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("learning", "auth", "community", "shared")
                    .defaultSchema("learning")
                    .target("2.1")
                    .load()
                    .migrate();

            JdbcTemplate upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            long stageId = upgradeJdbc.queryForObject(
                    "INSERT INTO learning.pron_stages (stage_index, name, target_sounds) "
                            + "VALUES (?, ?, CAST(? AS jsonb)) RETURNING id",
                    Long.class, 1, "Existing stage", "[\"zh\",\"z\"]");
            long userId = createUser(upgradeJdbc);
            long requestId = upgradeJdbc.queryForObject(
                    "INSERT INTO learning.service_requests (user_id, kind, status, file_hash) "
                            + "VALUES (?, 'IMPORT', 'COMPLETED', ?) RETURNING id",
                    Long.class, userId, "b".repeat(64));

            Flyway flyway = Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("learning", "auth", "community", "shared")
                    .defaultSchema("learning")
                    .target("3")
                    .load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            assertThat(upgradeJdbc.queryForMap(
                    "SELECT name, target_sounds::text AS sounds, units::text AS units "
                            + "FROM learning.pron_stages WHERE id=?", stageId))
                    .containsEntry("name", "Existing stage")
                    .containsEntry("sounds", "[\"zh\", \"z\"]")
                    .containsEntry("units", "[]");
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT file_hash FROM learning.service_requests WHERE id=?", String.class, requestId))
                    .isEqualTo("b".repeat(64));
        }
    }

    private long createTopic(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO learning.topics (code, name, hsk_level) VALUES (?, ?, ?) RETURNING id",
                Long.class, code, "Migration test topic", 1);
    }

    private static long createUser(JdbcTemplate jdbc) {
        String identifier = UUID.randomUUID().toString();
        String passwordHash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
        return jdbc.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "import-migration-" + identifier + "@example.test", passwordHash, "Migration test user");
    }

    private long createRequest(long userId, String kind, String status, String fileHash) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO learning.service_requests (user_id, kind, status, file_hash) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, userId, kind, status, fileHash);
    }
}
