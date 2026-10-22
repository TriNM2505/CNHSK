package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.LoginAttemptService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginAttemptAcceptanceIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "CorrectPass123";
    private static final String WRONG_PASSWORD = "WrongPass123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void AT51_fiveFailedLoginsAreCountedAndNextAttemptIsBlockedBeforeBcrypt() throws Exception {
        String email = uniqueEmail("at51");
        String ip = "192.0.2.151";
        User user = createUser(email, PASSWORD);

        for (int attempt = 1; attempt <= 5; attempt++) {
            login(email, WRONG_PASSWORD, ip)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));
        }
        assertThat(rateLimiterValue(email, ip)).isEqualTo("5");

        // If LoginService reaches bcrypt, this deliberately invalid stored hash cannot authenticate.
        user.setPasswordHash("not-a-bcrypt-hash");
        userRepository.saveAndFlush(user);

        login(email, PASSWORD, ip)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"))
                .andExpect(jsonPath("$.blockedUntil").exists());
    }

    @Test
    void AT52_blockForOneIpDoesNotPreventCorrectLoginFromAnotherIp() throws Exception {
        String email = uniqueEmail("at52");
        String blockedIp = "192.0.2.152";
        String allowedIp = "192.0.2.153";
        createUser(email, PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            loginAttemptService.recordFailure(email, blockedIp);
        }

        login(email, PASSWORD, allowedIp)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));

        assertThat(rateLimiter.exists(blockKey(email, blockedIp))).isTrue();
        login(email, PASSWORD, blockedIp)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"));
    }

    @Test
    void AT53_loginSucceedsAfterShortIsolatedRedisBlockExpiresAndClearsCounter() throws Exception {
        String email = uniqueEmail("at53");
        String ip = "192.0.2.154";
        createUser(email, PASSWORD);
        String counterKey = failureKey(email, ip);
        String blockedKey = blockKey(email, ip);
        rateLimiter.incrementAndBlockAtThreshold(counterKey, blockedKey, 1,
                Duration.ofSeconds(10), Duration.ofSeconds(2));

        login(email, PASSWORD, ip)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"));
        assertThat(rateLimiter.exists(counterKey)).isTrue();

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (rateLimiter.exists(blockedKey) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(rateLimiter.exists(blockedKey)).isFalse();

        login(email, PASSWORD, ip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
        assertThat(rateLimiter.exists(counterKey)).isFalse();
        assertThat(rateLimiter.exists(blockedKey)).isFalse();
    }

    @Test
    void AT54_failedLoginAttemptsDoNotModifyAnyAccountColumns() throws Exception {
        String email = uniqueEmail("at54");
        String ip = "192.0.2.155";
        User user = createUser(email, PASSWORD);
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT * FROM auth.users WHERE id = ?", user.getId());

        for (int attempt = 1; attempt <= 50; attempt++) {
            if (attempt <= 5) {
                login(email, WRONG_PASSWORD, ip)
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));
            } else {
                login(email, WRONG_PASSWORD, ip)
                        .andExpect(status().isLocked())
                        .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"));
            }
        }

        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT * FROM auth.users WHERE id = ?", user.getId());
        assertThat(after).isEqualTo(before);
    }

    private User createUser(String email, String password) {
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(password), "Login Attempt Learner"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String rateLimiterValue(String email, String ip) {
        return redisTemplate.opsForValue().get(failureKey(email, ip));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    private String failureKey(String email, String ip) {
        return loginGenerationPrefix(email) + "g" + currentGeneration(email) + ":fail:"
                + new TokenHasher().hash(ip);
    }

    private String blockKey(String email, String ip) {
        return loginGenerationPrefix(email) + "g" + currentGeneration(email) + ":block:"
                + new TokenHasher().hash(ip);
    }

    private long currentGeneration(String email) {
        String value = redisTemplate.opsForValue().get(loginGenerationPrefix(email) + "generation");
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String loginGenerationPrefix(String email) {
        String emailHash = new TokenHasher().hash(email.strip().toLowerCase(Locale.ROOT));
        return "auth:login:{" + emailHash + "}:";
    }
}
