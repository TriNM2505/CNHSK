package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.cnhsk.learning.credit.LedgerFilter;
import com.cnhsk.learning.credit.LedgerQueryService;
import com.cnhsk.learning.credit.ReconciliationService;
import com.cnhsk.learning.credit.web.LedgerAdminController;
import com.cnhsk.learning.credit.web.LedgerPage;
import com.cnhsk.learning.credit.web.LedgerTotals;
import com.cnhsk.learning.credit.web.Reconciliation;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = LedgerAdminController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class LedgerAdminSecurityConfigIntegrationTest {
    private static final String BASE = "/api/admin/credit-transactions";
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockitoBean private LedgerQueryService queries;
    @MockitoBean private ReconciliationService reconciliation;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach void fixtures() {
        when(clock.instant()).thenReturn(NOW);
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(queries.list(any(LedgerFilter.class), anyInt(), anyInt()))
                .thenReturn(new LedgerPage(List.of(), 0, 20, 0, new LedgerTotals(0, 0, 0)));
        when(queries.export(any(LedgerFilter.class))).thenReturn("header\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(reconciliation.reconcile()).thenReturn(new Reconciliation(0, 0, 0, 0, List.of()));
    }

    @Test void absentInvalidAndExpiredAuthenticationFailBeforeAnyServiceForAllThreeGets() throws Exception {
        String expired = token(List.of("ADMIN"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        for (String suffix : List.of("", "/export", "/reconciliation")) {
            error(call(suffix), 401, "UNAUTHENTICATED");
            error(call(suffix).header("Authorization", "Bearer invalid-token"), 401, "UNAUTHENTICATED");
            error(call(suffix).cookie(new Cookie("access_token", "invalid-token")), 401, "UNAUTHENTICATED");
            error(call(suffix).header("Authorization", "Bearer " + expired), 401, "TOKEN_EXPIRED");
            error(call(suffix).cookie(new Cookie("access_token", expired)), 401, "TOKEN_EXPIRED");
        }
        verifyNoInteractions(queries, reconciliation);
    }

    @ParameterizedTest @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "CONTENT_EDITOR", "ACCOUNTANT"})
    void everyNonAdminRoleIsDeniedForAllRoutesAndBothAuthenticationModesBeforeParsing(String role) throws Exception {
        String token = token(List.of(role));
        for (String suffix : List.of("", "/export", "/reconciliation")) {
            error(call(suffix).header("Authorization", "Bearer " + token), 403, "FORBIDDEN");
            error(call(suffix).cookie(new Cookie("access_token", token)), 403, "FORBIDDEN");
            error(get(BASE + suffix).param("from", "invalid").param("page", "overflow")
                    .header("Authorization", "Bearer " + token), 403, "FORBIDDEN");
        }
        verifyNoInteractions(queries, reconciliation);
    }

    @Test void mixedUnverifiedAdminUsesCookieOrBearerForEveryGetWithoutCsrf() throws Exception {
        String token = token(List.of("LEARNER", "ACCOUNTANT", "ADMIN"));
        for (String suffix : List.of("", "/export", "/reconciliation")) {
            mvc.perform(call(suffix).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mvc.perform(call(suffix).cookie(new Cookie("access_token", token))).andExpect(status().isOk());
        }
        ArgumentCaptor<LedgerFilter> filter = ArgumentCaptor.forClass(LedgerFilter.class);
        verify(queries, times(2)).list(filter.capture(), eq(0), eq(20));
        assertThat(filter.getAllValues()).allSatisfy(value -> {
            assertThat(value.from().toString()).isEqualTo("2031-11-15");
            assertThat(value.to().toString()).isEqualTo("2031-11-15");
            assertThat(value.type()).isNull();
        });
        verify(queries, times(2)).export(any(LedgerFilter.class));
        verify(reconciliation, times(2)).reconcile();
    }

    @Test void missingOrInvalidFiltersReturnJson422EvenWhenCsvIsRequestedBeforeTheService() throws Exception {
        for (String suffix : List.of("", "/export")) {
            for (MockHttpServletRequestBuilder request : List.of(get(BASE + suffix),
                    get(BASE + suffix).param("from", "2031-11-15"), get(BASE + suffix).param("to", "2031-11-15"),
                    call(suffix).param("type", ""), call(suffix).param("type", "topup"),
                    get(BASE + suffix).param("from", "2031-02-29").param("to", "2031-03-01"),
                    get(BASE + suffix).param("from", "2031-11-16").param("to", "2031-11-15"),
                    get(BASE + suffix).param("from", "2031-01-01").param("to", "2031-04-04")))
                error(admin(request.accept(suffix.equals("/export") ? "text/csv" : "application/json")),
                        422, "VALIDATION_FAILED");
        }
        for (String page : List.of("-1", "1.0", "wrong", "2147483648", "999999999999999999999"))
            error(admin(call("").param("page", page)), 422, "VALIDATION_FAILED");
        for (String size : List.of("0", "101", "-1", "1.0", "wrong", "2147483648"))
            error(admin(call("").param("size", size)), 422, "VALIDATION_FAILED");
        verifyNoInteractions(queries, reconciliation);
    }

    private static MockHttpServletRequestBuilder call(String suffix) {
        return get(BASE + suffix).param("from", "2031-11-15").param("to", "2031-11-15");
    }
    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token(List.of("ADMIN")));
    }
    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, false, UUID.randomUUID()); }
    private void error(MockHttpServletRequestBuilder request, int expected, String code) throws Exception {
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        Set<String> keys = new LinkedHashSet<>(); body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).isEqualTo(Set.of("error_code", "message", "request_id"));
        assertThat(body.path("error_code").textValue()).isEqualTo(code);
        assertThat(body.path("message").isTextual()).isTrue();
        assertThat(body.path("request_id").textValue()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace", "LedgerQueryService", "SELECT", "auth.users");
    }
}
