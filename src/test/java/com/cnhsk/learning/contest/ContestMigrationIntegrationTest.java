package com.cnhsk.learning.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ContestMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    @Test
    void AT01_flywayCreatesTheSpecifiedPartialUniqueIndex() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("SELECT version FROM learning.flyway_schema_history "
                + "WHERE success ORDER BY installed_rank", String.class)).containsSubsequence("8", "9");
        var index = jdbc.queryForMap("""
                SELECT i.indisunique, i.indisvalid, i.indnkeyatts, i.indnatts,
                       pg_get_expr(i.indpred, i.indrelid) AS predicate
                FROM pg_index i
                WHERE i.indexrelid = 'learning.uq_contest_attempt'::regclass
                  AND i.indrelid = 'learning.attempts'::regclass
                """);
        assertThat(index).containsEntry("indisunique", true).containsEntry("indisvalid", true);
        assertThat(((Number) index.get("indnkeyatts")).intValue()).isEqualTo(2);
        assertThat(((Number) index.get("indnatts")).intValue()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT pg_get_indexdef('learning.uq_contest_attempt'::regclass, n, true) "
                + "FROM generate_series(1, 2) n ORDER BY n", String.class))
                .containsExactly("user_id", "ref_id");
        assertThat(index.get("predicate").toString().replace("::text", "")
                .replace("(", "").replace(")", "").replaceAll("\\s+", " ").strip())
                .isEqualTo("kind = 'CONTEST'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "SUBMITTED", "ABANDONED", "EXPIRED"})
    void AT01_secondContestAttemptIsRejectedRegardlessOfTheFirstAttemptsStatus(String status) {
        long user = user();
        attempt(user, "CONTEST", 21_001, status);

        assertThatThrownBy(() -> attempt(user, "CONTEST", 21_001, "IN_PROGRESS"))
                .isInstanceOfSatisfying(DuplicateKeyException.class, exception -> {
                    assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
                    assertThat(exception.getMostSpecificCause().getMessage()).contains("uq_contest_attempt");
                });
    }

    @Test
    void AT01_otherLearnersContestsAndAttemptKindsRemainIndependent() {
        long first = user();
        long second = user();
        attempt(first, "CONTEST", 21_001, "IN_PROGRESS");
        attempt(second, "CONTEST", 21_001, "IN_PROGRESS");
        attempt(first, "CONTEST", 21_002, "IN_PROGRESS");
        attempt(first, "PRACTICE", 21_001, "IN_PROGRESS");
        attempt(first, "PRACTICE", 21_001, "IN_PROGRESS");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempts WHERE user_id IN (?,?)",
                Long.class, first, second)).isEqualTo(5L);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,?,'Contest migration fixture') RETURNING id", Long.class,
                "contest-migration-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    private void attempt(long userId, String kind, long contestId, String status) {
        jdbc.update("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,submitted_at)
                VALUES (?,?,?,?,?,CASE WHEN ?='SUBMITTED' THEN now() ELSE NULL END)
                """, userId, kind, "CONTEST".equals(kind) ? "CONTEST" : "TOPIC_PRACTICE", contestId, status, status);
    }
}
