package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class AdminUserQueryIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("AdminQueryPass123");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private AuditService auditServiceSpy;

    @Test
    void AT58_nonAdminCannotProbeExistingOrMissingUserIds() throws Exception {
        User learner = createUser("learner-" + UUID.randomUUID() + "@example.test", true);
        User target = createUser("target-" + UUID.randomUUID() + "@example.test", true);

        mockMvc.perform(get("/api/admin/users/{id}", target.getId())
                        .header("Authorization", bearer(learner, RoleCode.LEARNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/users/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(learner, RoleCode.LEARNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void AT59_listMasksEmailAndReturnsOnlyContractFields() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        User target = createUser("bauser-" + UUID.randomUUID() + "@gmail.com", true);

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .param("q", target.getEmail().substring(0, 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].emailMasked").value(
                        target.getEmail().substring(0, 2) + "•••@gmail.com"))
                .andReturn().getResponse().getContentAsString());

        JsonNode item = body.path("items").get(0);
        assertThat(item.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "emailMasked", "displayName", "roles", "status", "createdAt");
        assertThat(item.toString()).doesNotContain(target.getEmail(), "password", "token");
    }

    @Test
    void AT75_searchFiltersAndReturnsRequestedPageOrderedByCreationTime() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        String query = "t23" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<User> seeded = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            seeded.add(createUser("t23-user-" + query + "-" + index + "@example.test", true));
        }
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        for (int index = 0; index < seeded.size(); index++) {
            jdbcTemplate.update("UPDATE auth.users SET created_at = ? WHERE id = ?",
                    Timestamp.from(base.plusSeconds(index)), seeded.get(index).getId());
        }

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .param("q", query)
                        .param("size", "10")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(30))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andReturn().getResponse().getContentAsString());

        List<Long> expectedPageIds = new ArrayList<>();
        for (int index = 19; index >= 10; index--) {
            expectedPageIds.add(seeded.get(index).getId());
        }
        List<Long> actualPageIds = new ArrayList<>();
        response.path("items").forEach(item -> actualPageIds.add(item.path("id").asLong()));
        assertThat(actualPageIds).containsExactlyElementsOf(expectedPageIds);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .param("q", "x"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void listAppliesSingleRoleAndContractStatusFilters() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        String query = "role-status-" + UUID.randomUUID();
        User matching = createUser(query + "-matching@example.test", true);
        userRoleRepository.saveAndFlush(new UserRole(matching.getId(), RoleCode.TEACHER, admin.getId()));
        User wrongRole = createUser(query + "-wrong-role@example.test", true);
        User wrongStatus = createUser(query + "-wrong-status@example.test", false);
        userRoleRepository.saveAndFlush(new UserRole(wrongStatus.getId(), RoleCode.TEACHER, admin.getId()));

        String responseBody = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin, RoleCode.ADMIN))
                        .param("q", query)
                        .param("role", "TEACHER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matching.getId()))
                .andReturn().getResponse().getContentAsString();
        List<String> roles = new ArrayList<>();
        objectMapper.readTree(responseBody).path("items").get(0).path("roles")
                .forEach(role -> roles.add(role.asText()));
        assertThat(roles).containsExactly("LEARNER", "TEACHER");
    }

    @Test
    void AT76_detailReturnsFullEmailAndPersistsViewPiiAudit() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        User target = createUser("detail-" + UUID.randomUUID() + "@example.test", true);
        userRoleRepository.saveAndFlush(new UserRole(target.getId(), RoleCode.TEACHER, admin.getId()));

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/admin/users/{id}", target.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(target.getEmail()))
                .andExpect(jsonPath("$.roles[1].code").value("TEACHER"))
                .andExpect(jsonPath("$.roles[1].grantedBy").value(admin.getId()))
                .andExpect(jsonPath("$.roles[1].grantedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "email", "displayName", "roles", "status", "bannedAt", "banReason",
                "emailVerifiedAt", "createdAt");
        assertThat(body.toString()).doesNotContain("passwordHash", "password_hash", "accessToken", "refreshToken");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.audit_logs WHERE actor_id = ? AND action = ? "
                        + "AND target_type = 'user' AND target_id = ?",
                Long.class, admin.getId(), "VIEW_USER_PII", target.getId())).isEqualTo(1L);
    }

    @Test
    void auditFailureReturnsGeneric500AndNeverReturnsFullEmail() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        User target = createUser("private-" + UUID.randomUUID() + "@example.test", true);
        doThrow(new DataAccessResourceFailureException("simulated audit persistence failure"))
                .when(auditServiceSpy).record(any(), any(), any(), isNull(), isNull());

        String responseBody = mockMvc.perform(get("/api/admin/users/{id}", target.getId())
                        .header("Authorization", bearer(admin, RoleCode.ADMIN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value(ErrorCode.INTERNAL_ERROR.name()))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi nội bộ."))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain(target.getEmail(), "simulated audit persistence failure");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.audit_logs WHERE action = 'VIEW_USER_PII' AND target_id = ?",
                Long.class, target.getId())).isZero();
    }

    @Test
    void missingAdminDetailReturnsContract404AndUnauthenticatedListReturns401() throws Exception {
        User admin = createUser("admin-" + UUID.randomUUID() + "@example.test", true);
        mockMvc.perform(get("/api/admin/users/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(admin, RoleCode.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    private User createUser(String email, boolean verified) {
        User user = new User(email, PASSWORD_HASH, "Admin Query User");
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        user = userRepository.saveAndFlush(user);
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private String bearer(User user, RoleCode role) {
        return "Bearer " + jwtService.issueAccessToken(user.getId(), List.of(role.name()), true, UUID.randomUUID());
    }
}
