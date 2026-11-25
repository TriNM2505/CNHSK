package com.cnhsk.learning.aigen;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AiRunControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/ai-generation-runs";
    private static final Instant NOW = AiRunQueryServiceIntegrationTest.NOW;
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired StubClaudeClient provider;
    @MockitoSpyBean AiGenerationGuard guard;
    @MockitoSpyBean WeakKnowledgeSelector selector;
    @MockitoBean SettingsService settings;
    @MockitoBean Clock clock;
    long actor;
    int calls;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        actor = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) VALUES (?,?,'Run HTTP fixture',?) RETURNING id",
                Long.class, "run-http-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, Timestamp.from(NOW));
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", actor);
        when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(Math.toIntExact(actor));
        when(settings.getInt(SettingKey.AI_GENERATE_BATCH_SIZE)).thenReturn(5);
        when(settings.getInt(SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN)).thenReturn(3);
        when(settings.getInt(SettingKey.AI_GENERATE_MIN_BANK)).thenReturn(10);
        when(settings.getInt(SettingKey.AI_GENERATE_WINDOW_DAYS)).thenReturn(14);
        when(settings.getInt(SettingKey.AI_GENERATE_MIN_WRONG)).thenReturn(20);
        calls = provider.callCount();
    }

    @AfterEach
    void cleanupOwnedRows() {
        jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", actor);
        jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", actor);
        jdbc.update("DELETE FROM auth.users WHERE id=?", actor);
        assertThat(provider.callCount()).isEqualTo(calls);
    }

    @Test
    void anonymousRequestsAre401AndEveryNonAdminRoleIs403BeforeParsing() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/trigger")).andExpect(status().isUnauthorized());
        for (String role : List.of("TEACHER", "CONTENT_EDITOR", "LEARNER", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(get(ROOT).param("page", "bad").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            mvc.perform(post(ROOT + "/trigger").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void adminsCanReadWithEitherCredentialAndMixedRolesWithoutCsrf() throws Exception {
        long id = request("RUNNING", null, null);
        for (boolean cookie : List.of(false, true)) {
            MockHttpServletRequestBuilder request = get(ROOT);
            if (cookie) request.cookie(new Cookie("access_token", token("ADMIN")));
            else request.header("Authorization", bearer("ADMIN", "TEACHER"));
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(id))
                    .andExpect(jsonPath("$.items[0].status").value("RUNNING"));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void invalidListParametersHaveOriginal422ErrorEnvelope(String field, String value) throws Exception {
        mvc.perform(get(ROOT).param(field, value).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value(field)).andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    static Stream<Arguments> invalidParameters() {
        return Stream.of(Arguments.of("page", "-1"), Arguments.of("page", ""), Arguments.of("page", "abc"),
                Arguments.of("page", "1.5"), Arguments.of("page", "2147483648"), Arguments.of("size", "0"),
                Arguments.of("size", "51"), Arguments.of("size", ""), Arguments.of("size", "abc"),
                Arguments.of("size", "1.5"), Arguments.of("size", "2147483648"), Arguments.of("status", "PENDING"),
                Arguments.of("status", "ASSIGNED"), Arguments.of("status", "EXPIRED"), Arguments.of("status", "failed"),
                Arguments.of("status", ""), Arguments.of("status", " FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "COMPLETED", "FAILED"})
    void acceptsOnlyOriginalStatusValuesAndKeepsExactSafeResponseShape(String state) throws Exception {
        request(state, "{\"questionIds\":[1],\"dropped\":[{},{}],\"inputTokens\":3,\"outputTokens\":5}", null);
        JsonNode json = response(get(ROOT).param("status", state).param("size", "50"));
        assertThat(fields(json)).containsExactlyInAnyOrder("enabled", "embeddingReady", "monthTokens", "items", "page", "size", "totalElements");
        assertThat(fields(json.path("items").get(0))).containsExactlyInAnyOrder("id", "kid", "status", "createdAt", "completedAt",
                "questionCount", "droppedCount", "droppedReasons", "inputTokens", "outputTokens", "error");
        assertThat(json.path("items").get(0).path("status").asText()).isEqualTo(state);
        assertThat(json.path("items").get(0).path("completedAt").isNull()).isTrue();
        assertThat(json.path("items").get(0).path("error").isNull()).isTrue();
        assertThat(json.path("items").get(0).path("questionCount").asInt()).isEqualTo(1);
        assertThat(json.path("items").get(0).path("droppedCount").asInt()).isEqualTo(2);
        assertThat(json.path("monthTokens").asLong()).isEqualTo(8);
    }

    @Test
    void unknownStoredErrorAndPrivatePayloadNeverReachHttpResponseOrLogs(CapturedOutput output) throws Exception {
        String privateValue = "private-http-" + UUID.randomUUID();
        long id = request("FAILED", mapper.writeValueAsString(Map.of("response", privateValue, "inputTokens", 17)), privateValue);
        jdbc.update("UPDATE learning.service_requests SET payload=CAST(? AS jsonb) WHERE id=?",
                mapper.writeValueAsString(Map.of("kid", "NP1-001", "prompt", privateValue, "user", privateValue)), id);
        JsonNode json = response(get(ROOT));
        assertThat(json.path("items").get(0).path("error").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain(privateValue, "prompt", "payload", "userId", "response");
        assertThat(output.getAll()).contains("unsupported error code").doesNotContain(privateValue);
    }

    @Test
    void disabledTriggerReturns409AndHistoryRemainsReadable() throws Exception {
        when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(0);
        mvc.perform(post(ROOT + "/trigger").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("AI_GENERATION_DISABLED"));
        mvc.perform(get(ROOT).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, actor)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void heldSharedGuardReturnsBusy409AndGetRemainsReadOnly() throws Exception {
        try (var lease = guard.tryAcquire().orElseThrow()) {
            mvc.perform(post(ROOT + "/trigger").header("Authorization", bearer("ADMIN")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("AI_GENERATION_BUSY"));
            response(get(ROOT));
            lease.assertOwned();
        }
    }

    @Test
    void cookieTriggerRequiresMatchingCsrfBeforeAdmissionAndBearerNeedsNoCsrf() throws Exception {
        when(settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID)).thenReturn(0);
        for (String value : List.of("", "wrong")) {
            var request = post(ROOT + "/trigger").cookie(new Cookie("access_token", token("ADMIN")), new Cookie("XSRF-TOKEN", "run-csrf"));
            if (!value.isEmpty()) request.header("X-XSRF-TOKEN", value);
            mvc.perform(request).andExpect(status().isForbidden());
        }
        mvc.perform(post(ROOT + "/trigger").cookie(new Cookie("access_token", token("ADMIN")), new Cookie("XSRF-TOKEN", "run-csrf"))
                        .header("X-XSRF-TOKEN", "run-csrf"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("AI_GENERATION_DISABLED"));
        mvc.perform(post(ROOT + "/trigger").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("AI_GENERATION_DISABLED"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void acceptedBearerAndCookieTriggersReturn202WhileRealWorkerStillHoldsSharedLease(boolean cookie) throws Exception {
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<AiGenerationGuard.Lease> acquired = (Optional<AiGenerationGuard.Lease>) invocation.callRealMethod();
            return acquired.map(real -> {
                AiGenerationGuard.Lease observed = spy(real);
                doAnswer(close -> {
                    try { return close.callRealMethod(); }
                    finally { released.countDown(); }
                }).when(observed).close();
                return observed;
            });
        }).when(guard).tryAcquire();
        doAnswer(invocation -> {
            selected.countDown();
            assertThat(proceed.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(selector).selectForGeneration();
        try {
            var request = post(ROOT + "/trigger");
            if (cookie) request.cookie(new Cookie("access_token", token("ADMIN")), new Cookie("XSRF-TOKEN", "accepted-csrf"))
                    .header("X-XSRF-TOKEN", "accepted-csrf");
            else request.header("Authorization", bearer("ADMIN", "TEACHER"));
            String body = mvc.perform(request).andExpect(status().isAccepted()).andExpect(jsonPath("$.started").value(true))
                    .andReturn().getResponse().getContentAsString();
            assertThat(fields(mapper.readTree(body))).containsExactly("started");
            assertThat(selected.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(released.getCount()).isEqualTo(1);
            assertThat(guard.tryAcquire()).isEmpty();
            response(get(ROOT));
        } finally {
            proceed.countDown();
            assertThat(released.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE user_id=?", Long.class, actor)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void guardInfrastructureFailureReturnsSafe500RatherThanAcceptedOrBusy(CapturedOutput output) throws Exception {
        doThrow(new AiGenerationGuard.GuardException()).when(guard).tryAcquire();
        mvc.perform(post(ROOT + "/trigger").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        assertThat(output.getAll()).doesNotContain("GENERATION_LOCK_UNAVAILABLE");
    }

    private String token(String... roles) { return jwt.issueAccessToken(actor, List.of(roles), true, UUID.randomUUID()); }
    private String bearer(String... roles) { return "Bearer " + token(roles); }
    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request.header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private static List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
    private long request(String status, String result, String error) {
        return jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,result,error_message,created_at)"
                        + " VALUES (?,'AI_GENERATE',?,CAST(? AS jsonb),CAST(? AS jsonb),?,?) RETURNING id", Long.class,
                actor, status, "{\"kid\":\"NP1-001\"}", result, error, Timestamp.from(NOW));
    }
}
