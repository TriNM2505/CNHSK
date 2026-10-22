package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.security.TokenHasher;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "LifecyclePass123";
    private static final String NEW_PASSWORD = "LifecycleNewPass456";
    private static final String VERIFY_LINK_PATTERN = "/verify-email\\?token=([A-Za-z0-9_-]+)";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;
    @Value("${VITE_GAME_ORIGIN:http://localhost:5174}") private String gameOrigin;

    @BeforeEach
    void clearMailpitInbox() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @Test
    void fullWebAndMobileLifecycleUsesMailpitGameOriginPasswordChangeAndLogout() throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD,
                                "displayName", "Lifecycle Learner",
                                "acceptTerms", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));

        JsonNode messages = readMailpitMessages().path("messages");
        assertThat(messages).hasSize(1);
        JsonNode verificationEmail = readMailpitMessage(messages.get(0).path("ID").asText());
        String emailText = verificationEmail.path("Text").asText();
        assertThat(verificationEmail.path("To").toString()).contains(email);
        assertThat(emailText).contains("/verify-email?token=");
        String verificationToken = extractVerificationToken(emailText);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        User user = userRepository.findByEmail(email).orElseThrow();

        MvcResult webLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webAccess = requiredCookie(webLogin, "access_token");
        Cookie webRefresh = requiredCookie(webLogin, "refresh_token");
        Cookie webXsrf = requiredCookie(webLogin, "XSRF-TOKEN");
        assertCookie(webLogin, "access_token", "/", "Max-Age=900");
        assertCookie(webLogin, "refresh_token", "/api/auth", null);

        MvcResult mobileLogin = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        assertThat(mobileLogin.getResponse().getHeaders("Set-Cookie")).isEmpty();
        JsonNode mobileLoginBody = objectMapper.readTree(mobileLogin.getResponse().getContentAsString());
        String mobileAccess = mobileLoginBody.path("accessToken").asText();
        String mobileRefresh = mobileLoginBody.path("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Origin", gameOrigin)
                        .cookie(webAccess, webRefresh, webXsrf))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", gameOrigin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.id").value(user.getId()));

        String oldWebRefresh = webRefresh.getValue();
        MvcResult webRotation = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(webRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        Cookie rotatedWebAccess = requiredCookie(webRotation, "access_token");
        Cookie rotatedWebRefresh = requiredCookie(webRotation, "refresh_token");
        assertThat(findRefresh(oldWebRefresh).getRevokedAt()).isNotNull();
        assertThat(findRefresh(rotatedWebRefresh.getValue()).getRevokedAt()).isNull();

        MvcResult mobileRotation = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", mobileRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        assertThat(mobileRotation.getResponse().getHeaders("Set-Cookie")).isEmpty();
        JsonNode rotatedMobileBody = objectMapper.readTree(mobileRotation.getResponse().getContentAsString());
        String rotatedMobileRefresh = rotatedMobileBody.path("refreshToken").asText();
        assertThat(findRefresh(mobileRefresh).getRevokedAt()).isNotNull();
        assertThat(findRefresh(rotatedMobileRefresh).getRevokedAt()).isNull();

        mockMvc.perform(post("/api/me/password")
                        .cookie(rotatedWebAccess, rotatedWebRefresh, webXsrf)
                        .header("X-XSRF-TOKEN", webXsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", NEW_PASSWORD,
                                "confirmPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        MvcResult currentWebRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(rotatedWebRefresh))
                .andExpect(status().isOk())
                .andReturn();
        Cookie currentWebAccess = requiredCookie(currentWebRefresh, "access_token");
        Cookie currentWebRefreshCookie = requiredCookie(currentWebRefresh, "refresh_token");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", rotatedMobileRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));

        // Deliberate T-22 behavior: password change revokes other-session refresh tokens only;
        // a still-valid access token for that session remains usable until its <=15-minute expiry.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + mobileAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, NEW_PASSWORD)))
                .andExpect(status().isOk());

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .cookie(currentWebAccess, currentWebRefreshCookie, webXsrf)
                        .header("X-XSRF-TOKEN", webXsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertCookieExpired(logout, "access_token");
        assertCookieExpired(logout, "refresh_token");

        mockMvc.perform(post("/api/auth/refresh").cookie(currentWebRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void replayingRotatedWebRefreshTokenReturnsReuseDetected() throws Exception {
        User user = createUser("reuse-web-" + UUID.randomUUID() + "@example.test");
        MvcResult login = webLogin(user);
        Cookie originalRefresh = requiredCookie(login, "refresh_token");

        mockMvc.perform(post("/api/auth/refresh").cookie(originalRefresh))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(originalRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_REUSE_DETECTED"));
        assertNoActiveRefreshTokens(user.getId());
    }

    @Test
    void replayingRotatedMobileRefreshTokenReturnsReuseDetected() throws Exception {
        User user = createUser("reuse-mobile-" + UUID.randomUUID() + "@example.test");
        String originalRefresh = mobileLogin(user);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", originalRefresh))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", originalRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_REUSE_DETECTED"));
        assertNoActiveRefreshTokens(user.getId());
    }

    private MvcResult webLogin(User user) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String mobileLogin(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Type", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("refreshToken").asText();
    }

    private User createUser(String email) {
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(PASSWORD), "Lifecycle Test User"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));
        return user;
    }

    private JsonNode readMailpitMessages() throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode readMailpitMessage(String id) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private static String extractVerificationToken(String emailText) {
        Matcher matcher = Pattern.compile(VERIFY_LINK_PATTERN).matcher(emailText);
        assertThat(matcher.find()).as("verification link token in Mailpit email").isTrue();
        return matcher.group(1);
    }

    private String loginBody(String email, String password) throws Exception {
        return json(Map.of("email", email, "password", password));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Cookie requiredCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("response cookie %s", name).isNotNull();
        return cookie;
    }

    private void assertCookie(MvcResult result, String name, String path, String expectedAttribute) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow();
        assertThat(header).contains("Path=" + path);
        if (expectedAttribute != null) {
            assertThat(header).contains(expectedAttribute);
        }
    }

    private void assertCookieExpired(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow();
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains(name.equals("refresh_token") ? "Path=/api/auth" : "Path=/");
    }

    private AuthToken findRefresh(String rawToken) {
        String hash = new TokenHasher().hash(rawToken);
        return authTokenRepository.findAll().stream()
                .filter(token -> token.getTokenType() == TokenType.REFRESH && token.getTokenHash().equals(hash))
                .findFirst().orElseThrow();
    }

    private void assertNoActiveRefreshTokens(long userId) {
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.auth_tokens WHERE user_id = ? "
                        + "AND token_type = 'REFRESH' AND revoked_at IS NULL AND expires_at > now()",
                Integer.class, userId);
        assertThat(activeTokens).isZero();
    }
}
