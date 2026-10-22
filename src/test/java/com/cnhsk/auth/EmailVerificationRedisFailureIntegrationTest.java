package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationRedisFailureIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    private final TokenHasher tokenHasher = new TokenHasher();
    @MockitoBean private RateLimiter rateLimiter;
    @MockitoBean private StringRedisTemplate redisTemplate;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void FR012_redisFailureFailsClosedWithoutRevokingTokenOrSendingEmail() throws Exception {
        assertFailsClosedWithoutSendingEmail(false);
    }

    @Test
    void FR012_retryAfterLookupFailureFailsClosedWithoutRevokingTokenOrSendingEmail() throws Exception {
        assertFailsClosedWithoutSendingEmail(true);
    }

    private void assertFailsClosedWithoutSendingEmail(boolean failDuringRetryAfterLookup) throws Exception {
        String email = "redis-failure-" + UUID.randomUUID() + "@example.test";
        User user = userRepository.saveAndFlush(new User(email, "not-used", "Verification User"));
        String oldRawToken = "existing-" + UUID.randomUUID();
        AuthToken oldToken = authTokenRepository.saveAndFlush(new AuthToken(
                user.getId(), TokenType.EMAIL_VERIFY, tokenHasher.hash(oldRawToken), Instant.now().plusSeconds(600)));
        String rateLimitKey = "auth:verify-email:resend:" + user.getId();
        if (failDuringRetryAfterLookup) {
            when(rateLimiter.increment(eq(rateLimitKey), eq(Duration.ofHours(1))))
                    .thenReturn(4L);
            doThrow(new RedisConnectionFailureException("simulated Redis outage"))
                    .when(redisTemplate).getExpire(eq(rateLimitKey), eq(TimeUnit.SECONDS));
        } else {
            doThrow(new RedisConnectionFailureException("simulated Redis outage"))
                    .when(rateLimiter).increment(eq(rateLimitKey), eq(Duration.ofHours(1)));
        }
        String bearer = jwtService.issueAccessToken(user.getId(), List.of("LEARNER"), false, UUID.randomUUID());

        var result = mockMvc.perform(post("/api/auth/verify-email/resend")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.request_id").isString())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(email, oldRawToken, bearer);
        assertThat(authTokenRepository.findById(oldToken.getId()).orElseThrow().getRevokedAt()).isNull();
        assertThat(authTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(user.getId()))
                .filter(token -> token.getTokenType() == TokenType.EMAIL_VERIFY)).hasSize(1);

        HttpResponse<String> inbox = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(inbox.statusCode()).isEqualTo(200);
        JsonNode messages = objectMapper.readTree(inbox.body()).path("messages");
        assertThat(messages).isEmpty();
    }
}
