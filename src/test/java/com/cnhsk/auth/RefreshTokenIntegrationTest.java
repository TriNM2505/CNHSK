package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "RefreshPass123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void AT33_webRefreshRotatesTokenAndReturnsCookiesOnly() throws Exception {
        User user = createUser();
        MockHttpServletResponse login = login(user, "WEB");
        String oldRawToken = login.getCookie("refresh_token").getValue();

        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String nextRawToken = refreshed.getCookie("refresh_token").getValue();
        assertThat(refreshed.getCookie("access_token")).isNotNull();
        assertThat(refreshed.getContentAsString()).doesNotContain(oldRawToken, nextRawToken);
        assertThat(findToken(oldRawToken).getRevokedAt()).isNotNull();
        assertThat(findToken(nextRawToken).getRevokedAt()).isNull();
        assertThat(findToken(nextRawToken).getSessionId()).isEqualTo(findToken(oldRawToken).getSessionId());
        assertThat(jwtService.verifyAccessToken(refreshed.getCookie("access_token").getValue())
                .get("sid", String.class)).isEqualTo(findToken(nextRawToken).getSessionId().toString());
    }

    @Test
    void AT34_mobileRefreshReturnsOnlyContractTokenFieldsWithoutCookies() throws Exception {
        User user = createUser();
        String oldRawToken = mobileLogin(user).path("refreshToken").asText();

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(oldRawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(900))
                .andReturn().getResponse();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "user", "accessToken", "refreshToken", "accessTokenExpiresIn");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        String nextRawToken = body.path("refreshToken").asText();
        assertThat(findToken(oldRawToken).getRevokedAt()).isNotNull();
        assertThat(findToken(nextRawToken).getTokenHash()).isEqualTo(new TokenHasher().hash(nextRawToken));
        assertThat(findToken(nextRawToken).getTokenHash()).isNotEqualTo(nextRawToken);
        assertThat(findToken(nextRawToken).getSessionId()).isEqualTo(findToken(oldRawToken).getSessionId());
        assertThat(jwtService.verifyAccessToken(body.path("accessToken").asText())
                .get("sid", String.class)).isEqualTo(findToken(nextRawToken).getSessionId().toString());
    }

    @Test
    void AT35_reuseCommitsRevocationAndRealAuditBeforeReturning401() throws Exception {
        User user = createUser();
        String replayedToken = mobileLogin(user).path("refreshToken").asText();
        String otherActiveToken = mobileLogin(user).path("refreshToken").asText();
        mobileRefresh(replayedToken).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(replayedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_REUSE_DETECTED"));

        assertThat(findToken(replayedToken).getRevokedAt()).isNotNull();
        assertThat(findToken(otherActiveToken).getRevokedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? "
                        + "AND token_type = 'REFRESH' AND revoked_at IS NULL",
                Integer.class, user.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? "
                        + "AND action = 'TOKEN_REUSE' AND target_type = 'user' AND target_id = ?",
                Integer.class, user.getId(), user.getId())).isEqualTo(1);
    }

    @Test
    void AT36_databaseStoresOnlyRefreshTokenHashes() throws Exception {
        User user = createUser();
        String rawToken = mobileLogin(user).path("refreshToken").asText();
        String storedHash = findToken(rawToken).getTokenHash();

        assertThat(storedHash).isEqualTo(new TokenHasher().hash(rawToken));
        assertThat(storedHash).isNotEqualTo(rawToken);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.auth_tokens WHERE token_hash = ?", Integer.class, rawToken)).isZero();
    }

    @Test
    void missingAndUnknownRefreshTokensReturnInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "unknown-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshDoesNotRequireLegacyAccessTokenSid() throws Exception {
        User user = createUser();
        String refreshToken = mobileLogin(user).path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer legacy-token-without-sid")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void unrevokedExpiredTokenReturnsInvalidRefreshToken() throws Exception {
        User user = createUser();
        String rawToken = mobileLogin(user).path("refreshToken").asText();
        AuthToken token = findToken(rawToken);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        authTokenRepository.saveAndFlush(token);

        mobileRefresh(rawToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
        assertThat(findToken(rawToken).getRevokedAt()).isNull();
    }

    @Test
    void revokedExpiredTokenIsReuseAndRevokesOtherSessions() throws Exception {
        User user = createUser();
        String revokedExpired = mobileLogin(user).path("refreshToken").asText();
        String otherActive = mobileLogin(user).path("refreshToken").asText();
        mobileRefresh(revokedExpired).andExpect(status().isOk());
        AuthToken token = findToken(revokedExpired);
        token.setRevokedAt(Instant.now().minusSeconds(2));
        token.setExpiresAt(Instant.now().minusSeconds(1));
        authTokenRepository.saveAndFlush(token);

        mobileRefresh(revokedExpired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_REUSE_DETECTED"));

        assertThat(findToken(otherActive).getRevokedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? AND action = 'TOKEN_REUSE'",
                Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void revokedLatestTokenWithoutSameSessionSuccessorIsInvalidNotReuse() throws Exception {
        User user = createUser();
        String deliberatelyRevoked = mobileLogin(user).path("refreshToken").asText();
        String otherSession = mobileLogin(user).path("refreshToken").asText();
        AuthToken latest = findToken(deliberatelyRevoked);
        latest.setRevokedAt(Instant.now());
        authTokenRepository.saveAndFlush(latest);

        mobileRefresh(deliberatelyRevoked)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));

        assertThat(findToken(otherSession).getRevokedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? AND action = 'TOKEN_REUSE'",
                Integer.class, user.getId())).isZero();
    }

    @Test
    void concurrentRefreshesCannotLeaveTwoValidRotations() throws Exception {
        User user = createUser();
        String sameRawToken = mobileLogin(user).path("refreshToken").asText();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentMobileRefresh(start, sameRawToken));
            Future<MvcResult> second = executor.submit(() -> concurrentMobileRefresh(start, sameRawToken));
            start.countDown();
            MockHttpServletResponse firstResponse = first.get(30, TimeUnit.SECONDS).getResponse();
            MockHttpServletResponse secondResponse = second.get(30, TimeUnit.SECONDS).getResponse();

            List<Integer> statuses = List.of(firstResponse.getStatus(), secondResponse.getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
            JsonNode successBody = objectMapper.readTree(
                    firstResponse.getStatus() == 200
                            ? firstResponse.getContentAsString()
                            : secondResponse.getContentAsString());
            String returnedRefresh = successBody.path("refreshToken").asText();
            assertThat(returnedRefresh).isNotBlank();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? "
                            + "AND token_type = 'REFRESH' AND revoked_at IS NULL",
                    Integer.class, user.getId())).isZero();
            assertThat(findToken(returnedRefresh).getRevokedAt()).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared.audit_logs WHERE actor_id = ? AND action = 'TOKEN_REUSE'",
                    Integer.class, user.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult concurrentMobileRefresh(CountDownLatch start, String rawToken) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent refresh test did not start in time.");
        }
        return mobileRefresh(rawToken).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions mobileRefresh(String rawToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson(rawToken)));
    }

    private JsonNode mobileLogin(User user) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsString());
    }

    private MockHttpServletResponse login(User user, String clientType) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", clientType)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private User createUser() {
        User user = userRepository.saveAndFlush(new User(
                "refresh-" + UUID.randomUUID() + "@example.test",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(PASSWORD),
                "Refresh Test"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private AuthToken findToken(String rawToken) {
        String hash = new TokenHasher().hash(rawToken);
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getTokenType() == TokenType.REFRESH && token.getTokenHash().equals(hash))
                .findFirst()
                .orElseThrow();
    }

    private String refreshJson(String rawToken) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("refreshToken", rawToken));
    }
}
