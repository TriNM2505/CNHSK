package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistrationIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void registerWebBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("cnhsk.app.web-base-url", () -> "https://cnhsk.test");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    private final TokenHasher tokenHasher = new TokenHasher();
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void FR001_registerCreatesLearnerFreeUserAndHashedVerificationTokenAndSendsEmailAfterCommit() throws Exception {
        String email = "  " + UUID.randomUUID() + "@Example.test  ";
        String password = "StrongPass123";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(request(email, password, password, "  New Learner  ", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.strip().toLowerCase()))
                .andExpect(jsonPath("$.emailVerified").value(false));

        User user = userRepository.findAll().stream()
                .filter(candidate -> candidate.getEmail().equals(email.strip().toLowerCase()))
                .findFirst().orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("New Learner");
        assertThat(user.getPlanCode()).isEqualTo("FREE");
        assertThat(user.getPlanExpiresAt()).isNull();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(new BCryptPasswordEncoder().matches(password, user.getPasswordHash())).isTrue();
        assertThat(userRoleRepository.findById(new com.cnhsk.auth.entity.UserRoleId(user.getId(), RoleCode.LEARNER)))
                .isPresent();

        AuthToken token = authTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getUserId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(token.getTokenType()).isEqualTo(TokenType.EMAIL_VERIFY);
        assertThat(token.getTokenHash()).doesNotContain("=").hasSize(64);
        assertThat(Duration.between(Instant.now(), token.getExpiresAt()).abs())
                .isBetween(Duration.ofHours(24).minus(Duration.ofMinutes(1)),
                        Duration.ofHours(24).plus(Duration.ofMinutes(1)));

        JsonNode messages = readMailpitMessages();
        assertThat(messages.path("messages")).hasSize(1);
        JsonNode detail = readMailpitMessage(messages.path("messages").get(0).path("ID").asText());
        String text = detail.path("Text").asText();
        String rawToken = text.substring(text.indexOf("token=") + "token=".length()).split("\\s")[0]
                .replaceAll("[).,]+$", "");
        assertThat(token.getTokenHash()).isEqualTo(tokenHasher.hash(rawToken));
        assertThat(text).contains("/verify-email?token=");
    }

    @Test
    void FR004_duplicateNormalizedEmailReturnsContractConflict() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        register(email, "StrongPass123");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(request("  " + email.toUpperCase() + "  ", "OtherPass123", "OtherPass123", "Second User", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void FR004_concurrentDuplicateRegistrationCreatesExactlyOneAccount() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.test";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentRegister(email, ready, start));
            var second = executor.submit(() -> concurrentRegister(email, ready, start));
            ready.await();
            start.countDown();
            int firstStatus = first.get();
            int secondStatus = second.get();
            assertThat(java.util.List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(userRepository.findAll().stream().filter(user -> user.getEmail().equals(email))).hasSize(1);
    }

    @Test
    void FR005_invalidFieldsReturn422WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(request("bad-email", "abcdefgh", "different", "x", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'confirmPassword')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'acceptTerms')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'displayName')]").exists());
    }

    @Test
    void FR008_registrationDoesNotLogSensitiveRequestValues() throws Exception {
        String email = "private-" + UUID.randomUUID() + "@example.test";
        String password = "SecretPass123";
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.cnhsk");
        events.start();
        logger.addAppender(events);
        try {
            register(email, password);
            String output = events.list.toString();
            String rawToken = rawVerificationTokenFromInbox();
            assertThat(output).doesNotContain(email, password, rawToken);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(request(email, password, password, "Test Learner", true)))
                .andExpect(status().isCreated());
    }

    private int concurrentRegister(String email, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(request(email, "StrongPass123", "StrongPass123", "Race User", true)))
                .andReturn().getResponse().getStatus();
    }

    private String request(String email, String password, String confirm, String displayName, boolean accepted)
            throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "email", email, "password", password, "confirmPassword", confirm,
                "displayName", displayName, "acceptTerms", accepted));
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

    private String rawVerificationTokenFromInbox() throws Exception {
        JsonNode messages = readMailpitMessages().path("messages");
        assertThat(messages).hasSize(1);
        String text = readMailpitMessage(messages.get(0).path("ID").asText()).path("Text").asText();
        String rawToken = text.substring(text.indexOf("token=") + "token=".length()).split("\\s")[0]
                .replaceAll("[).,]+$", "");
        assertThat(rawToken).isNotBlank();
        return rawToken;
    }
}
