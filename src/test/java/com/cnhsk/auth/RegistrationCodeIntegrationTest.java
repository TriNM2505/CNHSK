package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.event.RegistrationCodeRequestedEvent;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "cnhsk.auth.registration-code-required=true")
@Import(RegistrationCodeIntegrationTest.SentCodes.class)
class RegistrationCodeIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongPass123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Mailbox mailbox;
    @MockitoSpyBean private RateLimiter rateLimiter;

    private final String email = "code-" + UUID.randomUUID() + "@example.test";

    @BeforeEach
    void emptyMailbox() {
        mailbox.events.clear();
    }

    @Test
    void noAccountExistsUntilTheEmailedCodeIsEntered() throws Exception {
        sendCode("  " + email.toUpperCase() + " ").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
        assertThat(userRepository.findByEmail(email)).isEmpty();

        String code = mailbox.lastCode(email);
        assertThat(code).matches("[0-9]{6}");
        register(email, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_VERIFY_TOKEN"));
        assertThat(userRepository.findByEmail(email)).isEmpty();

        register(email, " " + code + " ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(true));
        var user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForList("SELECT role_code FROM auth.user_roles WHERE user_id = ?",
                String.class, user.getId())).containsExactly("LEARNER");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auth.auth_tokens WHERE user_id = ?",
                Integer.class, user.getId())).isZero();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    void codeWorksOnceAndOnlyForItsOwnAddress() throws Exception {
        sendCode(email);
        String code = mailbox.lastCode(email);
        String other = "other-" + email;

        register(other, code).andExpect(status().isBadRequest());
        register(email, code).andExpect(status().isCreated());
        register(email, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.remainingAttempts").value(0));
    }

    @Test
    void fiveWrongCodesCancelTheCode() throws Exception {
        sendCode(email);
        String code = mailbox.lastCode(email);
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int left = 4; left >= 0; left--) {
            register(email, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_VERIFY_TOKEN"))
                    .andExpect(jsonPath("$.remainingAttempts").value(left));
        }
        register(email, code).andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void newCodeReplacesTheOldOneAndRestoresAttempts() throws Exception {
        sendCode(email);
        String first = mailbox.lastCode(email);
        register(email, first.equals("000000") ? "000001" : "000000").andExpect(status().isBadRequest());

        sendCode(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        skipDelay();
        sendCode(email).andExpect(status().isAccepted());
        String second = mailbox.lastCode(email);

        if (!first.equals(second)) {
            register(email, first).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.remainingAttempts").value(4));
        }
        register(email, second).andExpect(status().isCreated());
    }

    @Test
    void sixthCodeWithinAnHourIsRefused() throws Exception {
        for (int sent = 0; sent < 5; sent++) {
            sendCode(email).andExpect(status().isAccepted());
            skipDelay();
        }
        String last = mailbox.lastCode(email);

        sendCode(email).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.retryAfterSeconds").value(
                        org.hamcrest.Matchers.greaterThan(60)));
        register(email, last).andExpect(status().isCreated());
    }

    @Test
    void takenEmailAndInvalidFormSendNothing() throws Exception {
        sendCode(email);
        register(email, mailbox.lastCode(email)).andExpect(status().isCreated());
        mailbox.events.clear();
        skipDelay();

        sendCode(email).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EMAIL_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("bad-email", null))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(mailbox.events).isEmpty();
    }

    @Test
    void redisHoldsNeitherCodeNorAddress() throws Exception {
        sendCode(email);
        String code = mailbox.lastCode(email);

        var keys = redis.keys("auth:register-code:*");
        assertThat(keys).isNotEmpty().noneMatch(key -> key.contains(email));
        keys.stream().filter(key -> "hash".equals(redis.type(key).code()))
                .forEach(key -> assertThat(redis.opsForHash().values(key))
                        .doesNotContain(code)
                        .noneMatch(value -> value.toString().contains(email)));
    }

    @Test
    void codeIsRefusedWhenItsLimitsCannotBeChecked() throws Exception {
        doThrow(new QueryTimeoutException("simulated")).when(rateLimiter).increment(anyString(), any());

        sendCode(email).andExpect(status().isInternalServerError());
        assertThat(mailbox.events).isEmpty();
    }

    private void skipDelay() {
        var delays = redis.keys("auth:register-code:delay:*");
        if (!delays.isEmpty()) {
            redis.delete(delays);
        }
    }

    private ResultActions sendCode(String address) throws Exception {
        return mockMvc.perform(post("/api/auth/register/code").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(address, null))));
    }

    private ResultActions register(String address, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(address, code))));
    }

    private static Map<String, Object> body(String address, String code) {
        Map<String, Object> body = new HashMap<>(Map.of("email", address, "password", PASSWORD,
                "confirmPassword", PASSWORD, "displayName", "New Learner", "acceptTerms", true));
        if (code != null) {
            body.put("code", code);
        }
        return body;
    }

    @TestConfiguration
    static class SentCodes {
        @Bean
        Mailbox mailbox() {
            return new Mailbox();
        }
    }

    static class Mailbox {
        final List<RegistrationCodeRequestedEvent> events = new ArrayList<>();

        @EventListener
        void receive(RegistrationCodeRequestedEvent event) {
            events.add(event);
        }

        String lastCode(String address) {
            return events.stream().filter(event -> event.getRecipient().equals(address))
                    .reduce((first, second) -> second).orElseThrow().getCode();
        }
    }
}
