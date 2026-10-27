package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistrationRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserRoleRepository userRoleRepository;

    @Test
    void FR002_roleWriteFailureRollsBackTheInsertedUser() throws Exception {
        String email = "rollback-" + UUID.randomUUID() + "@example.test";
        doThrow(new DataIntegrityViolationException("simulated role insert failure"))
                .when(userRoleRepository).saveAndFlush(any(UserRole.class));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "StrongPass123",
                                "confirmPassword", "StrongPass123",
                                "displayName", "Rollback User",
                                "acceptTerms", true))))
                .andExpect(status().isInternalServerError());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.users WHERE email = ?", Integer.class, email);
        assertThat(count).isZero();
    }
}
