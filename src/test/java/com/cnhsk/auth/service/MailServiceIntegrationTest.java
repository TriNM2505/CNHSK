package com.cnhsk.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MailServiceIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void registerWebBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("cnhsk.app.web-base-url", () -> "https://cnhsk.test");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

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
