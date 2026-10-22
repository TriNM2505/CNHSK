package com.cnhsk.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.entity.UserRoleId;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRepositoriesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void userInsertUsesPlanDefaultAndMapsAllSpecifiedFieldsButNotQuotaColumns() {
        User user = newUser("mapped");
        user.setAvatarUrl("https://example.test/avatar.png");
        user.setHskLevel((short) 4);
        user.setEmailVerifiedAt(Instant.parse("2026-10-20T10:00:00Z"));
        user.setSuspendedAt(Instant.parse("2026-10-20T10:01:00Z"));
        user.setSuspendedUntil(Instant.parse("2026-10-21T10:01:00Z"));
        user.setReminderChannels("WEB,EMAIL");
        user.setPlanExpiresAt(null);

        User saved = userRepository.saveAndFlush(user);
        User loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getPlanCode()).isEqualTo("FREE");
        assertThat(loaded.getPlanExpiresAt()).isNull();
        assertThat(loaded.getReminderChannels()).isEqualTo("WEB,EMAIL");
        assertThat(loaded.getAvatarUrl()).isEqualTo("https://example.test/avatar.png");
        assertThat(loaded.getHskLevel()).isEqualTo((short) 4);
        assertThat(loaded.getEmailVerifiedAt()).isEqualTo(user.getEmailVerifiedAt());
        assertThat(loaded.getSuspendedAt()).isEqualTo(user.getSuspendedAt());
        assertThat(loaded.getSuspendedUntil()).isEqualTo(user.getSuspendedUntil());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT free_usage = '{}'::jsonb AND usage_reset_at IS NOT NULL "
                        + "FROM auth.users WHERE id = ?",
                Boolean.class,
                saved.getId())).isTrue();
    }

    @Test
    @Transactional
    void userRoleAndAuthTokenPersistEnumNamesAndCompositeKey() {
        User user = userRepository.saveAndFlush(newUser("role-token"));
        UserRole role = userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.TEACHER, null));
        Instant expiresAt = Instant.parse("2026-10-21T10:00:00Z");
        AuthToken token = authTokenRepository.saveAndFlush(
                new AuthToken(user.getId(), TokenType.EMAIL_VERIFY, "hash-" + UUID.randomUUID(), expiresAt));

        assertThat(userRoleRepository.findById(new UserRoleId(user.getId(), RoleCode.TEACHER)))
                .isPresent()
                .get()
                .extracting(UserRole::getGrantedBy)
                .isNull();
        assertThat(role.getGrantedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role_code FROM auth.user_roles WHERE user_id = ?",
                String.class,
                user.getId())).isEqualTo("TEACHER");
        assertThat(authTokenRepository.findById(token.getId()).orElseThrow().getTokenType())
                .isEqualTo(TokenType.EMAIL_VERIFY);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT token_type FROM auth.auth_tokens WHERE id = ?",
                String.class,
                token.getId())).isEqualTo("EMAIL_VERIFY");
    }

    @Test
    @Transactional
    void countActiveAdminsExcludesBannedAdmins() {
        long activeAdminsBefore = userRepository.countActiveAdmins();
        User activeAdmin = userRepository.saveAndFlush(newUser("active-admin"));
        User bannedAdmin = userRepository.saveAndFlush(newUser("banned-admin"));
        userRoleRepository.saveAndFlush(new UserRole(activeAdmin.getId(), RoleCode.ADMIN, null));
        assertThat(userRepository.countActiveAdmins()).isEqualTo(activeAdminsBefore + 1);

        userRoleRepository.saveAndFlush(new UserRole(bannedAdmin.getId(), RoleCode.ADMIN, null));
        bannedAdmin.setBannedAt(Instant.parse("2026-10-20T10:00:00Z"));
        bannedAdmin.setBanReason("test ban");
        userRepository.saveAndFlush(bannedAdmin);

        assertThat(userRepository.countActiveAdmins()).isEqualTo(activeAdminsBefore + 1);
    }

    @Test
    @Transactional
    void userSearchSupportsPageableAcrossEmailAndDisplayName() {
        userRepository.saveAndFlush(newUser("page-alpha", "Alpha User"));
        userRepository.saveAndFlush(newUser("page-beta", "Beta User"));

        Page<User> page = userRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                "PAGE-", "PAGE-", PageRequest.of(0, 1, Sort.by("id").ascending()));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @Transactional
    void databaseCheckRejectsUnknownRoleValue() {
        User user = userRepository.saveAndFlush(newUser("bad-role"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO auth.user_roles (user_id, role_code) VALUES (?, ?)", user.getId(), "HACKER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User newUser(String suffix) {
        return newUser(suffix, "User " + suffix);
    }

    private User newUser(String suffix, String displayName) {
        return new User(
                suffix + "-" + UUID.randomUUID() + "@example.test",
                "test-bcrypt-hash",
                displayName);
    }
}
