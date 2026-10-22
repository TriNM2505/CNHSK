package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordChangeIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_PASSWORD = "OldPassword123";
    private static final String NEW_PASSWORD = "NewPassword456";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuditService auditService;

    @Test
    void AT48_wrongCurrentPasswordReturns422AndDoesNotChangePasswordOrSessions() throws Exception {
        User user = createUser();
        Session current = login(user);

        changePassword(current.accessToken(), "WrongPassword123", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("CURRENT_PASSWORD_INCORRECT"));

        assertThat(passwordMatches(user.getId(), OLD_PASSWORD)).isTrue();
        assertThat(findRefresh(current.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void AT49_passwordChangeKeepsCurrentSessionAndRevokesOtherSessionRefreshTokens() throws Exception {
        User user = createUser();
        Session current = login(user);
        Session other = login(user);
        assertThat(current.sessionId()).isNotEqualTo(other.sessionId());

        changePassword(current.accessToken(), OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(passwordMatches(user.getId(), NEW_PASSWORD)).isTrue();
        assertThat(findRefresh(current.refreshToken()).getRevokedAt()).isNull();
        assertThat(findRefresh(other.refreshToken()).getRevokedAt()).isNotNull();

        String rotatedCurrent = refresh(current.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(rotatedCurrent).path("refreshToken").asText()).isNotBlank();
        refresh(other.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));

        // No FR-092 marker is written by password change: other access JWTs can expire naturally.
        mockMvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void AT50_newPasswordMatchingCurrentPasswordReturnsPasswordUnchanged() throws Exception {
        User user = createUser();
        Session current = login(user);

        changePassword(current.accessToken(), OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("PASSWORD_UNCHANGED"));
        assertThat(passwordMatches(user.getId(), OLD_PASSWORD)).isTrue();
    }

    @Test
    void webCookieSessionCanChangePasswordWithExistingCsrfProtection() throws Exception {
        User user = createUser();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", user.getEmail(), "password", OLD_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie("access_token");
        Cookie refresh = loginResult.getResponse().getCookie("refresh_token");
        Cookie xsrf = loginResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/me/password")
                        .cookie(access, refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "currentPassword", OLD_PASSWORD,
                                "newPassword", NEW_PASSWORD,
                                "confirmPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());
        assertThat(passwordMatches(user.getId(), NEW_PASSWORD)).isTrue();
    }

    @Test
    void missingActiveRefreshForSidReturnsSessionRevokedWithoutChangingPassword() throws Exception {
        User user = createUser();
        Session current = login(user);
        AuthToken refresh = findRefresh(current.refreshToken());
        refresh.setRevokedAt(java.time.Instant.now());
        authTokenRepository.saveAndFlush(refresh);

        changePassword(current.accessToken(), OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"));
        assertThat(passwordMatches(user.getId(), OLD_PASSWORD)).isTrue();
    }

    @Test
    void concurrentRefreshAndPasswordChangePreserveCurrentSession() throws Exception {
        User user = createUser();
        Session current = login(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> refreshFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return refresh(current.refreshToken()).andReturn();
            });
            Future<MvcResult> changeFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return changePassword(current.accessToken(), OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andReturn();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(refreshFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(changeFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(passwordMatches(user.getId(), NEW_PASSWORD)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? AND token_type = 'REFRESH' "
                            + "AND session_id = ? AND revoked_at IS NULL AND expires_at > now()",
                    Integer.class, user.getId(), current.sessionId())).isGreaterThanOrEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentOldPasswordLoginCannotCreateSurvivingSessionAfterPasswordChange() throws Exception {
        User user = createUser();
        Session current = login(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> loginFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return loginRequest(user, OLD_PASSWORD);
            });
            Future<MvcResult> changeFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return changePassword(current.accessToken(), OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andReturn();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult loginResult = loginFuture.get(30, TimeUnit.SECONDS);
            assertThat(changeFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(passwordMatches(user.getId(), NEW_PASSWORD)).isTrue();
            if (loginResult.getResponse().getStatus() == 200) {
                JsonNode issued = objectMapper.readTree(loginResult.getResponse().getContentAsString());
                String issuedRefresh = issued.path("refreshToken").asText();
                assertThat(findRefresh(issuedRefresh).getRevokedAt()).isNotNull();
            } else {
                assertThat(loginResult.getResponse().getStatus()).isEqualTo(401);
            }
            assertThat(findRefresh(current.refreshToken()).getRevokedAt()).isNull();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentOtherSessionRefreshAndPasswordChangeNeverLeavesOtherRefreshActive() throws Exception {
        User user = createUser();
        Session current = login(user);
        Session other = login(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> refreshFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return refresh(other.refreshToken()).andReturn();
            });
            Future<MvcResult> changeFuture = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                return changePassword(current.accessToken(), OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).andReturn();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult refreshResult = refreshFuture.get(30, TimeUnit.SECONDS);
            assertThat(List.of(200, 401)).contains(refreshResult.getResponse().getStatus());
            assertThat(changeFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(passwordMatches(user.getId(), NEW_PASSWORD)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? AND token_type = 'REFRESH' "
                            + "AND session_id = ? AND revoked_at IS NULL",
                    Integer.class, user.getId(), other.sessionId())).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? AND token_type = 'REFRESH' "
                            + "AND session_id = ? AND revoked_at IS NULL",
                    Integer.class, user.getId(), current.sessionId())).isGreaterThanOrEqualTo(1);
            if (refreshResult.getResponse().getStatus() == 200) {
                String issued = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                        .path("refreshToken").asText();
                assertThat(findRefresh(issued).getRevokedAt()).isNotNull();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private User createUser() {
        User user = userRepository.saveAndFlush(new User("password-change-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode(OLD_PASSWORD), "Password Change User"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private Session login(User user) throws Exception {
        MvcResult result = loginRequest(user, OLD_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String access = body.path("accessToken").asText();
        String refresh = body.path("refreshToken").asText();
        String sid = jwtService.verifyAccessToken(access).get("sid", String.class);
        assertThat(findRefresh(refresh).getSessionId().toString()).isEqualTo(sid);
        return new Session(access, refresh, UUID.fromString(sid));
    }

    private MvcResult loginRequest(User user, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", user.getEmail(), "password", password))))
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(
            String accessToken, String current, String next, String confirmation) throws Exception {
        return mockMvc.perform(post("/api/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "currentPassword", current, "newPassword", next, "confirmPassword", confirmation))));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String rawToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", rawToken))));
    }

    private AuthToken findRefresh(String rawToken) {
        String hash = new TokenHasher().hash(rawToken);
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getTokenType() == TokenType.REFRESH && token.getTokenHash().equals(hash))
                .findFirst().orElseThrow();
    }

    private boolean passwordMatches(long userId, String password) {
        return new BCryptPasswordEncoder(12).matches(
                password, userRepository.findById(userId).orElseThrow().getPasswordHash());
    }

    private static void awaitStart(CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request did not start before timeout.");
        }
    }

    private record Session(String accessToken, String refreshToken, UUID sessionId) {
    }
}
