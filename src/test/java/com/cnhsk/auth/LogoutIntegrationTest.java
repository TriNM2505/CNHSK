package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.minimum-idle=1"
})
@Import(LogoutIntegrationTest.FixedClockConfiguration.class)
class LogoutIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "LogoutPass123";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-21T12:00:00Z");
    private static final long FIXED_EPOCH_MILLI = FIXED_INSTANT.toEpochMilli();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private SessionInvalidationService invalidationService;
    @Autowired private CnhskProperties properties;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private AuthTokenRepository authTokenSpy;
    @MockitoSpyBean private UserRepository userRepositorySpy;
    @MockitoSpyBean private SessionInvalidationService invalidationServiceSpy;

    @Test
    void AT37_webLogoutRevokesOnlyCurrentRefreshAndExpiresMatchingCookies() throws Exception {
        User user = createUser();
        MockHttpSessionTokens firstSession = webLogin(user);
        MockHttpSessionTokens secondSession = webLogin(user);

        var response = mockMvc.perform(post("/api/auth/logout")
                        .cookie(firstSession.accessCookie(), firstSession.refreshCookie(), firstSession.xsrfCookie())
                        .header("X-XSRF-TOKEN", firstSession.xsrfCookie().getValue()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEmpty();
        List<String> setCookies = response.getHeaders("Set-Cookie");
        List<String> authCookies = setCookies.stream()
                .filter(header -> header.startsWith("access_token=") || header.startsWith("refresh_token="))
                .collect(Collectors.toList());
        assertThat(authCookies).hasSize(2);
        assertThat(authCookies).anySatisfy(header -> assertThat(header)
                .contains("access_token=").contains("Max-Age=0")
                .contains("Domain=" + properties.getCookie().getDomain()).contains("Path=/"));
        assertThat(authCookies).anySatisfy(header -> assertThat(header)
                .contains("refresh_token=").contains("Max-Age=0")
                .contains("Domain=" + properties.getCookie().getDomain()).contains("Path=/api/auth"));
        assertThat(findToken(firstSession.refreshToken()).getRevokedAt()).isNotNull();
        assertThat(findToken(secondSession.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void logoutIsIdempotentForMissingUnknownAndOtherUsersRefreshTokens() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        MobileSession session = mobileLogin(user);
        MobileSession otherSession = mobileLogin(otherUser);

        mobileLogout(user, session.accessToken(), otherSession.refreshToken())
                .andExpect(status().isNoContent());
        mobileLogout(user, session.accessToken(), "not-a-refresh-token")
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(findToken(session.refreshToken()).getRevokedAt()).isNull();
        assertThat(findToken(otherSession.refreshToken()).getRevokedAt()).isNull();

        var response = mobileLogout(user, session.accessToken(), session.refreshToken())
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(findToken(session.refreshToken()).getRevokedAt()).isNotNull();
        assertThat(findToken(otherSession.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void AT39_logoutAllRevokesAllRefreshTokensAndInvalidatesAccessTokensIssuedInSameSecond() throws Exception {
        User user = createUser();
        MobileSession first = mobileLogin(user);
        MobileSession second = mobileLogin(user);

        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        assertThat(activeRefreshTokenCount(user.getId())).isZero();
        JsonNode marker = objectMapper.readTree(redisTemplate.opsForValue()
                .get("auth:invalid-before:" + user.getId()));
        assertThat(marker.path("invalidBeforeMs").asLong()).isEqualTo(FIXED_EPOCH_MILLI + 1);
        assertThat(marker.path("reason").asText()).isEqualTo("REVOKED");
        assertThat(marker.has("banReason")).isFalse();
        Long ttl = redisTemplate.getExpire("auth:invalid-before:" + user.getId(), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(900L);

        assertAccessRevoked(first.accessToken());
        assertAccessRevoked(second.accessToken());
    }

    @Test
    void logoutAllWithNoActiveRefreshTokensStillWritesMarker() throws Exception {
        User user = createUser();
        MobileSession session = mobileLogin(user);
        authTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()) && token.getTokenType() == TokenType.REFRESH)
                .forEach(token -> token.setRevokedAt(FIXED_INSTANT));
        authTokenRepository.flush();

        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(activeRefreshTokenCount(user.getId())).isZero();
        assertAccessRevoked(session.accessToken());
    }

    @Test
    void redisMarkerFailureReturnsStructured500AfterRefreshRevocationCommitAndCanBeRetried() throws Exception {
        User user = createUser();
        MobileSession session = mobileLogin(user);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(invalidationServiceSpy).markRevoked(user.getId(), FIXED_EPOCH_MILLI + 1);

        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        assertThat(activeRefreshTokenCount(user.getId())).isZero();
        assertThat(invalidationService.findMarker(user.getId())).isEmpty();

        doCallRealMethod().when(invalidationServiceSpy)
                .markRevoked(user.getId(), FIXED_EPOCH_MILLI + 1);
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(invalidationService.findMarker(user.getId())).isPresent();
    }

    @Test
    void refreshStartedBeforeLogoutAllCannotLeaveSuccessorTokenActive() throws Exception {
        User user = createUser();
        MobileSession session = mobileLogin(user);
        AtomicBoolean pauseBeforeInsert = new AtomicBoolean(true);
        CountDownLatch beforeInsert = new CountDownLatch(1);
        CountDownLatch allowInsert = new CountDownLatch(1);
        AtomicReference<Thread> logoutThread = new AtomicReference<>();
        AtomicInteger logoutBackendPid = new AtomicInteger();
        CountDownLatch logoutReachedUserLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread() == logoutThread.get()) {
                logoutBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                logoutReachedUserLock.countDown();
            }
            User lockedUser = entityManager.find(User.class, invocation.getArgument(0),
                    LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(lockedUser);
        }).when(userRepositorySpy).findByIdForUpdate(anyLong());
        doAnswer(invocation -> {
            AuthToken token = invocation.getArgument(0);
            if (pauseBeforeInsert.get() && token.getTokenType() == TokenType.REFRESH
                    && token.getRevokedAt() == null) {
                beforeInsert.countDown();
                if (!allowInsert.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release refresh insert.");
                }
            }
            entityManager.persist(token);
            entityManager.flush();
            return token;
        }).when(authTokenSpy).saveAndFlush(any(AuthToken.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> refresh = executor.submit(() -> mobileRefresh(session.refreshToken()).andReturn());
            assertThat(beforeInsert.await(10, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> logout = executor.submit(() -> {
                logoutThread.set(Thread.currentThread());
                return mockMvc.perform(post("/api/auth/logout-all")
                                .header("Authorization", "Bearer " + session.accessToken()))
                        .andReturn();
            });
            assertThat(logoutReachedUserLock.await(10, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock(logoutBackendPid.get());
            allowInsert.countDown();

            MvcResult refreshResult = refresh.get(15, TimeUnit.SECONDS);
            MvcResult logoutResult = logout.get(15, TimeUnit.SECONDS);
            assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(logoutResult.getResponse().getStatus()).isEqualTo(204);
            JsonNode refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
            assertThat(activeRefreshTokenCount(user.getId())).isZero();
            assertAccessRevoked(refreshed.path("accessToken").asText());
        } finally {
            allowInsert.countDown();
            pauseBeforeInsert.set(false);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void logoutAllStartedBeforeRefreshPreventsRotationOfLockedToken() throws Exception {
        User user = createUser();
        MobileSession session = mobileLogin(user);
        AtomicReference<Thread> logoutThread = new AtomicReference<>();
        AtomicBoolean pauseLogout = new AtomicBoolean(true);
        CountDownLatch logoutHasUserLock = new CountDownLatch(1);
        CountDownLatch allowLogout = new CountDownLatch(1);
        CountDownLatch refreshReachedUserLock = new CountDownLatch(1);
        AtomicInteger refreshBackendPid = new AtomicInteger();
        doAnswer(invocation -> {
            Thread currentThread = Thread.currentThread();
            if (currentThread != logoutThread.get()) {
                refreshBackendPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class));
                refreshReachedUserLock.countDown();
            }
            User lockedUser = entityManager.find(User.class, invocation.getArgument(0),
                    LockModeType.PESSIMISTIC_WRITE);
            Optional<User> result = Optional.ofNullable(lockedUser);
            if (currentThread == logoutThread.get() && pauseLogout.get()) {
                logoutHasUserLock.countDown();
                if (!allowLogout.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release logout transaction.");
                }
            }
            return result;
        }).when(userRepositorySpy).findByIdForUpdate(anyLong());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> logout = executor.submit(() -> {
                logoutThread.set(Thread.currentThread());
                return mockMvc.perform(post("/api/auth/logout-all")
                                .header("Authorization", "Bearer " + session.accessToken()))
                        .andReturn();
            });
            assertThat(logoutHasUserLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> refresh = executor.submit(() -> mobileRefresh(session.refreshToken()).andReturn());
            assertThat(refreshReachedUserLock.await(10, TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLock(refreshBackendPid.get());
            allowLogout.countDown();

            assertThat(logout.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            MvcResult refreshResult = refresh.get(15, TimeUnit.SECONDS);
            assertThat(refreshResult.getResponse().getStatus()).isEqualTo(401);
            assertThat(objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                    .path("error_code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(activeRefreshTokenCount(user.getId())).isZero();
        } finally {
            allowLogout.countDown();
            pauseLogout.set(false);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private User createUser() {
        User user = userRepository.saveAndFlush(new User(
                "logout-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode(PASSWORD), "Logout Test"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private MobileSession mobileLogin(User user) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new MobileSession(body.path("accessToken").asText(), body.path("refreshToken").asText());
    }

    private MockHttpSessionTokens webLogin(User user) throws Exception {
        var response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new MockHttpSessionTokens(response.getCookie("access_token"), response.getCookie("refresh_token"),
                response.getCookie("XSRF-TOKEN"), response.getCookie("refresh_token").getValue());
    }

    private org.springframework.test.web.servlet.ResultActions mobileLogout(
            User user, String accessToken, String refreshToken) throws Exception {
        var request = post("/api/auth/logout").header("Authorization", "Bearer " + accessToken);
        if (refreshToken == null) {
            return mockMvc.perform(request);
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))));
    }

    private org.springframework.test.web.servlet.ResultActions mobileRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))));
    }

    private void assertAccessRevoked(String accessToken) throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("SESSION_REVOKED"));
    }

    private AuthToken findToken(String rawToken) {
        String hash = new TokenHasher().hash(rawToken);
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getTokenType() == TokenType.REFRESH && token.getTokenHash().equals(hash))
                .findFirst().orElseThrow();
    }

    private int activeRefreshTokenCount(long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth.auth_tokens "
                + "WHERE user_id = ? AND token_type = 'REFRESH' AND revoked_at IS NULL", Integer.class, userId);
    }

    private void awaitDatabaseLock(int backendPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'",
                    Integer.class, backendPid);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL backend " + backendPid + " to wait on a lock.");
    }

    private record MobileSession(String accessToken, String refreshToken) {
    }

    private record MockHttpSessionTokens(
            Cookie accessCookie, Cookie refreshCookie, Cookie xsrfCookie, String refreshToken) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
