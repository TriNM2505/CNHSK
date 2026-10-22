package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void registerWebBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("cnhsk.app.web-base-url", () -> "https://cnhsk.test");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    private final TokenHasher tokenHasher = new TokenHasher();
    @Autowired private RateLimiter rateLimiter;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    private Long userId;
    private String rateLimitKey;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @AfterEach
    void removeRateLimitCounter() {
        if (rateLimitKey != null) {
            rateLimiter.clear(rateLimitKey);
        }
    }

    @Test
    void FR009_validTokenVerifiesAccountAndRevokesToken() throws Exception {
        String rawToken = "valid-" + UUID.randomUUID();
        AuthToken token = createVerificationToken(rawToken, Instant.now().plusSeconds(600), null, false);

        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM auth.users WHERE id = ?", Boolean.class, userId))
                .isTrue();
        assertThat(authTokenRepository.findById(token.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void FR011_missingExpiredAndRevokedUnverifiedTokensReturnInvalidVerifyToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("unknown-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_VERIFY_TOKEN"));

        String expired = "expired-" + UUID.randomUUID();
        createVerificationToken(expired, Instant.now().minusSeconds(1), null, false);
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(expired)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_VERIFY_TOKEN"));

        String revoked = "revoked-" + UUID.randomUUID();
        createVerificationToken(revoked, Instant.now().plusSeconds(600), Instant.now(), false);
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(revoked)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_VERIFY_TOKEN"));
    }

    @Test
    void FR094_reusingConsumedTokenForVerifiedAccountIsIdempotent() throws Exception {
        String rawToken = "consumed-" + UUID.randomUUID();
        createVerificationToken(rawToken, Instant.now().minusSeconds(30), Instant.now(), true);

        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void FR012_resendLimitsFourthRequestAndReturnsRetryAfterSeconds() throws Exception {
        createUnverifiedUser();
        rateLimitKey = "auth:verify-email:resend:" + userId;
        String bearer = jwtService.issueAccessToken(userId, java.util.List.of("LEARNER"), false, UUID.randomUUID());

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/verify-email/resend")
                            .header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        JsonNode messages = readMailpitMessages();
        assertThat(messages.path("messages")).hasSize(3);
    }

    @Test
    void FR012_verifiedAccountCannotRequestAnotherVerificationEmail() throws Exception {
        createUnverifiedUser();
        User verified = userRepository.findById(userId).orElseThrow();
        verified.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(verified);
        String bearer = jwtService.issueAccessToken(userId, java.util.List.of("LEARNER"), true, UUID.randomUUID());

        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void FR013_resendRevokesOldTokenAndPersistsNewHashBeforeSending() throws Exception {
        createUnverifiedUser();
        String oldRawToken = "old-" + UUID.randomUUID();
        AuthToken oldToken = createVerificationToken(oldRawToken, Instant.now().plusSeconds(600), null, false);
        String bearer = jwtService.issueAccessToken(userId, java.util.List.of("LEARNER"), false, UUID.randomUUID());

        mockMvc.perform(post("/api/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isAccepted());

        assertThat(authTokenRepository.findById(oldToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        var newToken = authTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getUserId().equals(userId))
                .filter(candidate -> candidate.getTokenType() == TokenType.EMAIL_VERIFY)
                .filter(candidate -> !candidate.getTokenHash().equals(tokenHasher.hash(oldRawToken)))
                .findFirst().orElseThrow();
        assertThat(newToken.getRevokedAt()).isNull();
        assertThat(newToken.getTokenHash()).hasSize(64);

        JsonNode messages = readMailpitMessages().path("messages");
        assertThat(messages).hasSize(1);
        JsonNode detail = readMailpitMessage(messages.get(0).path("ID").asText());
        String expectedEmail = userRepository.findById(userId).orElseThrow().getEmail();
        assertThat(detail.path("To").toString()).contains(expectedEmail);
        assertThat(detail.path("Text").asText()).contains("/verify-email?token=");
    }

    private void createUnverifiedUser() {
        User user = userRepository.saveAndFlush(new User(
                "verify-" + UUID.randomUUID() + "@example.test", "not-used", "Verification User"));
        userId = user.getId();
    }

    private AuthToken createVerificationToken(String rawToken, Instant expiresAt, Instant revokedAt,
            boolean verified) {
        if (userId == null) {
            createUnverifiedUser();
        }
        if (verified) {
            User user = userRepository.findById(userId).orElseThrow();
            user.setEmailVerifiedAt(Instant.now());
            userRepository.saveAndFlush(user);
        }
        AuthToken token = new AuthToken(userId, TokenType.EMAIL_VERIFY, tokenHasher.hash(rawToken), expiresAt);
        token.setRevokedAt(revokedAt);
        return authTokenRepository.saveAndFlush(token);
    }

    private String tokenRequest(String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("token", token));
    }

    private JsonNode readMailpitMessages() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode readMailpitMessage(String id) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
