package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.LoginAttemptService;
import com.cnhsk.shared.security.RateLimiter;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "CorrectPass123";
    private static final String CLIENT_IP = "192.0.2.45";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @Test
    void FR017_FR018_wrongAndUnknownCredentialsHaveSame401ResponseAndIncrementFailures() throws Exception {
        User user = createUser("wrong-" + UUID.randomUUID() + "@example.test", PASSWORD);

        var wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), "WrongPass123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        var unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("missing-" + UUID.randomUUID() + "@example.test", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).path("message").asText())
                .isEqualTo("Email hoặc mật khẩu không đúng");
        assertThat(objectMapper.readTree(unknownEmail).path("message").asText())
                .isEqualTo(objectMapper.readTree(wrongPassword).path("message").asText());
        assertThat(rateLimiter.exists(failureKey(user.getEmail(), CLIENT_IP))).isTrue();
    }

    @Test
    void FR047_blockedPairReturns423BeforeAttemptingBcrypt() throws Exception {
        String email = "blocked-" + UUID.randomUUID() + "@example.test";
        User user = createUser(email, "not-a-valid-bcrypt-hash");
        for (int attempt = 0; attempt < 5; attempt++) {
            loginAttemptService.recordFailure(email, CLIENT_IP);
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error_code").value("LOGIN_SOURCE_BLOCKED"))
                .andExpect(jsonPath("$.blockedUntil").exists());
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    void emptyEmailReturns422ValidationWithoutCallingRedisOrReturning500() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("   ", PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field == 'email')]" ).exists());
    }

    @Test
    void FR020_FR021_bannedTakesPriorityWhenAccountIsAlsoSuspended() throws Exception {
        User user = createUser("banned-suspended-" + UUID.randomUUID() + "@example.test", PASSWORD);
        user.setBannedAt(Instant.now());
        user.setBanReason("Lý do nội bộ");
        user.setSuspendedAt(Instant.now());
        user.setSuspendedUntil(Instant.now().plusSeconds(3600));
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.banReason").value("Lý do nội bộ"));
    }

    @Test
    void FR021_activeSuspensionReturnsSuspendedUntil() throws Exception {
        User user = createUser("suspended-" + UUID.randomUUID() + "@example.test", PASSWORD);
        Instant until = Instant.now().plusSeconds(3600);
        user.setSuspendedAt(Instant.now());
        user.setSuspendedUntil(until);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.suspendedUntil").exists());
    }

    @Test
    void FR022_mobileReturnsOnlyContractTokenFieldsAndPersistsOnlyRefreshHash() throws Exception {
        User user = createUser("mobile-" + UUID.randomUUID() + "@example.test", PASSWORD);

        var response = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(900))
                .andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        String refreshToken = body.path("refreshToken").asText();
        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "user", "accessToken", "refreshToken", "accessTokenExpiresIn");
        var storedRefresh = authTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(storedRefresh.getTokenHash()).isEqualTo(new TokenHasher().hash(refreshToken))
                .isNotEqualTo(refreshToken);
        assertThat(storedRefresh.getSessionId()).isNotNull();
        assertThat(jwtService.verifyAccessToken(body.path("accessToken").asText())
                .get("sid", String.class)).isEqualTo(storedRefresh.getSessionId().toString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE action = 'LOGIN_SUCCESS' AND target_id = ?",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void FR014_FR028_FR089_webAndUnknownClientTypeUseCookiesAndOmitTokensFromBody() throws Exception {
        User user = createUser("web-" + UUID.randomUUID() + "@example.test", PASSWORD);

        var response = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "unexpected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessTokenExpiresIn").doesNotExist())
                .andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).hasSize(3)
                .anySatisfy(cookie -> assertThat(cookie).contains("access_token=").contains("HttpOnly").contains("Domain="))
                .anySatisfy(cookie -> assertThat(cookie).contains("refresh_token=").contains("Path=/api/auth"))
                .anySatisfy(cookie -> assertThat(cookie).contains("XSRF-TOKEN=").doesNotContain("HttpOnly"));
    }

    @Test
    void FR019_successClearsFailureCounterAndWritesLoginAudit() throws Exception {
        User user = createUser("counter-" + UUID.randomUUID() + "@example.test", PASSWORD);
        for (int attempt = 0; attempt < 3; attempt++) {
            loginAttemptService.recordFailure(user.getEmail(), CLIENT_IP);
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk());

        assertThat(rateLimiter.exists(failureKey(user.getEmail(), CLIENT_IP))).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE action = 'LOGIN_SUCCESS' AND target_id = ?",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void loginIgnoresLegacyAccessCookieWithoutSid() throws Exception {
        User user = createUser("legacy-access-" + UUID.randomUUID() + "@example.test", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer legacy-token-without-sid")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    private User createUser(String email, String password) {
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(password), "Login Learner"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private String failureKey(String email, String ipAddress) {
        String normalizedEmail = email.strip().toLowerCase(java.util.Locale.ROOT);
        TokenHasher hasher = new TokenHasher();
        String emailHash = hasher.hash(normalizedEmail);
        String ipHash = hasher.hash(ipAddress);
        long generation = rateLimiter.loginGeneration("auth:login:{" + emailHash + "}:generation");
        return "auth:login:{" + emailHash + "}:g" + generation + ":fail:" + ipHash;
    }
}
