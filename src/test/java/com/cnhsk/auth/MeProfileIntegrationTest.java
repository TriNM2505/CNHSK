package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void FR051_getProfileReturnsOnlyContractFields() throws Exception {
        User user = createUser();

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/me/profile")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.displayName").value("Profile User"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString());

        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("displayName", "avatarUrl", "hskLevel", "email", "emailVerified");
        assertThat(body.has("passwordHash")).isFalse();
    }

    @Test
    void FR055_patchUpdatesOnlyAllowedFieldsAndNullClearsNullableFields() throws Exception {
        User user = createUser();
        user.setAvatarUrl("https://example.test/avatar.png");
        user.setHskLevel((short) 4);
        userRepository.saveAndFlush(user);

        mockMvc.perform(patch("/api/me/profile")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"  New Name  \",\"avatarUrl\":null,\"hskLevel\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.hskLevel").doesNotExist());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getDisplayName()).isEqualTo("New Name");
        assertThat(updated.getAvatarUrl()).isNull();
        assertThat(updated.getHskLevel()).isNull();
        assertThat(updated.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void emptyPatchReturnsCurrentProfileWithoutWritingUserRow() throws Exception {
        User user = createUser();
        var before = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM auth.users WHERE id = ?", java.sql.Timestamp.class, user.getId());

        mockMvc.perform(patch("/api/me/profile")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profile User"));

        var after = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM auth.users WHERE id = ?", java.sql.Timestamp.class, user.getId());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void invalidOrUnknownProfileFieldsReturn422() throws Exception {
        User user = createUser();
        for (String body : List.of(
                "{\"displayName\":null}",
                "{\"email\":\"other@example.test\"}",
                "{\"userId\":999}",
                "{\"avatarUrl\":\"http://example.test/a.png\"}",
                "{\"hskLevel\":10}")) {
            mockMvc.perform(patch("/api/me/profile")
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details").isArray());
        }
    }

    private User createUser() {
        User user = userRepository.saveAndFlush(new User("profile-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode("ProfilePass123"), "Profile User"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user.getId(), List.of("LEARNER"), false, UUID.randomUUID());
    }
}
