package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

class LeaderboardMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    @Test
    void AT16_flywayAppliesV8AndCreatesTheSpecifiedPartialIndex() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT version FROM learning.flyway_schema_history "
                + "WHERE success ORDER BY installed_rank", String.class)).containsSubsequence("7", "8");
        var index = jdbc.queryForMap("""
                SELECT i.indisunique, i.indisvalid, i.indnkeyatts, i.indnatts,
                       am.amname, pg_get_expr(i.indpred, i.indrelid) AS predicate
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_am am ON am.oid = c.relam
                WHERE i.indexrelid = 'learning.ix_att_ref_rank'::regclass
                  AND i.indrelid = 'learning.attempts'::regclass
                """);
        assertThat(index).containsEntry("indisunique", false).containsEntry("indisvalid", true)
                .containsEntry("amname", "btree");
        assertThat(((Number) index.get("indnkeyatts")).intValue()).isEqualTo(3);
        assertThat(((Number) index.get("indnatts")).intValue()).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT pg_get_indexdef('learning.ix_att_ref_rank'::regclass, n, true) "
                + "FROM generate_series(1, 3) n ORDER BY n", String.class))
                .containsExactly("kind", "ref_id", "submitted_at");
        assertThat(index.get("predicate").toString().replace("::text", "")
                .replace("(", "").replace(")", "").replaceAll("\\s+", " ").strip())
                .isEqualTo("status = 'SUBMITTED'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE schemaname='learning' AND indexname='ix_att_ref_rank'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                + "WHERE schemaname='learning' AND tablename='attempts'", String.class))
                .contains("ix_att_rank", "uq_quiz_first", "ix_att_user_ref");
    }

    @Test
    void AT16_upgradeFromV7AddsOnlyTheIndexAndPreservesAttempts() {
        try (var database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("leaderboard_upgrade").withUsername("leaderboard_owner")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            migration(database, "7").migrate();
            var upgrade = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            long userId = upgrade.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                    + "VALUES (?,?,?) RETURNING id", Long.class, "leaderboard-upgrade@example.test",
                    new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()), "Nguoi kiem thu");
            upgrade.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,submitted_at,score) "
                    + "VALUES (?,'QUIZ','QUIZ',42,'SUBMITTED',TIMESTAMPTZ '2026-11-13 00:00:00+00',50)", userId);
            upgrade.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id) "
                    + "VALUES (?,'PRACTICE','QUIZ_RETRY',42)", userId);
            var beforeSchema = schema(upgrade);
            var beforeIndexes = indexes(upgrade);
            var beforeAttempts = upgrade.queryForList("SELECT * FROM learning.attempts ORDER BY id");
            assertThat(beforeIndexes).noneMatch(definition -> definition.contains("ix_att_ref_rank"));

            var migration = migration(database, "8");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(migration.validateWithResult().validationSuccessful).isTrue();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(schema(upgrade)).isEqualTo(beforeSchema);
            assertThat(indexes(upgrade)).containsAll(beforeIndexes).hasSize(beforeIndexes.size() + 1)
                    .anyMatch(definition -> definition.contains("ix_att_ref_rank"));
            assertThat(upgrade.queryForList("SELECT * FROM learning.attempts ORDER BY id"))
                    .isEqualTo(beforeAttempts);
        }
    }

    private Flyway migration(PostgreSQLContainer<?> database, String target) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("learning", "auth", "community", "shared").defaultSchema("learning").target(target).load();
    }

    private Map<String, List<String>> schema(JdbcTemplate database) {
        return Map.of(
                "tables", database.queryForList("SELECT table_schema||'.'||table_name FROM information_schema.tables "
                        + "WHERE table_schema IN ('learning','auth','community','shared') AND table_type='BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history' ORDER BY 1", String.class),
                "columns", database.queryForList("SELECT table_schema||'.'||table_name||'.'||column_name||':' "
                        + "||data_type||':'||is_nullable||':'||COALESCE(column_default,'') FROM information_schema.columns "
                        + "WHERE table_schema IN ('learning','auth','community','shared') "
                        + "AND table_name <> 'flyway_schema_history' ORDER BY 1", String.class),
                "constraints", database.queryForList("SELECT conrelid::regclass::text||':'||conname||':' "
                        + "||pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace IN "
                        + "('learning'::regnamespace,'auth'::regnamespace,'community'::regnamespace,'shared'::regnamespace) "
                        + "AND conrelid <> 'learning.flyway_schema_history'::regclass ORDER BY 1", String.class));
    }

    private List<String> indexes(JdbcTemplate database) {
        return database.queryForList("SELECT schemaname||'.'||indexname||':'||indexdef FROM pg_indexes "
                + "WHERE schemaname IN ('learning','auth','community','shared') "
                + "AND tablename <> 'flyway_schema_history' ORDER BY 1", String.class);
    }
}
