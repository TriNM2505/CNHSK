package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class AuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "UnverifiedPass123";

    @Autowired private AuthApi authApi;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void AT12_unverifiedUserCanLoginButVerifiedEmailRequirementRejectsPointAction() throws Exception {
        String email = "auth-api-" + java.util.UUID.randomUUID() + "@example.test";
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(PASSWORD), "Auth API Learner"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));

        assertThatThrownBy(() -> authApi.requireVerifiedEmail(user.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo(ErrorCode.ACCOUNT_UNVERIFIED);
                    assertThat(apiException.getStatus().value()).isEqualTo(403);
                });

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
    }
}
