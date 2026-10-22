package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.stream.StreamSupport;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CurrentUserIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "CurrentUserPass123";
    private static final String GAME_ORIGIN = "http://localhost:5174";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Value("${cnhsk.jwt.secret}") private String jwtSecret;
    @MockitoBean private AuditService auditService;

    @Test
    void AT29_cookieLoginFromGameOriginReturnsCurrentUserAndContractShape() throws Exception {
        User user = createUser("me-cookie-" + UUID.randomUUID() + "@example.test");
        var loginResponse = mockMvc.perform(post("/api/auth/login")
                        .header("Origin", GAME_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Origin", GAME_ORIGIN)
                        .cookie(loginResponse.getCookies()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", GAME_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.displayName").value("Current User"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"))
                .andReturn().getResponse().getContentAsString());

        assertThat(fieldNames(response)).containsExactlyInAnyOrder(
                "id", "email", "displayName", "emailVerified", "roles", "hskLevel", "avatarUrl");
    }

    @Test
    void AT30_missingTokenReturnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @Test
    void legacyAccessTokenWithoutSidIsRejectedOnProtectedApi() throws Exception {
        User user = createUser("legacy-sid-" + UUID.randomUUID() + "@example.test");
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("roles", List.of("LEARNER"))
                .claim("ev", false)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @Test
    void bearerTokenReturnsCurrentUser() throws Exception {
        User user = createUser("me-bearer-" + UUID.randomUUID() + "@example.test");
        String token = jwtService.issueAccessToken(user.getId(), List.of("LEARNER"), false, UUID.randomUUID());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void validTokenForMissingUserReturnsUnauthenticatedWithoutLeakingDetails() throws Exception {
        String token = jwtService.issueAccessToken(Long.MAX_VALUE, List.of("LEARNER"), false, UUID.randomUUID());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @Test
    void rolesAreReadFromDatabaseRatherThanTokenClaims() throws Exception {
        User user = createUser("me-roles-" + UUID.randomUUID() + "@example.test");
        String token = jwtService.issueAccessToken(user.getId(), List.of("LEARNER"), false, UUID.randomUUID());
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.TEACHER, null));

        JsonNode response = objectMapper.readTree(mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(StreamSupport.stream(response.path("roles").spliterator(), false)
                .map(JsonNode::asText).toList()).containsExactly("LEARNER", "TEACHER");
        assertThat(response.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "email", "displayName", "emailVerified", "roles", "hskLevel", "avatarUrl");
        assertThat(response.has("passwordHash")).isFalse();
        assertThat(response.has("accessToken")).isFalse();
        assertThat(response.has("refreshToken")).isFalse();
    }

    private User createUser(String email) {
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(PASSWORD), "Current User"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private String loginJson(String email) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD));
    }

    private static List<String> fieldNames(JsonNode node) {
        return java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList();
    }
}
