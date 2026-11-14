package com.cnhsk.shared.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class SettingsMigrationIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
            .encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void flywayCreatesExactColumnsPrimaryKeyAndCrossSchemaForeignKey() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT version FROM learning.flyway_schema_history "
                + "WHERE success ORDER BY installed_rank", String.class)).containsSubsequence("9", "10");
        var columns = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length, column_default
                FROM information_schema.columns
                WHERE table_schema='shared' AND table_name='app_settings' ORDER BY ordinal_position
                """);
        assertThat(columns).hasSize(4);
        assertThat(columns).extracting(row -> row.get("column_name"))
                .containsExactly("key", "value", "updated_by", "updated_at");
        assertThat(columns).extracting(row -> row.get("data_type"))
                .containsExactly("character varying", "text", "bigint", "timestamp with time zone");
        assertThat(columns).allSatisfy(row -> assertThat(row.get("is_nullable")).isEqualTo("NO"));
        assertThat(columns.get(0).get("character_maximum_length")).isEqualTo(100);
        assertThat(columns.get(3).get("column_default")).isEqualTo("now()");
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conrelid='shared.app_settings'::regclass AND contype IN ('p','f')", String.class))
                .containsExactlyInAnyOrder("PRIMARY KEY (key)", "FOREIGN KEY (updated_by) REFERENCES auth.users(id)");
    }

    @Test
    void migrationRerunPreservesExistingDataAndTimestampDefaultIsUtcInstant() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long user = user();
            String key = "migration-" + UUID.randomUUID();
            jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?)", key, "17", user);
            var before = jdbc.queryForMap("SELECT * FROM shared.app_settings WHERE key=?", key);
            assertThat(before.get("updated_at")).isNotNull();
            OffsetDateTime at = jdbc.queryForObject("SELECT updated_at FROM shared.app_settings WHERE key=?",
                    OffsetDateTime.class, key);
            assertThat(at.getOffset().getTotalSeconds()).isZero();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForMap("SELECT * FROM shared.app_settings WHERE key=?", key)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT display_name FROM auth.users WHERE id=?", String.class, user))
                    .isEqualTo("Settings migration fixture");
            status.setRollbackOnly();
        });
    }

    @Test
    void primaryKeyRejectsDuplicates() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long user = user();
            String key = "migration-" + UUID.randomUUID();
            jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?)", key, "1", user);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?)",
                    key, "2", user)).isInstanceOf(DuplicateKeyException.class);
            status.setRollbackOnly();
        });
    }

    @Test
    void foreignKeyRejectsMissingActor() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?)",
                    "migration-" + UUID.randomUUID(), "1", Long.MIN_VALUE))
                    .isInstanceOf(DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }

    @Test
    void nullValueIsRejected() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            long user = user();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by) VALUES (?,?,?)",
                    "migration-" + UUID.randomUUID(), null, user)).isInstanceOf(DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,'Settings migration fixture') RETURNING id", Long.class,
                "settings-migration-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }
}
