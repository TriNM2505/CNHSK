package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.credit.PlanAdminService;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanAdminController;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.learning.credit.web.PlanView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = PlanAdminController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class PlanAdminSecurityConfigIntegrationTest {
    private static final String BASE = "/api/admin/plans";
    private static final String CSRF = "synthetic-plan-csrf";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockitoBean private PlanAdminService service;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach void fixtures() {
        when(clock.instant()).thenReturn(Instant.parse("2031-11-15T10:00:00Z"));
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(service.listPlans()).thenReturn(List.of(view(PlanCode.FREE), view(PlanCode.PREMIUM),
                new PlanView(PlanCode.PREMIUM_PLUS, false, null, null, null, null, new Benefits(), null, 0)));
        when(service.updatePlan(any(PlanCode.class), any(PlanUpdateRequest.class))).thenReturn(view(PlanCode.PREMIUM));
    }

    @Test void missingAndMalformedBearerAuthenticationAre401BeforeTheService() throws Exception {
        for (MockHttpServletRequestBuilder call : List.of(get(BASE), write("PREMIUM", body())))
            error(call, 401, "UNAUTHENTICATED");
        for (MockHttpServletRequestBuilder call : List.of(get(BASE), write("PREMIUM", body())))
            error(call.header("Authorization", "Bearer invalid-token"), 401, "UNAUTHENTICATED");
        error(get(BASE).cookie(new Cookie("access_token", "invalid-token")), 401, "UNAUTHENTICATED");
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"LEARNER", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR"})
    void everyRoleWithoutAdminIs403ForBothRoutesAndAuthenticationModes(String role) throws Exception {
        String token = token(List.of(role));
        error(get(BASE).header("Authorization", "Bearer " + token), 403, "FORBIDDEN");
        error(write("PREMIUM", body()).header("Authorization", "Bearer " + token), 403, "FORBIDDEN");
        error(get(BASE).cookie(new Cookie("access_token", token)), 403, "FORBIDDEN");
        error(withCsrf(write("PREMIUM", body()), token), 403, "FORBIDDEN");
        verifyNoInteractions(service);
    }

    @Test void unsafeCookieRequestsPreserveCsrfBeforeAuthenticationAndRoleOrdering() throws Exception {
        Cookie invalid = new Cookie("access_token", "invalid-token");
        error(write("PREMIUM", body()).cookie(invalid), 403, "CSRF_TOKEN_INVALID");
        error(withCsrf(write("PREMIUM", body()), invalid.getValue()), 401, "UNAUTHENTICATED");
        String denied = token(List.of("ACCOUNTANT"));
        error(write("PREMIUM", body()).cookie(new Cookie("access_token", denied)), 403, "CSRF_TOKEN_INVALID");
        error(withCsrf(write("PREMIUM", body()), denied), 403, "FORBIDDEN");
        verifyNoInteractions(service);
    }

    @Test void mixedUnverifiedAdminCanGetThroughCookieOrBearerAndPutThroughBearerWithoutCsrf() throws Exception {
        String token = token(List.of("LEARNER", "ACCOUNTANT", "ADMIN"));
        response(get(BASE).header("Authorization", "Bearer " + token).param("userId", "999"), 200);
        response(get(BASE).cookie(new Cookie("access_token", token)), 200);
        response(write("PREMIUM", body().put("name", "  Trimmed name  "))
                .header("Authorization", "Bearer " + token), 200);
        verify(service, times(2)).listPlans();
        ArgumentCaptor<PlanUpdateRequest> request = ArgumentCaptor.forClass(PlanUpdateRequest.class);
        verify(service).updatePlan(eq(PlanCode.PREMIUM), request.capture());
        assertThat(request.getValue().name()).isEqualTo("Trimmed name");
        assertThat(request.getValue().price()).isEqualTo("0");
        assertThat(request.getValue().isActive()).isFalse();
    }

    @Test void cookiePutRequiresMatchingCsrfBeforeParsingOrCallingTheService() throws Exception {
        String token = token(List.of("ADMIN"));
        Cookie access = new Cookie("access_token", token);
        error(write("PREMIUM", body()).cookie(access), 403, "CSRF_TOKEN_INVALID");
        error(write("PREMIUM", body()).cookie(access, new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", "wrong-synthetic-csrf"), 403, "CSRF_TOKEN_INVALID");
        error(put(BASE + "/FREE").cookie(access).contentType(MediaType.APPLICATION_JSON).content("{"),
                403, "CSRF_TOKEN_INVALID");
        verifyNoInteractions(service);
        response(withCsrf(write("PREMIUM", body()), token), 200);
        verify(service).updatePlan(eq(PlanCode.PREMIUM), any(PlanUpdateRequest.class));
    }

    @Test void unrelatedXsrfCookieDoesNotMakeBearerOnlyPutRequireCsrf() throws Exception {
        response(admin(write("PREMIUM", body())).cookie(new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", "unrelated-header"), 200);
        verify(service).updatePlan(eq(PlanCode.PREMIUM), any(PlanUpdateRequest.class));
    }

    @Test void malformedNonObjectDuplicateAndTrailingJsonAreLocal422BeforeTheService() throws Exception {
        for (String body : List.of("", " ", "{", "[]", "null", "true", "3", "\"text\"", "{}",
                "{\"name\":\"one\",\"name\":\"two\"}", "{\"name\":\"one\"} {}", "{\"name\":\"one\"} garbage",
                "{\"name\":\"one\",\"benefits\":{\"GRADING\":0,\"GRADING\":1}}")) {
            error(admin(put(BASE + "/FREE").contentType(MediaType.APPLICATION_JSON).content(body)), 422, "VALIDATION_FAILED");
        }
        verifyNoInteractions(service);
    }

    @Test void premiumRequiresEveryNonNullFieldAndRejectsCoercionOverflowAndInvalidBenefitKeys() throws Exception {
        for (String field : List.of("name", "durationDays", "price", "benefits", "isActive")) {
            ObjectNode missing = body(); missing.remove(field);
            error(admin(write("PREMIUM", missing)), 422, "VALIDATION_FAILED");
            error(admin(write("PREMIUM", body().putNull(field))), 422, "VALIDATION_FAILED");
        }
        for (ObjectNode invalid : List.of(body().put("name", 1), body().put("description", false),
                body().put("durationDays", "30"), body().put("durationDays", 30.0), body().put("durationDays", Long.MAX_VALUE),
                body().put("price", 0), body().put("price", "1.000"), body().put("isActive", "false")))
            error(admin(write("PREMIUM", invalid)), 422, "VALIDATION_FAILED");
        for (String benefits : List.of("[]", "1", "\"limits\"", "{\"UNKNOWN\":0}", "{\"GRADING\":null}",
                "{\"GRADING\":0.0}", "{\"GRADING\":2147483648}", "{\"GRADING\":-1}", "{\"GRADING\":10001}")) {
            ObjectNode invalid = body(); invalid.set("benefits", mapper.readTree(benefits));
            error(admin(write("PREMIUM", invalid)), 422, "VALIDATION_FAILED");
        }
        verifyNoInteractions(service);
    }

    @Test void premiumAcceptsFalseZeroEmptyAndSparseBenefitsWithoutInventingMissingLimits() throws Exception {
        ObjectNode empty = body();
        response(admin(write("PREMIUM", empty)), 200);
        ObjectNode sparse = body();
        sparse.set("benefits", mapper.createObjectNode().put("GRADING", 10_000).put("TRANSLATE", 0));
        response(admin(write("PREMIUM", sparse)), 200);
        ArgumentCaptor<PlanUpdateRequest> request = ArgumentCaptor.forClass(PlanUpdateRequest.class);
        verify(service, times(2)).updatePlan(eq(PlanCode.PREMIUM), request.capture());
        assertThat(request.getAllValues().get(0).benefits()).isEmpty();
        assertThat(request.getAllValues().get(1).benefits()).containsExactlyInAnyOrderEntriesOf(Map.of("GRADING", 10_000, "TRANSLATE", 0));
        assertThat(request.getAllValues()).allSatisfy(value -> {
            assertThat(value.price()).isEqualTo("0");
            assertThat(value.isActive()).isFalse();
            assertThat(value.durationDays()).isEqualTo(1);
        });
    }

    @Test void freeAllowsOmittedOrNullNoneditableFieldsButRejectsSuppliedWrongJsonTypes() throws Exception {
        response(admin(write("FREE", mapper.createObjectNode().put("name", "Free"))), 200);
        ObjectNode nulls = mapper.createObjectNode().put("name", "Free").putNull("durationDays")
                .putNull("price").putNull("benefits").putNull("isActive").putNull("description");
        response(admin(write("FREE", nulls)), 200);
        ObjectNode ignored = body().put("name", "Free ignored fields").put("price", "abc")
                .put("durationDays", Long.MAX_VALUE).put("isActive", false);
        ignored.set("benefits", mapper.createObjectNode().put("UNKNOWN", -1).put("GRADING", 10001));
        response(admin(write("FREE", ignored)), 200);
        ArgumentCaptor<PlanUpdateRequest> request = ArgumentCaptor.forClass(PlanUpdateRequest.class);
        verify(service, times(3)).updatePlan(eq(PlanCode.FREE), request.capture());
        assertThat(request.getAllValues()).allSatisfy(value -> {
            assertThat(value.description()).isNull(); assertThat(value.durationDays()).isNull();
            assertThat(value.price()).isNull(); assertThat(value.benefits()).isNull(); assertThat(value.isActive()).isNull();
        });
        clearInvocations(service);
        for (ObjectNode invalid : List.of(mapper.createObjectNode().put("name", "Free").put("durationDays", "30"),
                mapper.createObjectNode().put("name", "Free").put("price", 0),
                mapper.createObjectNode().put("name", "Free").put("benefits", true),
                mapper.createObjectNode().put("name", "Free").put("isActive", "false")))
            error(admin(write("FREE", invalid)), 422, "VALIDATION_FAILED");
        verifyNoInteractions(service);
    }

    @Test void pathBindingAndBodyValidationReturnLocal422BeforeTheUnavailablePlanBranch() throws Exception {
        for (String code : List.of("premium", "UNKNOWN")) error(admin(write(code, body())), 422, "VALIDATION_FAILED");
        error(admin(write("PREMIUM_PLUS", mapper.createObjectNode())), 422, "VALIDATION_FAILED");
        verifyNoInteractions(service);
        when(service.updatePlan(eq(PlanCode.PREMIUM_PLUS), any(PlanUpdateRequest.class))).thenThrow(
                new ApiException(ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY, "Gói không khả dụng.", Map.of()));
        error(admin(write("PREMIUM_PLUS", body())), 422, "PLAN_NOT_AVAILABLE");
        verify(service).updatePlan(eq(PlanCode.PREMIUM_PLUS), any(PlanUpdateRequest.class));
    }

    @Test void inUseConflictUsesTheProtectedThreeFieldErrorEnvelope() throws Exception {
        when(service.updatePlan(eq(PlanCode.PREMIUM), any(PlanUpdateRequest.class))).thenThrow(
                new ApiException(ErrorCode.PLAN_IN_USE, HttpStatus.CONFLICT, "Gói đang có người sử dụng.", Map.of()));
        JsonNode error = error(admin(write("PREMIUM", body())), 409, "PLAN_IN_USE");
        assertThat(error.toString()).doesNotContain("stackTrace", "exception", "PlanAdminService", "SELECT", "auth.users");
        verify(service).updatePlan(eq(PlanCode.PREMIUM), any(PlanUpdateRequest.class));
    }

    private ObjectNode body() {
        ObjectNode body = mapper.createObjectNode().put("name", "Synthetic premium").put("durationDays", 1)
                .put("price", "0").put("isActive", false);
        body.set("benefits", mapper.createObjectNode());
        return body;
    }
    private MockHttpServletRequestBuilder write(String code, JsonNode body) throws Exception {
        return put(BASE + "/" + code).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder call) {
        return call.header("Authorization", "Bearer " + token(List.of("ADMIN")));
    }
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder call, String token) {
        return call.cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }
    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, false, UUID.randomUUID()); }
    private JsonNode response(MockHttpServletRequestBuilder call, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(call).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode error(MockHttpServletRequestBuilder call, int expectedStatus, String code) throws Exception {
        JsonNode error = response(call, expectedStatus);
        Set<String> keys = new LinkedHashSet<>(); error.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).isEqualTo(Set.of("error_code", "message", "request_id"));
        assertThat(error.path("error_code").textValue()).isEqualTo(code);
        assertThat(error.path("message").isTextual()).isTrue();
        assertThat(error.path("request_id").textValue()).isNotBlank();
        return error;
    }
    private static PlanView view(PlanCode code) {
        return new PlanView(code, true, code.name(), null, code == PlanCode.FREE ? null : 1, "0.00", new Benefits(), false, 0);
    }
}
