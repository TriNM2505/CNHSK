package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.RoleService;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("RoleTestPass123");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleService roleService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private AuditService auditServiceSpy;
    @MockitoSpyBean private SessionInvalidationService invalidationServiceSpy;

    @Test
    void AT65_grantStoresGrantorTimestampAndBeforeAfterAudit() throws Exception {
        User admin = createUser("role-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User target = createUser("role-target");
        grantFixture(target, RoleCode.LEARNER, null);

        mockMvc.perform(post("/api/admin/users/{id}/roles", target.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .contentType("application/json")
                        .content("{\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        UserRole granted = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(target.getId()).stream()
                .filter(role -> role.getRoleCode() == RoleCode.TEACHER).findFirst().orElseThrow();
        assertThat(granted.getGrantedBy()).isEqualTo(admin.getId());
        assertThat(granted.getGrantedAt()).isNotNull();
        String detailJson = jdbcTemplate.queryForObject(
                "SELECT detail FROM shared.audit_logs WHERE actor_id = ? AND action = 'ROLE_GRANT' "
                        + "AND target_type = 'user' AND target_id = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                admin.getId(), target.getId());
        JsonNode detail = objectMapper.readTree(detailJson);
        assertThat(detail.path("before").toString()).isEqualTo("[\"LEARNER\"]");
        assertThat(detail.path("after").toString()).isEqualTo("[\"LEARNER\",\"TEACHER\"]");
    }

    @Test
    void AT66_AT67_selfGrantAndDuplicateGrantReturnContractErrorsWithoutExtraWrites() throws Exception {
        User admin = createUser("role-self");
        grantFixture(admin, RoleCode.ADMIN, null);
        grantFixture(admin, RoleCode.LEARNER, null);

        mockMvc.perform(post("/api/admin/users/{id}/roles", admin.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .contentType("application/json")
                        .content("{\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SELF_ROLE_GRANT"));

        User target = createUser("role-duplicate");
        grantFixture(target, RoleCode.LEARNER, null);
        grantFixture(target, RoleCode.TEACHER, admin.getId());
        mockMvc.perform(post("/api/admin/users/{id}/roles", target.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .contentType("application/json")
                        .content("{\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ROLE_ALREADY_GRANTED"));

        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(target.getId()))
                .filteredOn(role -> role.getRoleCode() == RoleCode.TEACHER).hasSize(1);
        assertThat(auditCount(admin.getId(), target.getId(), "ROLE_GRANT")).isZero();
    }

    @Test
    void grantsAdminToBannedUserWithoutCountingThatUserAsActiveAdmin() throws Exception {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
        User admin = createUser("role-banned-grant-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User banned = createUser("role-banned-grant-target");
        banned.setBannedAt(Instant.now());
        banned.setBanReason("policy violation");
        userRepository.saveAndFlush(banned);

        mockMvc.perform(post("/api/admin/users/{id}/roles", banned.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .contentType("application/json")
                        .content("{\"roleCode\":\"ADMIN\"}"))
                .andExpect(status().isNoContent());
        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(banned.getId()))
                .extracting(UserRole::getRoleCode).contains(RoleCode.ADMIN);
        assertThat(userRepository.countActiveAdmins()).isEqualTo(1);
        }
    }

    @Test
    void auditFailureRollsBackGrantedRole() {
        User admin = createUser("role-audit-failure-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User target = createUser("role-audit-failure-target");
        doThrow(new DataAccessResourceFailureException("simulated audit failure"))
                .when(auditServiceSpy).record(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> roleService.grantRole(admin.getId(), target.getId(), "TEACHER"))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(target.getId()))
                .extracting(UserRole::getRoleCode).doesNotContain(RoleCode.TEACHER);
    }

    @Test
    void nonAdminAndUnauthenticatedRequestsAreRejected() throws Exception {
        User learner = createUser("role-not-admin");
        grantFixture(learner, RoleCode.LEARNER, null);
        User target = createUser("role-not-admin-target");

        mockMvc.perform(post("/api/admin/users/{id}/roles", target.getId())
                        .header("Authorization", bearer(learner, RoleCode.LEARNER))
                        .contentType("application/json")
                        .content("{\"roleCode\":\"TEACHER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", target.getId(), "TEACHER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @Test
    void deleteValidatesRoleCodeAndUsesUserNotFoundAndRoleNotGrantedCodes() throws Exception {
        User admin = createUser("role-delete-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User target = createUser("role-delete-target");
        grantFixture(target, RoleCode.LEARNER, null);
        String adminBearer = bearer(admin, RoleCode.ADMIN);

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", target.getId(), "NOT_A_ROLE")
                        .header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", Long.MAX_VALUE, "TEACHER")
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));
        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", target.getId(), "TEACHER")
                        .header("Authorization", adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ROLE_NOT_GRANTED"));
    }

    @Test
    void AT69_selfAdminRevokeIsForbiddenAndLastActiveAdminCannotBeRemoved() {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
        User admin = createUser("role-last-admin");
        grantFixture(admin, RoleCode.ADMIN, null);

        assertThatThrownBy(() -> roleService.revokeRole(admin.getId(), admin.getId(), "ADMIN"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode())
                        .isEqualTo(ErrorCode.SELF_ROLE_REVOKE));

        User caller = createUser("role-last-caller");
        grantFixture(caller, RoleCode.LEARNER, null);
        assertThatThrownBy(() -> roleService.revokeRole(caller.getId(), admin.getId(), "ADMIN"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode())
                        .isEqualTo(ErrorCode.REVOKE_LAST_ADMIN));
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isEqualTo(1);
        }
    }

    @Test
    void AT68_revocationWritesMarkerBeforeCommitAndRedisFailureRollsBackRoleAndAudit() throws Exception {
        User admin = createUser("role-marker-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User target = createUser("role-marker-target");
        grantFixture(target, RoleCode.LEARNER, null);
        grantFixture(target, RoleCode.TEACHER, admin.getId());
        doThrow(new DataAccessResourceFailureException("simulated Redis failure"))
                .when(invalidationServiceSpy).markRevoked(anyLong(), anyLong());

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", target.getId(), "TEACHER")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(target.getId()))
                .extracting(UserRole::getRoleCode).contains(RoleCode.TEACHER);
        assertThat(auditCount(admin.getId(), target.getId(), "ROLE_REVOKE")).isZero();
    }

    @Test
    void AT68_successfulRevokeInvalidatesOldAccessTokenAndRefreshReadsCurrentRoles() throws Exception {
        User admin = createUser("role-refresh-admin");
        grantFixture(admin, RoleCode.ADMIN, null);
        User target = createUser("role-refresh-target");
        grantFixture(target, RoleCode.LEARNER, null);
        grantFixture(target, RoleCode.TEACHER, admin.getId());
        UUID sessionId = UUID.randomUUID();
        String oldAccess = jwtService.issueAccessToken(target.getId(), List.of("LEARNER", "TEACHER"), true,
                sessionId);
        String rawRefresh = "refresh-" + UUID.randomUUID();
        userToken(target, sessionId, rawRefresh);

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{roleCode}", target.getId(), "TEACHER")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                        .header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"));

        JsonNode refreshed = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", rawRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(refreshed.path("user").path("roles").toString()).isEqualTo("[\"LEARNER\"]");
        assertThat(auditCount(admin.getId(), target.getId(), "ROLE_REVOKE")).isEqualTo(1);
    }

    @Test
    void concurrentOppositeAdminRevocationsCannotRemoveTheLastActiveAdmin() throws Exception {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
        User first = createUser("role-concurrent-first");
        User second = createUser("role-concurrent-second");
        grantFixture(first, RoleCode.ADMIN, null);
        grantFixture(second, RoleCode.ADMIN, first.getId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = executor.submit(() -> concurrentRevoke(
                    first.getId(), second.getId(), ready, start, unexpected));
            Future<String> secondResult = executor.submit(() -> concurrentRevoke(
                    second.getId(), first.getId(), ready, start, unexpected));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> results = List.of(firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));

            assertThat(unexpected.get()).isNull();
            assertThat(results).containsExactlyInAnyOrder("SUCCESS", ErrorCode.REVOKE_LAST_ADMIN.name());
            assertThat(userRepository.countActiveAdmins()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        }
    }

    private String concurrentRevoke(long actorId, long targetId, CountDownLatch ready,
            CountDownLatch start, AtomicReference<Throwable> unexpected) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent revoke start.");
        }
        try {
            roleService.revokeRole(actorId, targetId, "ADMIN");
            return "SUCCESS";
        } catch (ApiException exception) {
            if (exception.getCode() == ErrorCode.REVOKE_LAST_ADMIN) {
                return exception.getCode().name();
            }
            unexpected.set(exception);
            return exception.getCode().name();
        } catch (RuntimeException exception) {
            unexpected.set(exception);
            return "UNEXPECTED";
        }
    }

    private User createUser(String prefix) {
        User user = new User(prefix + "-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Role Test User");
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private void grantFixture(User user, RoleCode roleCode, Long grantedBy) {
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), roleCode, grantedBy));
    }

    private String bearer(User user, RoleCode roleCode) {
        return "Bearer " + jwtService.issueAccessToken(user.getId(), List.of(roleCode.name()), true, UUID.randomUUID());
    }

    private void userToken(User user, UUID sessionId, String rawRefresh) {
        jdbcTemplate.update("INSERT INTO auth.auth_tokens "
                        + "(user_id, token_type, token_hash, expires_at, session_id) VALUES (?, 'REFRESH', ?, ?, ?)",
                user.getId(), sha256(rawRefresh), java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), sessionId);
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private long auditCount(long actorId, long targetId, String action) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM shared.audit_logs "
                        + "WHERE actor_id = ? AND target_type = 'user' AND target_id = ? AND action = ?",
                Long.class, actorId, targetId, action);
    }

    private AdminRoleSnapshot isolateAdminRoles() {
        List<AdminRoleRow> existing = jdbcTemplate.query(
                "SELECT user_id, granted_by, granted_at FROM auth.user_roles WHERE role_code = 'ADMIN'",
                (resultSet, rowNum) -> new AdminRoleRow(resultSet.getLong("user_id"),
                        (Long) resultSet.getObject("granted_by"), resultSet.getTimestamp("granted_at")));
        jdbcTemplate.update("DELETE FROM auth.user_roles WHERE role_code = 'ADMIN'");
        return new AdminRoleSnapshot(existing);
    }

    private final class AdminRoleSnapshot implements AutoCloseable {
        private final List<AdminRoleRow> existing;

        private AdminRoleSnapshot(List<AdminRoleRow> existing) {
            this.existing = existing;
        }

        @Override
        public void close() {
            jdbcTemplate.update("DELETE FROM auth.user_roles WHERE role_code = 'ADMIN'");
            existing.forEach(row -> jdbcTemplate.update(
                    "INSERT INTO auth.user_roles (user_id, role_code, granted_by, granted_at) "
                            + "VALUES (?, 'ADMIN', ?, ?)",
                    row.userId(), row.grantedBy(), row.grantedAt()));
        }
    }

    private record AdminRoleRow(long userId, Long grantedBy, java.sql.Timestamp grantedAt) {
    }
}
