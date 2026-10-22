package com.cnhsk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class LoginAttemptServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "Login.Attempt-" + UUID.randomUUID() + "@example.test";
    private static final String IP_ADDRESS = "192.0.2.17";
    private static final String CORRECT_PASSWORD = "CorrectPass123";

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @AfterEach
    void clearAttemptState() {
        loginAttemptService.clearAfterSuccess(EMAIL, IP_ADDRESS);
        loginAttemptService.clearAfterSuccess(EMAIL.toLowerCase(), "192.0.2.18");
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry() throws Exception {
        loginAttemptService.recordFailure(EMAIL, IP_ADDRESS);
        String failureKey = loginKey(EMAIL, IP_ADDRESS, "fail");
        String blockKey = loginKey(EMAIL, IP_ADDRESS, "block");
        long failureExpiryAtFirstFailure = rateLimiter.expiryTimeMillis(failureKey);

        for (int attempt = 2; attempt <= 4; attempt++) {
            loginAttemptService.recordFailure(EMAIL.toLowerCase(), IP_ADDRESS);
        }
        loginAttemptService.ensureNotBlocked(EMAIL, IP_ADDRESS);
        assertThat(rateLimiter.expiryTimeMillis(failureKey)).isEqualTo(failureExpiryAtFirstFailure);

        long t0 = clock.millis();
        loginAttemptService.recordFailure(EMAIL, IP_ADDRESS);
        ApiException blocked = catchBlocked(EMAIL, IP_ADDRESS);
        assertThat(rateLimiter.expiryTimeMillis(blockKey)).isPositive();
        assertThat(blocked.getCode()).isEqualTo(ErrorCode.LOGIN_SOURCE_BLOCKED);
        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.LOCKED);
        assertThat(blocked.getExtra().get("blockedUntil"))
                .isEqualTo(Instant.ofEpochMilli(rateLimiter.expiryTimeMillis(blockKey)));
        assertThat(catchBlocked(EMAIL, IP_ADDRESS).getExtra().get("blockedUntil"))
                .isEqualTo(blocked.getExtra().get("blockedUntil"));

        loginAttemptService.ensureNotBlocked(EMAIL, "192.0.2.18");
        loginAttemptService.ensureNotBlocked("another-" + EMAIL, IP_ADDRESS);

        User user = userRepository.saveAndFlush(new User(EMAIL,
                new BCryptPasswordEncoder(12).encode(CORRECT_PASSWORD), "Login Attempt User"));
        assertThat(new BCryptPasswordEncoder(12).matches(CORRECT_PASSWORD, user.getPasswordHash())).isTrue();
        var lockedLogin = mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(IP_ADDRESS); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", EMAIL, "password", CORRECT_PASSWORD))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"))
                .andReturn();
        long t1 = clock.millis();

        long actualBlockExpiry = rateLimiter.expiryTimeMillis(blockKey);
        long fifteenMinutesMillis = Duration.ofMinutes(15).toMillis();
        long roundingAllowanceMillis = 1L;
        assertThat(actualBlockExpiry)
                .as("Redis PEXPIRETIME must fall between the fifth failure and the blocked response")
                .isBetween(t0 + fifteenMinutesMillis - roundingAllowanceMillis, t1 + fifteenMinutesMillis);
        Instant responseBlockedUntil = Instant.parse(
                objectMapper.readTree(lockedLogin.getResponse().getContentAsString())
                        .path("blockedUntil").asText());
        assertThat(responseBlockedUntil).isEqualTo(Instant.ofEpochMilli(actualBlockExpiry));
    }

    @Test
    void FR049_clearAfterSuccessDeletesCounterAndBlockForThatPair() {
        for (int attempt = 0; attempt < 5; attempt++) {
            loginAttemptService.recordFailure(EMAIL, IP_ADDRESS);
        }

        loginAttemptService.clearAfterSuccess(EMAIL, IP_ADDRESS);

        String failureKey = loginKey(EMAIL, IP_ADDRESS, "fail");
        String blockKey = loginKey(EMAIL, IP_ADDRESS, "block");
        assertThat(rateLimiter.exists(failureKey)).isFalse();
        assertThat(rateLimiter.exists(blockKey)).isFalse();
        loginAttemptService.ensureNotBlocked(EMAIL, IP_ADDRESS);
        loginAttemptService.recordFailure(EMAIL, IP_ADDRESS);
        assertThat(rateLimiter.exists(blockKey)).isFalse();
    }

    @Test
    void FR042_passwordResetAdvancesGenerationAndRemovesCurrentAndLegacyEmailKeys() {
        String email = "reset-cleanup-" + UUID.randomUUID() + "@example.test";
        String emailHash = new TokenHasher().hash(email);
        String generationKey = "auth:login:{" + emailHash + "}:generation";
        String registryKey = "auth:login:{" + emailHash + "}:registry";
        String ipHash = new TokenHasher().hash(IP_ADDRESS);
        loginAttemptService.recordFailure(email, IP_ADDRESS);
        for (int attempt = 0; attempt < 5; attempt++) {
            loginAttemptService.recordFailure(email, "192.0.2.99");
        }
        String legacyFailure = "auth:login-fail:" + emailHash + ":192.0.2.200";
        String legacyBlock = "auth:login-block:" + emailHash + ":192.0.2.200";
        redisTemplate.opsForValue().set(legacyFailure, "2", Duration.ofMinutes(15));
        redisTemplate.opsForValue().set(legacyBlock, "1", Duration.ofMinutes(15));
        long oldGeneration = rateLimiter.loginGeneration(generationKey);
        String oldCounter = "auth:login:{" + emailHash + "}:g" + oldGeneration + ":fail:" + ipHash;
        String oldBlock = "auth:login:{" + emailHash + "}:g" + oldGeneration + ":block:"
                + new TokenHasher().hash("192.0.2.99");

        loginAttemptService.clearAllAfterPasswordReset(email);

        assertThat(rateLimiter.loginGeneration(generationKey)).isEqualTo(oldGeneration + 1);
        assertThat(rateLimiter.exists(oldCounter)).isFalse();
        assertThat(rateLimiter.exists(oldBlock)).isFalse();
        assertThat(rateLimiter.exists(legacyFailure)).isFalse();
        assertThat(rateLimiter.exists(legacyBlock)).isFalse();
        assertThat(redisTemplate.opsForSet().size(registryKey)).isZero();
    }

    @Test
    void staleGenerationWriterAndPairCleanupCannotTouchNewGenerationKeys() {
        String email = "generation-race-" + UUID.randomUUID() + "@example.test";
        String emailHash = new TokenHasher().hash(email);
        String prefix = "auth:login:{" + emailHash + "}:";
        String generationKey = prefix + "generation";
        String registryKey = prefix + "registry";
        String ipHash = new TokenHasher().hash(IP_ADDRESS);
        long oldGeneration = rateLimiter.loginGeneration(generationKey);

        loginAttemptService.clearAllAfterPasswordReset(email);
        long currentGeneration = rateLimiter.loginGeneration(generationKey);
        String counter = prefix + "g" + currentGeneration + ":fail:" + ipHash;
        String block = prefix + "g" + currentGeneration + ":block:" + ipHash;
        assertThat(rateLimiter.recordLoginFailure(generationKey, currentGeneration, counter, block, registryKey,
                5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofMinutes(30))).isEqualTo(1);

        assertThat(rateLimiter.recordLoginFailure(generationKey, oldGeneration,
                prefix + "g" + oldGeneration + ":fail:" + ipHash,
                prefix + "g" + oldGeneration + ":block:" + ipHash,
                registryKey, 5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofMinutes(30)))
                .isEqualTo(-1);
        assertThat(rateLimiter.clearLoginPair(generationKey, oldGeneration, counter, block, registryKey)).isFalse();
        assertThat(rateLimiter.exists(counter)).isTrue();
    }

    @Test
    void FR046_concurrentFailuresAreCountedAtomicallyAndCreateTheBlock() throws Exception {
        int workers = 8;
        int attemptsPerWorker = 10;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int attempt = 0; attempt < attemptsPerWorker; attempt++) {
                        loginAttemptService.recordFailure(EMAIL, IP_ADDRESS);
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            String blockKey = loginKey(EMAIL, IP_ADDRESS, "block");
            String failureKey = loginKey(EMAIL, IP_ADDRESS, "fail");
            assertThat(rateLimiter.exists(blockKey)).isTrue();
            assertThat(rateLimiter.expiryTimeMillis(failureKey)).isPositive();
            assertThat(catchBlocked(EMAIL, IP_ADDRESS).getCode()).isEqualTo(ErrorCode.LOGIN_SOURCE_BLOCKED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void redisCounterAndBlockExpireAndCounterCanStartAgain() throws InterruptedException {
        String counterKey = "test:login-attempt:failure:" + UUID.randomUUID();
        String blockKey = "test:login-attempt:block:" + UUID.randomUUID();
        try {
            assertThat(rateLimiter.incrementAndBlockAtThreshold(counterKey, blockKey, 2,
                    Duration.ofSeconds(2), Duration.ofSeconds(2))).isEqualTo(1);
            assertThat(rateLimiter.incrementAndBlockAtThreshold(counterKey, blockKey, 2,
                    Duration.ofSeconds(2), Duration.ofSeconds(2))).isEqualTo(2);

            assertRedisTtlIsWithinWindow(counterKey);
            assertRedisTtlIsWithinWindow(blockKey);
            awaitKeysToExpire(counterKey, blockKey);

            assertThat(rateLimiter.exists(counterKey)).isFalse();
            assertThat(rateLimiter.exists(blockKey)).isFalse();
            assertThat(rateLimiter.incrementAndBlockAtThreshold(counterKey, blockKey, 2,
                    Duration.ofSeconds(2), Duration.ofSeconds(2))).isEqualTo(1);
        } finally {
            rateLimiter.clear(counterKey);
            rateLimiter.clear(blockKey);
        }
    }

    private void assertRedisTtlIsWithinWindow(String key) {
        Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttlMillis)
                .as("Redis PTTL for %s must be present and within the configured window", key)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofSeconds(2).toMillis());
    }

    private void awaitKeysToExpire(String counterKey, String blockKey) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((rateLimiter.exists(counterKey) || rateLimiter.exists(blockKey))
                && System.nanoTime() < deadlineNanos) {
            Thread.sleep(50);
        }
    }

    @Test
    void redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput output) {
        RateLimiter failingRateLimiter = mock(RateLimiter.class);
        LoginAttemptService service = new LoginAttemptService(failingRateLimiter);
        DataAccessResourceFailureException redisFailure = new DataAccessResourceFailureException(
                "simulated Redis outage");
        when(failingRateLimiter.loginGeneration(anyString())).thenReturn(0L);
        when(failingRateLimiter.loginBlockExpiryTimeMillis(anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString())).thenThrow(redisFailure);
        when(failingRateLimiter.recordLoginFailure(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(redisFailure);
        doThrow(redisFailure).when(failingRateLimiter).clearLoginPair(anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(), anyString());

        service.ensureNotBlocked(EMAIL, IP_ADDRESS);
        service.recordFailure(EMAIL, IP_ADDRESS);
        service.clearAfterSuccess(EMAIL, IP_ADDRESS);

        verify(failingRateLimiter).clearLoginPair(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString(), anyString());
        assertThat(output).contains("WARN", "Login attempt rate limit unavailable", "continuing fail-open");
        assertThat(output.toString()).doesNotContain(EMAIL, IP_ADDRESS, "simulated Redis outage");
    }

    private ApiException catchBlocked(String email, String ipAddress) {
        try {
            loginAttemptService.ensureNotBlocked(email, ipAddress);
            throw new AssertionError("Expected the email/IP pair to be blocked.");
        } catch (ApiException exception) {
            return exception;
        }
    }

    private String loginKey(String email, String ipAddress, String kind) {
        String emailHash = new TokenHasher().hash(email.strip().toLowerCase(java.util.Locale.ROOT));
        String ipHash = new TokenHasher().hash(ipAddress);
        long generation = rateLimiter.loginGeneration("auth:login:{" + emailHash + "}:generation");
        return "auth:login:{" + emailHash + "}:g" + generation + ":" + kind + ":" + ipHash;
    }
}
