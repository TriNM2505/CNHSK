package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.LoginAttemptService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.SessionInvalidationMarker;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.junit.jupiter.api.extension.ExtendWith;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD = "CurrentPass123";
    private static final String NEW_PASSWORD = "NewPassword456";
    private static final String GENERIC_FORGOT_MESSAGE =
            "Nếu email có tài khoản trong hệ thống, hướng dẫn đặt lại mật khẩu sẽ được gửi.";
    private static final String CLIENT_IP = "192.0.2.170";
    private static final Pattern RESET_LINK_TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SessionInvalidationService invalidationService;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private StringRedisTemplate redisTemplate;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @MockitoSpyBean private SessionInvalidationService invalidationServiceSpy;
    @MockitoSpyBean private LoginAttemptService loginAttemptServiceSpy;
    @MockitoSpyBean private RateLimiter rateLimiterSpy;
    @MockitoSpyBean private UserRepository userRepositorySpy;
    @MockitoSpyBean private Clock clockSpy;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private UserRepository lockingRepositoryDelegate;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        lockingRepositoryDelegate = new JpaRepositoryFactory(entityManager).getRepository(UserRepository.class);
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void AT41_existingAndUnknownEmailsReceiveIdenticalAcceptedResponse() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        MvcResult existing = forgot(user.getEmail(), CLIENT_IP);
        MvcResult missing = forgot("missing-" + UUID.randomUUID() + "@example.test", "192.0.2.171");

        assertThat(existing.getResponse().getStatus()).isEqualTo(202);
        assertThat(missing.getResponse().getStatus()).isEqualTo(202);
        assertThat(objectMapper.readTree(existing.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(missing.getResponse().getContentAsString()));
        assertThat(objectMapper.readTree(existing.getResponse().getContentAsString()).fieldNames())
                .toIterable().containsExactly("message");
        assertThat(objectMapper.readTree(existing.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo(GENERIC_FORGOT_MESSAGE)
                .doesNotContain(user.getEmail());
        assertThat(mailMessages().path("messages")).hasSize(1);
    }

    @Test
    void AT42_createsHashedOneHourTokenAndSendsMailAfterCommit() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        Instant before = Instant.now();

        forgot(user.getEmail(), CLIENT_IP).getResponse().getStatus();

        List<AuthToken> tokens = resetTokens(user.getId());
        assertThat(tokens).hasSize(1);
        AuthToken token = tokens.get(0);
        assertThat(token.getTokenHash()).hasSize(64);
        assertThat(token.getExpiresAt()).isBetween(before.plus(Duration.ofMinutes(59)),
                Instant.now().plus(Duration.ofMinutes(61)));
        JsonNode mail = onlyMailDetail();
        assertThat(mail.path("Text").asText()).contains("/reset-password?token=");
        String rawToken = tokenFromMail(mail.path("Text").asText());
        assertThat(token.getTokenHash()).isEqualTo(new TokenHasher().hash(rawToken));
        assertThat(token.getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void AT43_bannedAccountGetsGenericAcceptedResponseWithoutMail() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        user.setBannedAt(Instant.now());
        user.setBanReason("policy");
        userRepository.saveAndFlush(user);

        MvcResult result = forgot(user.getEmail(), CLIENT_IP);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo(GENERIC_FORGOT_MESSAGE)
                .doesNotContain(user.getEmail());
        assertThat(resetTokens(user.getId())).isEmpty();
        assertThat(mailMessages().path("messages")).isEmpty();
    }

    @Test
    void AT44_tokensAreDistinctBase64UrlValuesAndOldUnusedTokensAreRevoked() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        forgot(user.getEmail(), "192.0.2.172");
        AuthToken first = resetTokens(user.getId()).get(0);
        String firstRaw = tokenFromMail(onlyMailDetail().path("Text").asText());
        forgot(user.getEmail(), "192.0.2.173");
        AuthToken second = resetTokens(user.getId()).stream()
                .filter(token -> token.getRevokedAt() == null).findFirst().orElseThrow();
        JsonNode messages = mailMessages().path("messages");
        assertThat(messages).hasSize(2);
        String secondRaw = messages.findValuesAsText("ID").stream()
                .map(this::mailDetail)
                .map(detail -> tokenFromMail(detail.path("Text").asText()))
                .filter(raw -> second.getTokenHash().equals(new TokenHasher().hash(raw)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No delivered email contained the second reset token."));

        AuthToken firstAfterRotation = authTokenRepository.findById(first.getId()).orElseThrow();
        assertThat(firstAfterRotation.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNull();
        assertThat(firstRaw).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(secondRaw).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(firstRaw);
        assertThat(second.getTokenHash()).isEqualTo(new TokenHasher().hash(secondRaw));
    }

    @Test
    void AT45_rateLimitsEmailAndIpWhileAlwaysReturningSameAcceptedMessage() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        List<String> bodies = new ArrayList<>();
        for (int attempt = 0; attempt < 4; attempt++) {
            MvcResult result = forgot(user.getEmail(), "192.0.2." + (180 + attempt));
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            bodies.add(result.getResponse().getContentAsString());
        }
        assertThat(bodies).containsOnly(bodies.get(0));
        assertThat(objectMapper.readTree(bodies.get(0)).path("message").asText())
                .isEqualTo(GENERIC_FORGOT_MESSAGE);
        assertThat(mailMessages().path("messages")).hasSize(3);

        String ip = "192.0.2.190";
        for (int attempt = 0; attempt < 11; attempt++) {
            MvcResult result = forgot("ip-limit-" + UUID.randomUUID() + "@example.test", ip);
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(bodies.get(0));
        }
        assertThat(redisTemplate.opsForValue().get("auth:forgot:ip:" + ip)).isEqualTo("11");
    }

    @Test
    void redisRateLimitFailureFailsClosedButReturnsSameGenericResponse(CapturedOutput output) throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        doThrow(new DataAccessResourceFailureException("simulated Redis outage"))
                .when(rateLimiterSpy).increment(anyString(), any(Duration.class));

        MvcResult result = forgot(user.getEmail(), CLIENT_IP);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo(GENERIC_FORGOT_MESSAGE)
                .doesNotContain(user.getEmail());
        assertThat(resetTokens(user.getId())).isEmpty();
        assertThat(mailMessages().path("messages")).isEmpty();
        assertThat(output.getOut() + output.getErr()).contains("failing closed")
                .doesNotContain(user.getEmail(), CLIENT_IP, "simulated Redis outage");
    }

    @Test
    void AT46_validResetConsumesTokenRevokesEveryRefreshAndAllowsNewPasswordLogin() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String resetRaw = createResetToken(user, "reset-valid-" + UUID.randomUUID());
        JsonNode oldSession = mobileLogin(user.getEmail(), CURRENT_PASSWORD);
        mobileLogin(user.getEmail(), CURRENT_PASSWORD);

        mockMvc.perform(resetRequest(resetRaw, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(resetToken(user.getId(), resetRaw).getRevokedAt()).isNotNull();
        assertThat(activeRefreshCount(user.getId())).isZero();
        String changedHash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(new BCryptPasswordEncoder(12).matches(CURRENT_PASSWORD, changedHash)).isFalse();
        assertThat(new BCryptPasswordEncoder(12).matches(NEW_PASSWORD, changedHash)).isTrue();
        assertThat(invalidationService.findMarker(user.getId()))
                .contains(SessionInvalidationMarker.revoked(invalidationService.findMarker(user.getId())
                        .orElseThrow().invalidBeforeMs()));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer "
                        + oldSession.path("accessToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"));
        mockMvc.perform(loginRequest(user.getEmail(), NEW_PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void AT47_reusedExpiredAndUnknownResetTokensReturn400() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-reuse-" + UUID.randomUUID());
        mockMvc.perform(resetRequest(raw, NEW_PASSWORD, NEW_PASSWORD)).andExpect(status().isNoContent());
        mockMvc.perform(resetRequest(raw, "AnotherPass789", "AnotherPass789"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("INVALID_RESET_TOKEN"));
        mockMvc.perform(resetRequest("unknown-" + UUID.randomUUID(), NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("INVALID_RESET_TOKEN"));
        String expired = createResetToken(user, "expired-" + UUID.randomUUID());
        AuthToken expiredRow = resetToken(user.getId(), expired);
        expiredRow.setExpiresAt(Instant.now().minusSeconds(1));
        authTokenRepository.saveAndFlush(expiredRow);
        mockMvc.perform(resetRequest(expired, "AnotherPass789", "AnotherPass789"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void AT50_samePasswordReturns422AndDoesNotConsumeResetToken() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-same-" + UUID.randomUUID());

        mockMvc.perform(resetRequest(raw, CURRENT_PASSWORD, CURRENT_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("PASSWORD_UNCHANGED"));

        assertThat(resetToken(user.getId(), raw).getRevokedAt()).isNull();
    }

    @Test
    void invalidPasswordAndConfirmationAreValidationErrors() throws Exception {
        mockMvc.perform(resetRequest("token", "abcdefgh", "different"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void databaseChangesCommitEvenWhenRedisMarkerWriteFails() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-marker-failure-" + UUID.randomUUID());
        JsonNode session = mobileLogin(user.getEmail(), CURRENT_PASSWORD);
        doThrow(new DataAccessResourceFailureException("simulated Redis outage"))
                .when(invalidationServiceSpy).markRevoked(anyLong(), anyLong());

        mockMvc.perform(resetRequest(raw, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").exists());

        assertThat(resetToken(user.getId(), raw).getRevokedAt()).isNotNull();
        assertThat(activeRefreshCount(user.getId())).isZero();
        String changedHash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(new BCryptPasswordEncoder(12).matches(CURRENT_PASSWORD, changedHash)).isFalse();
        assertThat(new BCryptPasswordEncoder(12).matches(NEW_PASSWORD, changedHash)).isTrue();
        assertThat(session.path("refreshToken").asText()).isNotBlank();
    }

    @Test
    void databaseChangesCommitEvenWhenRedisLoginCleanupFails() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-cleanup-failure-" + UUID.randomUUID());
        doThrow(new DataAccessResourceFailureException("simulated Redis outage"))
                .when(loginAttemptServiceSpy).clearAllAfterPasswordReset(user.getEmail());

        mockMvc.perform(resetRequest(raw, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"));

        assertThat(resetToken(user.getId(), raw).getRevokedAt()).isNotNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPasswordHash())
                .isNotEqualTo(new BCryptPasswordEncoder(12).encode(CURRENT_PASSWORD));
        assertThat(invalidationService.findMarker(user.getId())).isPresent();
    }

    @Test
    void twoConcurrentResetsWithSameTokenCanOnlyConsumeItOnce() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-concurrent-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentReset(ready, start, raw, NEW_PASSWORD));
            Future<Integer> second = executor.submit(() -> concurrentReset(ready, start, raw, "OtherPassword789"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(statuses).containsExactlyInAnyOrder(204, 400);
            assertThat(resetToken(user.getId(), raw).getRevokedAt()).isNotNull();
            assertThat(activeRefreshCount(user.getId())).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void loginWithOldPasswordWaitingBehindCommittedResetCannotReceiveSession() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-login-race-" + UUID.randomUUID());
        AtomicReference<Thread> resetThread = new AtomicReference<>();
        AtomicReference<Thread> loginThread = new AtomicReference<>();
        CountDownLatch resetHasUserLock = new CountDownLatch(1);
        CountDownLatch allowReset = new CountDownLatch(1);
        CountDownLatch loginReachedLock = new CountDownLatch(1);
        AtomicLong loginBackendPid = new AtomicLong();

        doAnswer(invocation -> {
            Thread current = Thread.currentThread();
            if (current == loginThread.get()) {
                loginBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                loginReachedLock.countDown();
            }
            if (current == resetThread.get()) {
                Optional<User> result = lockingRepositoryDelegate.findByIdForUpdate(user.getId());
                resetHasUserLock.countDown();
                if (!allowReset.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out holding reset user lock.");
                }
                return result;
            }
            return lockingRepositoryDelegate.findByIdForUpdate(user.getId());
        }).when(userRepositorySpy).findByIdForUpdate(user.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> reset = executor.submit(() -> {
                resetThread.set(Thread.currentThread());
                return mockMvc.perform(resetRequest(raw, NEW_PASSWORD, NEW_PASSWORD)).andReturn();
            });
            assertThat(resetHasUserLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> login = executor.submit(() -> {
                loginThread.set(Thread.currentThread());
                return mockMvc.perform(loginRequest(user.getEmail(), CURRENT_PASSWORD)).andReturn();
            });
            assertThat(loginReachedLock.await(10, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock(loginBackendPid.get());
            allowReset.countDown();

            assertThat(reset.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            MvcResult loginResult = login.get(30, TimeUnit.SECONDS);
            assertThat(loginResult.getResponse().getStatus()).isEqualTo(401);
            assertThat(objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .path("error_code").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(activeRefreshCount(user.getId())).isZero();
        } finally {
            allowReset.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void newPasswordLoginInCommitToMarkerGapIsKnownToHaveItsAccessTokenRevoked() throws Exception {
        Instant fixedNow = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        doReturn(fixedNow).when(clockSpy).instant();
        User user = createUser(CURRENT_PASSWORD);
        String raw = createResetToken(user, "reset-marker-gap-" + UUID.randomUUID());
        CountDownLatch markerReached = new CountDownLatch(1);
        CountDownLatch allowMarker = new CountDownLatch(1);
        doAnswer(invocation -> {
            markerReached.countDown();
            if (!allowMarker.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out releasing session marker write.");
            }
            return invocation.callRealMethod();
        }).when(invalidationServiceSpy).markRevoked(anyLong(), anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> reset = executor.submit(
                    () -> mockMvc.perform(resetRequest(raw, NEW_PASSWORD, NEW_PASSWORD)).andReturn());
            assertThat(markerReached.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(resetToken(user.getId(), raw).getRevokedAt()).isNotNull();
            assertThat(activeRefreshCount(user.getId())).isZero();

            JsonNode newSession = objectMapper.readTree(mockMvc.perform(loginRequest(user.getEmail(), NEW_PASSWORD))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            allowMarker.countDown();
            assertThat(reset.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer "
                            + newSession.path("accessToken").asText()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"));
            assertThat(activeRefreshCount(user.getId())).isEqualTo(1);
        } finally {
            allowMarker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void resetAndRefreshRaceNeverLeavesAnActiveRefreshToken() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String resetRaw = createResetToken(user, "reset-refresh-race-" + UUID.randomUUID());
        JsonNode session = mobileLogin(user.getEmail(), CURRENT_PASSWORD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> reset = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(resetRequest(resetRaw, NEW_PASSWORD, NEW_PASSWORD))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> refresh = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/auth/refresh").header("X-Client-Type", "MOBILE")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "refreshToken", session.path("refreshToken").asText()))))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(reset.get(30, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(refresh.get(30, TimeUnit.SECONDS)).isIn(200, 401);
            assertThat(activeRefreshCount(user.getId())).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void resetAndLogoutAllRaceNeverLeavesAnActiveRefreshToken() throws Exception {
        User user = createUser(CURRENT_PASSWORD);
        String resetRaw = createResetToken(user, "reset-logout-race-" + UUID.randomUUID());
        JsonNode session = mobileLogin(user.getEmail(), CURRENT_PASSWORD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> reset = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(resetRequest(resetRaw, NEW_PASSWORD, NEW_PASSWORD))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> logout = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/auth/logout-all")
                                .header("Authorization", "Bearer " + session.path("accessToken").asText()))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(reset.get(30, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(logout.get(30, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(activeRefreshCount(user.getId())).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int concurrentReset(CountDownLatch ready, CountDownLatch start, String token, String password)
            throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(resetRequest(token, password, password)).andReturn().getResponse().getStatus();
    }

    private MvcResult forgot(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resetRequest(
            String token, String password, String confirmation) throws Exception {
        return post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", token, "newPassword", password, "confirmPassword", confirmation)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password) throws Exception {
        return post("/api/auth/login").with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                .header("X-Client-Type", "MOBILE").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
    }

    private JsonNode mobileLogin(String email, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private User createUser(String password) {
        User user = userRepository.saveAndFlush(new User("reset-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode(password), "Reset Test"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private String createResetToken(User user, String rawToken) {
        authTokenRepository.saveAndFlush(new AuthToken(user.getId(), TokenType.RESET_PASSWORD,
                new TokenHasher().hash(rawToken), Instant.now().plus(Duration.ofHours(1))));
        return rawToken;
    }

    private List<AuthToken> resetTokens(long userId) {
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(userId) && token.getTokenType() == TokenType.RESET_PASSWORD)
                .toList();
    }

    private AuthToken resetToken(long userId, String rawToken) {
        String hash = new TokenHasher().hash(rawToken);
        return resetTokens(userId).stream().filter(token -> token.getTokenHash().equals(hash))
                .findFirst().orElseThrow();
    }

    private int activeRefreshCount(long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth.auth_tokens "
                + "WHERE user_id = ? AND token_type = 'REFRESH' AND revoked_at IS NULL", Integer.class, userId);
    }

    private JsonNode mailMessages() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode onlyMailDetail() throws Exception {
        JsonNode messages = mailMessages().path("messages");
        assertThat(messages).hasSize(1);
        return mailDetail(messages.get(0).path("ID").asText());
    }

    private JsonNode mailDetail(String id) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading Mailpit message detail.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read Mailpit message detail.", exception);
        }
    }

    private String tokenFromMail(String body) {
        Matcher matcher = RESET_LINK_TOKEN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void awaitDatabaseLock(long backendPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'",
                    Integer.class, backendPid);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL user-row lock.");
    }
}
