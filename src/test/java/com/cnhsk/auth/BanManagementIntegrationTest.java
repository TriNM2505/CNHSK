package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.cnhsk.auth.dto.LoginResponse;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.BanService;
import com.cnhsk.auth.service.RefreshTokenService;
import com.cnhsk.auth.service.RoleService;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationMarker;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
class BanManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "BanTestPass123";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final TokenHasher TOKEN_HASHER = new TokenHasher();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private BanService banService;
    @Autowired private RoleService roleService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JwtService jwtService;
    @Autowired private SessionInvalidationService invalidationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @MockitoSpyBean private AuditService auditServiceSpy;
    @MockitoSpyBean private SessionInvalidationService invalidationServiceSpy;
    @MockitoSpyBean private JwtService jwtServiceSpy;

    @Test
    void AT22_AT73_banRevokesEveryRefreshTokenWritesAuditAndBlocksOldAccess() throws Exception {
        User admin = createUser("ban-success-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-success-target");
        grant(target, RoleCode.LEARNER, null);
        String oldAccess = accessToken(target, List.of("LEARNER"));
        addRefresh(target, "ban-refresh-one");
        addRefresh(target, "ban-refresh-two");

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"reason\":\"  spams  \"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        User banned = userRepository.findById(target.getId()).orElseThrow();
        assertThat(banned.getBannedBy()).isEqualTo(admin.getId());
        assertThat(banned.getBanReason()).isEqualTo("spams");
        assertThat(banned.getBannedAt()).isNotNull();
        assertThat(activeRefreshCount(target.getId())).isZero();
        assertAuditState(admin.getId(), target.getId(), "USER_BAN", null, "spams");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.banReason").value("spams"));
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.banReason").value("spams"));
    }

    @Test
    void AT73_unbanCommitsAuditTransitionsMarkerAndDoesNotRestoreRefreshTokens() throws Exception {
        User admin = createUser("unban-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("unban-target");
        grant(target, RoleCode.LEARNER, null);
        addRefresh(target, "unban-old-refresh");
        banService.ban(admin.getId(), target.getId(), "abuse");
        SessionInvalidationMarker bannedMarker = invalidationService.findMarker(target.getId()).orElseThrow();
        assertThat(activeRefreshCount(target.getId())).isZero();

        mockMvc.perform(delete("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        User unbanned = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unbanned.getBannedAt()).isNull();
        assertThat(unbanned.getBannedBy()).isNull();
        assertThat(unbanned.getBanReason()).isNull();
        assertThat(activeRefreshCount(target.getId())).isZero();
        assertThat(invalidationService.findMarker(target.getId()))
                .contains(SessionInvalidationMarker.revoked(bannedMarker.invalidBeforeMs()));
        assertAuditState(admin.getId(), target.getId(), "USER_UNBAN", "abuse", null);

        awaitEpochMillis(bannedMarker.invalidBeforeMs());
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").header("X-Client-Type", "MOBILE")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"unban-old-refresh\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void duplicateBanAndUnbanAreNoOpsAndDoNotDuplicateAudit() throws Exception {
        User admin = createUser("ban-idempotent-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-idempotent-target");
        grant(target, RoleCode.LEARNER, null);

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"original reason\"}"))
                .andExpect(status().isNoContent());
        User firstBan = userRepository.findById(target.getId()).orElseThrow();
        Instant originalBannedAt = firstBan.getBannedAt();
        Long originalBannedBy = firstBan.getBannedBy();
        String originalReason = firstBan.getBanReason();

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"replacement reason\"}"))
                .andExpect(status().isNoContent());
        User afterDuplicateBan = userRepository.findById(target.getId()).orElseThrow();
        assertThat(afterDuplicateBan.getBannedAt()).isEqualTo(originalBannedAt);
        assertThat(afterDuplicateBan.getBannedBy()).isEqualTo(originalBannedBy);
        assertThat(afterDuplicateBan.getBanReason()).isEqualTo(originalReason);
        assertThat(auditCount(admin.getId(), target.getId(), "USER_BAN")).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(auditCount(admin.getId(), target.getId(), "USER_UNBAN")).isEqualTo(1);
    }

    @Test
    void banValidatesTrimmedReasonBoundariesAndErrorCases() throws Exception {
        User admin = createUser("ban-validation-admin");
        grant(admin, RoleCode.ADMIN, null);
        User fiveChars = createUser("ban-five-char-target");
        User fiveHundredChars = createUser("ban-five-hundred-target");

        mockMvc.perform(post("/api/admin/users/{id}/ban", fiveChars.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"  abcde  \"}"))
                .andExpect(status().isNoContent());
        String maxReason = "x".repeat(500);
        mockMvc.perform(post("/api/admin/users/{id}/ban", fiveHundredChars.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("reason", "  " + maxReason + "  "))))
                .andExpect(status().isNoContent());

        User tooShort = createUser("ban-short-target");
        User tooLong = createUser("ban-long-target");
        mockMvc.perform(post("/api/admin/users/{id}/ban", tooShort.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"  ab  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/admin/users/{id}/ban", tooLong.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("reason", "x".repeat(501)))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/admin/users/{id}/ban", admin.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"self ban reason\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("SELF_BAN"));
        mockMvc.perform(post("/api/admin/users/{id}/ban", Long.MAX_VALUE)
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"missing target\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));
        mockMvc.perform(delete("/api/admin/users/{id}/ban", Long.MAX_VALUE)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));
    }

    @Test
    void banEndpointsRequireAdminRole() throws Exception {
        User learner = createUser("ban-security-learner");
        grant(learner, RoleCode.LEARNER, null);
        User target = createUser("ban-security-target");

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", "Bearer " + accessToken(learner, List.of("LEARNER")))
                        .contentType("application/json").content("{\"reason\":\"not admin\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/admin/users/{id}/ban", target.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @Test
    void AT74_banningLastActiveAdminIsRejected() throws Exception {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
            User admin = createUser("ban-last-admin");
            grant(admin, RoleCode.ADMIN, null);
            User actor = createUser("ban-last-actor");
            mockMvc.perform(post("/api/admin/users/{id}/ban", admin.getId())
                            .header("Authorization", bearer(actor))
                            .contentType("application/json")
                            .content("{\"reason\":\"last admin\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("BAN_LAST_ADMIN"));
            assertThat(userRepository.findById(admin.getId()).orElseThrow().getBannedAt()).isNull();
        }
    }

    @Test
    void redisFailureDuringBanReturns500AndRollsBackUserTokensAndAudit() throws Exception {
        User admin = createUser("ban-redis-failure-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-redis-failure-target");
        grant(target, RoleCode.LEARNER, null);
        addRefresh(target, "ban-redis-failure-refresh");
        doThrow(new DataAccessResourceFailureException("simulated Redis outage"))
                .when(invalidationServiceSpy).markBanned(anyLong(), anyLong(), anyString());

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"redis failure\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getBannedAt()).isNull();
        assertThat(activeRefreshCount(target.getId())).isEqualTo(1);
        assertThat(auditCount(admin.getId(), target.getId(), "USER_BAN")).isZero();
    }

    @Test
    void redisFailureAfterUnbanCommitWarnsWithoutPiiAndStillReturns204(CapturedOutput output) throws Exception {
        User admin = createUser("unban-redis-failure-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("unban-redis-failure-target");
        grant(target, RoleCode.LEARNER, null);
        banService.ban(admin.getId(), target.getId(), "private ban reason");
        doThrow(new DataAccessResourceFailureException("simulated Redis outage"))
                .when(invalidationServiceSpy).markUnbanned(target.getId());

        mockMvc.perform(delete("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getBannedAt()).isNull();
        assertThat(auditCount(admin.getId(), target.getId(), "USER_UNBAN")).isEqualTo(1);
        assertThat(output.getAll()).contains("Session marker update failed after committed account unban")
                .doesNotContain(target.getEmail(), "private ban reason");
    }

    @Test
    void auditFailureRollsBackBanAndRefreshRevocation() throws Exception {
        User admin = createUser("ban-audit-failure-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-audit-failure-target");
        grant(target, RoleCode.LEARNER, null);
        addRefresh(target, "ban-audit-failure-refresh");
        doAnswer(invocation -> {
            if ("USER_BAN".equals(invocation.getArgument(1))) {
                throw new DataAccessResourceFailureException("simulated audit failure");
            }
            return invocation.callRealMethod();
        }).when(auditServiceSpy).record(anyLong(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/admin/users/{id}/ban", target.getId())
                        .header("Authorization", bearer(admin)).contentType("application/json")
                        .content("{\"reason\":\"audit failure\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getBannedAt()).isNull();
        assertThat(activeRefreshCount(target.getId())).isEqualTo(1);
        assertThat(auditCount(admin.getId(), target.getId(), "USER_BAN")).isZero();
    }

    @Test
    void sameMillisecondBanCutoffRejectsTokenIssuedAtThatMillisecond() throws Exception {
        User target = createUser("ban-same-millisecond-target");
        grant(target, RoleCode.LEARNER, null);
        String access = accessToken(target, List.of("LEARNER"));
        long issuedAtMs = ((Number) jwtService.verifyAccessToken(access).get("iatMs")).longValue();
        invalidationService.markBanned(target.getId(), issuedAtMs + 1, "same millisecond ban");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"));

        redisTemplate.delete("auth:invalid-before:" + target.getId());
        invalidationService.markRevoked(target.getId(), issuedAtMs);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void banAndRefreshSerializeWhenRefreshGetsUserLockFirst() throws Exception {
        User admin = createUser("ban-refresh-first-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-refresh-first-target");
        grant(target, RoleCode.LEARNER, null);
        String rawRefresh = "ban-refresh-first-" + UUID.randomUUID();
        addRefresh(target, rawRefresh);
        CountDownLatch refreshIssuingAccess = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        doAnswer(invocation -> {
            String result = (String) invocation.callRealMethod();
            if (target.getId().equals(invocation.getArgument(0))
                    && Thread.currentThread().getName().equals("t25-refresh-first")) {
                refreshIssuingAccess.countDown();
                await(releaseRefresh, "release refresh transaction");
            }
            return result;
        }).when(jwtServiceSpy).issueAccessToken(anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(UUID.class));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshTokenService.RefreshResult> refresh = executor.submit(() -> {
                Thread.currentThread().setName("t25-refresh-first");
                return refreshTokenService.refresh(rawRefresh, "MOBILE");
            });
            assertThat(refreshIssuingAccess.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch banStarted = new CountDownLatch(1);
            Future<?> ban = executor.submit(() -> {
                Thread.currentThread().setName("t25-ban-second");
                banStarted.countDown();
                banService.ban(admin.getId(), target.getId(), "race refresh");
            });
            assertThat(banStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait("ban waiting for refresh-held user row");
            releaseRefresh.countDown();
            RefreshTokenService.RefreshResult rotated = refresh.get(15, TimeUnit.SECONDS);
            ban.get(15, TimeUnit.SECONDS);
            assertThat(rotated.response().accessToken()).isNotBlank();
            assertThat(activeRefreshCount(target.getId())).isZero();
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + rotated.response().accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"));
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void banAndRefreshSerializeWhenBanCommitsBeforeRefresh() throws Exception {
        User admin = createUser("ban-first-admin");
        grant(admin, RoleCode.ADMIN, null);
        User target = createUser("ban-first-target");
        grant(target, RoleCode.LEARNER, null);
        String rawRefresh = "ban-first-refresh-" + UUID.randomUUID();
        addRefresh(target, rawRefresh);
        CountDownLatch markerWritten = new CountDownLatch(1);
        CountDownLatch releaseBan = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (target.getId().equals(invocation.getArgument(0))
                    && Thread.currentThread().getName().equals("t25-ban-first")) {
                markerWritten.countDown();
                await(releaseBan, "release ban transaction");
            }
            return result;
        }).when(invalidationServiceSpy).markBanned(anyLong(), anyLong(), anyString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> ban = executor.submit(() -> {
                Thread.currentThread().setName("t25-ban-first");
                banService.ban(admin.getId(), target.getId(), "race refresh");
            });
            assertThat(markerWritten.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch refreshStarted = new CountDownLatch(1);
            Future<String> refresh = executor.submit(() -> {
                Thread.currentThread().setName("t25-refresh-second");
                refreshStarted.countDown();
                try {
                    refreshTokenService.refresh(rawRefresh, "MOBILE");
                    return "SUCCESS";
                } catch (ApiException exception) {
                    return exception.getCode().name();
                }
            });
            assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWait("refresh waiting for ban-held user row");
            releaseBan.countDown();
            ban.get(15, TimeUnit.SECONDS);
            assertThat(refresh.get(15, TimeUnit.SECONDS)).isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(activeRefreshCount(target.getId())).isZero();
        } finally {
            releaseBan.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentBanAndRoleRemovalNeverRemoveTheLastActiveAdmin() throws Exception {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
            User firstAdmin = createUser("ban-role-race-first");
            User secondAdmin = createUser("ban-role-race-second");
            User actor = createUser("ban-role-race-actor");
            grant(firstAdmin, RoleCode.ADMIN, null);
            grant(secondAdmin, RoleCode.ADMIN, firstAdmin.getId());

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> ban = executor.submit(() -> concurrentResult(ready, start,
                        () -> banService.ban(actor.getId(), firstAdmin.getId(), "admin race")));
                Future<String> revoke = executor.submit(() -> concurrentResult(ready, start,
                        () -> roleService.revokeRole(actor.getId(), secondAdmin.getId(), "ADMIN")));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                List<String> outcomes = List.of(ban.get(15, TimeUnit.SECONDS), revoke.get(15, TimeUnit.SECONDS));
                assertThat(outcomes).contains("SUCCESS");
                assertThat(outcomes.stream().filter(value -> value.endsWith("LAST_ADMIN"))).hasSize(1);
                assertThat(userRepository.countActiveAdmins()).isEqualTo(1);
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void concurrentBanAndAdminGrantPreserveAtLeastOneActiveAdmin() throws Exception {
        try (AdminRoleSnapshot ignored = isolateAdminRoles()) {
            User onlyAdmin = createUser("ban-grant-race-admin");
            User newAdmin = createUser("ban-grant-race-target");
            User actor = createUser("ban-grant-race-actor");
            grant(onlyAdmin, RoleCode.ADMIN, null);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> ban = executor.submit(() -> concurrentResult(ready, start,
                        () -> banService.ban(actor.getId(), onlyAdmin.getId(), "grant race")));
                Future<String> grant = executor.submit(() -> concurrentResult(ready, start,
                        () -> roleService.grantRole(actor.getId(), newAdmin.getId(), "ADMIN")));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                List<String> outcomes = List.of(ban.get(15, TimeUnit.SECONDS), grant.get(15, TimeUnit.SECONDS));
                assertThat(outcomes).contains("SUCCESS");
                assertThat(outcomes).allMatch(value -> value.equals("SUCCESS")
                        || value.equals(ErrorCode.BAN_LAST_ADMIN.name()));
                assertThat(userRepository.countActiveAdmins()).isGreaterThanOrEqualTo(1);
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private String concurrentResult(CountDownLatch ready, CountDownLatch start, Runnable action)
            throws InterruptedException {
        ready.countDown();
        await(start, "concurrent operation start");
        try {
            action.run();
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.getCode().name();
        }
    }

    private static void await(CountDownLatch latch, String operation) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to " + operation + ".");
        }
    }

    private static void awaitEpochMillis(long epochMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        while (System.currentTimeMillis() < epochMillis && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(100));
        }
        assertThat(System.currentTimeMillis()).isGreaterThanOrEqualTo(epochMillis);
    }

    private void awaitDatabaseLockWait(String operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND pid <> pg_backend_pid() "
                    + "AND state = 'active' AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
        throw new IllegalStateException("Timed out waiting for database lock while " + operation + ".");
    }

    private User createUser(String prefix) {
        User user = new User(prefix + "-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Ban Test User");
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private void grant(User user, RoleCode code, Long grantedBy) {
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), code, grantedBy));
    }

    private String bearer(User admin) {
        return "Bearer " + accessToken(admin, List.of("ADMIN"));
    }

    private String accessToken(User user, List<String> roles) {
        return jwtService.issueAccessToken(user.getId(), roles, true, UUID.randomUUID());
    }

    private void addRefresh(User user, String rawToken) {
        authTokenRepository.saveAndFlush(new AuthToken(user.getId(), TokenType.REFRESH, TOKEN_HASHER.hash(rawToken),
                Instant.now().plusSeconds(3600), UUID.randomUUID()));
    }

    private String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private long activeRefreshCount(long userId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM auth.auth_tokens "
                + "WHERE user_id = ? AND token_type = 'REFRESH' AND revoked_at IS NULL", Long.class, userId);
    }

    private long auditCount(long actorId, long targetId, String action) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM shared.audit_logs "
                + "WHERE actor_id = ? AND target_type = 'user' AND target_id = ? AND action = ?",
                Long.class, actorId, targetId, action);
    }

    private void assertAuditState(long actorId, long targetId, String action,
            String beforeReason, String afterReason) throws Exception {
        String detail = jdbcTemplate.queryForObject("SELECT detail::text FROM shared.audit_logs "
                + "WHERE actor_id = ? AND target_type = 'user' AND target_id = ? AND action = ?",
                String.class, actorId, targetId, action);
        JsonNode json = objectMapper.readTree(detail);
        if (beforeReason == null) {
            assertThat(json.path("before").path("bannedAt").isNull()).isTrue();
            assertThat(json.path("before").path("reason").isNull()).isTrue();
        } else {
            assertThat(json.path("before").path("bannedAt").isTextual()).isTrue();
            assertThat(json.path("before").path("reason").asText()).isEqualTo(beforeReason);
        }
        if (afterReason == null) {
            assertThat(json.path("after").path("reason").isNull()).isTrue();
            assertThat(json.path("after").path("bannedAt").isNull()).isTrue();
        } else {
            assertThat(json.path("after").path("bannedAt").isTextual()).isTrue();
            assertThat(json.path("after").path("reason").asText()).isEqualTo(afterReason);
        }
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
                            + "VALUES (?, 'ADMIN', ?, ?)", row.userId(), row.grantedBy(), row.grantedAt()));
        }
    }

    private record AdminRoleRow(long userId, Long grantedBy, java.sql.Timestamp grantedAt) {
    }
}
