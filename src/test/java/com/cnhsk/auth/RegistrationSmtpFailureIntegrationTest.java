package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistrationSmtpFailureIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void smtpFailureAfterCommitStillReturnsCreatedAndPersistsUser() throws Exception {
        String email = "smtp-failure-" + UUID.randomUUID() + "@example.test";
        doReturn(new MimeMessage(Session.getInstance(new Properties()))).when(mailSender).createMimeMessage();
        doThrow(new MailSendException("simulated SMTP connection failure"))
                .when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "StrongPass123",
                                "confirmPassword", "StrongPass123",
                                "displayName", "SMTP Failure User",
                                "acceptTerms", true))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(email)).isPresent();
    }
}
