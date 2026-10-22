package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthSessionIdMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthTokenRepository authTokenRepository;

    @Test
    void migrationRunsAfterV2BeforeV3AndCreatesNullableUuidColumnAndPartialIndex() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM learning.flyway_schema_history WHERE version IN ('1', '2', '2.1') "
                        + "ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsSubsequence("1", "2", "2.1");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema='auth' AND table_name='auth_tokens' AND column_name='session_id'",
                String.class)).isEqualTo("uuid");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema='auth' AND table_name='auth_tokens' AND column_name='session_id'",
                String.class)).isEqualTo("YES");
        String indexDefinition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='auth' AND indexname='ix_tokens_session'",
                String.class);
        assertThat(indexDefinition).contains("WHERE (session_id IS NOT NULL)");
    }

    @Test
    void nonRefreshTokenMayHaveNullSessionButRefreshTokenMayNot() {
        User user = userRepository.saveAndFlush(new User("session-migration-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode("MigrationPass123"), "Migration User"));
        AuthToken resetToken = authTokenRepository.saveAndFlush(new AuthToken(
                user.getId(), TokenType.RESET_PASSWORD, UUID.randomUUID().toString(), Instant.now().plusSeconds(600)));
        assertThat(resetToken.getSessionId()).isNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO auth.auth_tokens (user_id, token_type, token_hash, expires_at, session_id) "
                        + "VALUES (?, 'REFRESH', ?, now() + interval '1 day', NULL)",
                user.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO auth.auth_tokens (user_id, token_type, token_hash, expires_at, session_id) "
                        + "VALUES (?, 'REFRESH', ?, now() + interval '1 day', ?)",
                user.getId(), UUID.randomUUID().toString(), sessionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT session_id FROM auth.auth_tokens WHERE user_id=? AND token_type='REFRESH'",
                UUID.class, user.getId())).isEqualTo(sessionId);
    }
}
