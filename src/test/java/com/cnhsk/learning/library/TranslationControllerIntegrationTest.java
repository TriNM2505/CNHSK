package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.library.web.TranslateRequest.Language;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.library.web"})
@Import(TranslationServiceIntegrationTest.TimeConfiguration.class)
class TranslationControllerIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private Clock clock;
    @MockitoSpyBean private StubClaudeClient stub;
    @MockitoSpyBean private TranslationService service;
    private TranslationTestData data;
    private long user;
    private int calls;

    @BeforeEach void setup() {
        data = new TranslationTestData(jdbc, redis, clock);
        user = data.user(0, 0);
        calls = stub.callCount();
    }
    @AfterEach void cleanup() { data.cleanup(); }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "null", "[]", "{}", "{", "{} {}",
            "{\"text\":\"\",\"from\":\"ZH\",\"to\":\"VI\"}",
            "{\"text\":\" \\n\\t\",\"from\":\"ZH\",\"to\":\"VI\"}",
            "{\"text\":\"我\",\"from\":\"ZH\",\"to\":\"ZH\"}",
            "{\"text\":\"我\",\"from\":\"VI\",\"to\":\"VI\"}",
            "{\"text\":\"我\",\"from\":\"EN\",\"to\":\"VI\"}",
            "{\"text\":\"我\",\"from\":\"zh\",\"to\":\"VI\"}",
            "{\"text\":\"我\",\"to\":\"VI\"}",
            "{\"text\":null,\"from\":\"ZH\",\"to\":\"VI\"}",
            "{\"text\":12,\"from\":\"ZH\",\"to\":\"VI\"}",
            "{\"text\":\"我\",\"from\":null,\"to\":\"VI\"}",
            "{\"text\":\"我\",\"from\":\"ZH\",\"to\":3}"})
    void AT18_invalidInputIsAlways422BeforeTheTranslationService(String body) throws Exception {
        String before = data.usageJson(user);
        var request = post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(body);
        mvc.perform(request).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Nhập nội dung 1–2.000 ký tự và chọn hai ngôn ngữ ZH, VI khác nhau."))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        assertThat(data.usageJson(user)).isEqualTo(before);
        assertThat(stub.callCount() - calls).isZero();
        verify(service, never()).translate(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"中", "𠀀"})
    void AT18_2001CodePointsAreRejectedBut2000AfterStripAreAccepted(String point) throws Exception {
        String tooLong = mapper.writeValueAsString(Map.of("text", point.repeat(2001), "from", "ZH", "to", "VI"));
        mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(tooLong))
                .andExpect(status().isUnprocessableEntity());
        assertThat(data.used(user)).isZero();
        assertThat(stub.callCount() - calls).isZero();
        var valid = data.request(" \n" + point.repeat(2000) + "\t ", Language.ZH);
        stub.enqueue(data.completion("Bản dịch biên 2.000."));
        mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("text", " \n" + point.repeat(2000) + "\t ", "from", "ZH", "to", "VI"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.translated").value("Bản dịch biên 2.000."))
                .andExpect(jsonPath("$.cached").value(false)).andExpect(jsonPath("$.consumption").value("QUOTA"));
        assertThat(data.used(user)).isEqualTo(1);
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(redis.hasKey(data.key(valid))).isTrue();
    }

    @Test
    void AT18_cacheHitHasExplicitNullConsumptionAndResponseHasNoHistoryFields() throws Exception {
        String body = mapper.writeValueAsString(data.uniqueRequest());
        stub.enqueue(data.completion("Kết quả không có lịch sử."));
        mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        var response = mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true)).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(fields(json)).containsExactlyInAnyOrder("translated", "pinyin", "segments", "cached", "consumption");
        assertThat(json.has("consumption")).isTrue();
        assertThat(json.path("consumption").isNull()).isTrue();
        assertThat(data.used(user)).isEqualTo(1);
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @Test
    void AT19_402NeverCallsClaudeAnd503RefundsWithTheFixedPublicMessage() throws Exception {
        data.usage(user, 10);
        String body = mapper.writeValueAsString(data.uniqueRequest());
        mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_CREDITS"));
        assertThat(stub.callCount() - calls).isZero();
        data.usage(user, 0);
        stub.enqueueFailure(AiUnavailableException.Reason.NOT_CONFIGURED);
        mvc.perform(post("/api/translate").header("Authorization", bearer("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("TRANSLATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(TranslationService.UNAVAILABLE_MESSAGE))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        assertThat(data.used(user)).isZero();
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @Test
    void securityRejectsUnauthenticatedUsersAndCookieRequestsWithoutCsrf() throws Exception {
        String body = mapper.writeValueAsString(data.uniqueRequest());
        mvc.perform(post("/api/translate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        Cookie access = new Cookie("access_token", token("LEARNER"));
        mvc.perform(post("/api/translate").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/translate").cookie(access, new Cookie("XSRF-TOKEN", "translation-csrf"))
                .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(data.used(user)).isZero();
        assertThat(stub.callCount() - calls).isZero();
        verify(service, never()).translate(anyLong(), any());
        stub.enqueue(data.completion("Dịch qua cookie hợp lệ."));
        mvc.perform(post("/api/translate").cookie(access, new Cookie("XSRF-TOKEN", "translation-csrf"))
                .header("X-XSRF-TOKEN", "translation-csrf").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT"})
    void securityRequiresLearnerEvenForAdministrator(String role) throws Exception {
        mvc.perform(post("/api/translate").header("Authorization", bearer(role)).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(data.uniqueRequest()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        assertThat(data.used(user)).isZero();
        assertThat(stub.callCount() - calls).isZero();
        verify(service, never()).translate(anyLong(), any());
    }

    @Test
    void contractDocumentsTranslateStatusesAndKeepsIdsIntegersAndCachedBoolean() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode api = mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        var operation = api.path("paths").path("/api/translate").path("post");
        assertThat(fields(operation.path("responses"))).contains("200", "401", "402", "403", "422", "503");
        var schemas = api.path("components").path("schemas");
        var input = schemas.path("TranslateRequest");
        assertThat(input.path("properties").path("text").path("maxLength").intValue()).isEqualTo(2000);
        assertThat(input.path("required").toString()).contains("text", "from", "to");
        var result = schemas.path("TranslateResult");
        assertThat(types(result.path("properties").path("cached"))).contains("boolean").doesNotContain("string");
        assertThat(result.path("required").toString()).contains("translated", "segments", "cached", "consumption");
        var id = schemas.path("Segment").path("properties").path("lexemeId");
        assertThat(types(id)).contains("integer", "null").doesNotContain("string");
        assertThat(id.path("format").textValue()).isEqualTo("int64");
        assertThat(stub.callCount() - calls).isZero();
    }

    private Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }
    private Set<String> types(JsonNode node) {
        Set<String> result = new HashSet<>();
        var type = node.path("type");
        if (type.isTextual()) result.add(type.textValue());
        else type.forEach(value -> result.add(value.textValue()));
        return result;
    }
    private String token(String role) { return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID()); }
    private String bearer(String role) { return "Bearer " + token(role); }
}
