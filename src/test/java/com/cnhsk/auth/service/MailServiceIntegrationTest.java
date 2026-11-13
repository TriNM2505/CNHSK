package com.cnhsk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Properties;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.ReminderSendResult;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.reminder.UnsubscribeTokens;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.auth.event.PasswordResetRequestedEvent;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MailServiceIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void registerWebBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("cnhsk.app.web-base-url", () -> "https://cnhsk.test");
        registry.add("cnhsk.app.api-base-url", () -> "https://api.cnhsk.test/");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired private AuthApi auth;
    @Autowired private UserRepository users;
    @Autowired private UnsubscribeTokens tokens;
    @Autowired private JdbcTemplate jdbc;

    @Value("${test.mailpit.base-url}")
    private String mailpitBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void sendsVerificationEmailAfterCommitWithExpectedLink() throws Exception {
        String token = "verification-" + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.execute(status -> {
            eventPublisher.publishEvent(new EmailVerificationRequestedEvent("verify@cnhsk.test", token));
            assertThat(readMessageListUnchecked().path("messages")).isEmpty();
            return null;
        });

        JsonNode message = onlyMessageDetail();
        assertThat(message.path("Subject").asText()).isEqualTo("Xác thực email CNHSK");
        assertThat(message.path("Text").asText())
                .contains("https://cnhsk.test/verify-email?token=" + token)
                .contains("24 giờ");
    }

    @Test
    void sendsPasswordResetEmailAfterCommitWithExpectedLink() throws Exception {
        String token = "reset-" + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.execute(status -> {
            eventPublisher.publishEvent(new PasswordResetRequestedEvent("reset@cnhsk.test", token));
            return null;
        });

        JsonNode message = onlyMessageDetail();
        assertThat(message.path("Subject").asText()).isEqualTo("Đặt lại mật khẩu CNHSK");
        assertThat(message.path("Text").asText())
                .contains("https://cnhsk.test/reset-password?token=" + token)
                .contains("1 giờ");
    }

    @Test
    void doesNotSendEmailWhenPublishingTransactionRollsBack() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
                    "rollback@cnhsk.test", "rollback-" + UUID.randomUUID()));
            status.setRollbackOnly();
            return null;
        });

        assertThat(readMessageList().path("messages")).isEmpty();
    }

    @Test
    void FR014_FR015_AT13_deliversMultipartReminderWithFrontendLinksAndVerifiedUnsubscribeHeaders() throws Exception {
        User fixture = new User("study-reminder-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode("ReminderFixture123"), "<script>name</script> & Learner");
        fixture.setEmailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"));
        fixture.setReminderTime(LocalTime.of(20, 0));
        fixture.setReminderChannels("WEB,EMAIL");
        User user = users.saveAndFlush(fixture);
        try {
            assertThat(auth.sendStudyReminder(user.getId(), 7)).isEqualTo(ReminderSendResult.SENT);

            JsonNode message = onlyMessageDetail();
            MimeMessage raw = rawMessage(message.path("ID").asText());
            assertThat(raw.isMimeType("multipart/*")).isTrue();
            assertThat(raw.getSubject()).isEqualTo("CNHSK · Bạn có 7 thẻ cần ôn hôm nay");
            String header = raw.getHeader("List-Unsubscribe", null);
            assertThat(header).startsWith("<https://api.cnhsk.test/api/public/reminder/unsubscribe?token=")
                    .endsWith(">");
            assertThat(raw.getHeader("List-Unsubscribe-Post", null)).isEqualTo("List-Unsubscribe=One-Click");
            String token = UriComponentsBuilder.fromUriString(header.substring(1, header.length() - 1))
                    .build().getQueryParams().getFirst("token");
            assertThat(tokens.verify(token)).isEqualTo(user.getId());
            assertThat(message.path("Text").asText()).contains("Xin chào " + user.getDisplayName(), "7 thẻ",
                    "https://cnhsk.test/learn/review", "https://cnhsk.test/reminder/unsubscribe?token=" + token);
            assertThat(message.path("HTML").asText()).contains("&lt;script&gt;name&lt;/script&gt; &amp; Learner",
                    "7 thẻ", "href=\"https://cnhsk.test/learn/review\"", "Ôn ngay", "Tắt nhắc qua email",
                    "href=\"https://cnhsk.test/reminder/unsubscribe?token=" + token + "\"")
                    .doesNotContain("<script>");
        } finally {
            jdbc.update("DELETE FROM auth.users WHERE id = ?", user.getId());
        }
    }

    private MimeMessage rawMessage(String messageId) throws Exception {
        assertThat(messageId).isNotBlank();
        HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + messageId + "/raw")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(response.body()));
    }

    private JsonNode readMessageList() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode readMessageListUnchecked() {
        try {
            return readMessageList();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect the isolated Mailpit inbox.", exception);
        }
    }

    private JsonNode onlyMessageDetail() throws Exception {
        JsonNode messages = readMessageList().path("messages");
        assertThat(messages).hasSize(1);
        String messageId = messages.get(0).path("ID").asText();
        assertThat(messageId).isNotBlank();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + messageId))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }
}
