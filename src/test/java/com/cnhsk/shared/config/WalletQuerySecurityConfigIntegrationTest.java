package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.cnhsk.learning.credit.RedeemService;
import com.cnhsk.learning.credit.WalletQueryService;
import com.cnhsk.learning.credit.web.*;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WalletController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class WalletQuerySecurityConfigIntegrationTest {
    private static final String WALLET = "/api/me/credits";
    private static final String HISTORY = "/api/me/credit-transactions";
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private RedeemService redeem;
    @MockitoBean private WalletQueryService queries;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2031-11-15T10:00:00Z"));
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(queries.history(anyInt(), anyInt())).thenAnswer(invocation -> {
            int page = invocation.getArgument(0), size = invocation.getArgument(1);
            if (page < 0 || size < 1 || size > 100) throw WalletQueryService.invalidPagination();
            return new TransactionPage(List.of(), page, size, 0);
        });
        when(queries.wallet()).thenReturn(new Wallet(0, new PlanStatus(PlanStatus.PlanCode.FREE, "Free", null, true),
                List.of(), Instant.parse("2031-11-30T17:00:00Z")));
    }

    @Test void missingAndMalformedAuthenticationAre401BeforeQuery() throws Exception {
        for (String path : List.of(WALLET, HISTORY)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
            mvc.perform(get(path).header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(new Cookie("access_token", "invalid-token"))).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queries, redeem);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "MODERATOR", "CONTENT_EDITOR"})
    void everyNonLearnerIs403ForBothRoutes(String role) throws Exception {
        String token = token(List.of(role));
        for (String path : List.of(WALLET, HISTORY)) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            mvc.perform(get(path).cookie(new Cookie("access_token", token))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(queries, redeem);
    }

    @Test void unverifiedMixedLearnerUsesCookieOrBearerWithoutCsrfOrClientIdentity() throws Exception {
        String token = token(List.of("LEARNER", "ADMIN"));
        for (String path : List.of(WALLET, HISTORY)) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token).param("userId", "999"))
                    .andExpect(status().isOk());
            mvc.perform(get(path).cookie(new Cookie("access_token", token)))
                    .andExpect(status().isOk());
        }
        verify(queries, times(2)).wallet();
        verify(queries, times(2)).history(0, 20);
        verifyNoInteractions(redeem);
    }

    @Test void missingPlan401UsesTheErrorEnvelopeForBothRoutes() throws Exception {
        ApiException missing = new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                "Không thể xác thực người dùng hiện tại.", java.util.Map.of());
        when(queries.wallet()).thenThrow(missing);
        when(queries.history(0, 20)).thenThrow(missing);
        for (String path : List.of(WALLET, HISTORY)) {
            mvc.perform(get(path).header("Authorization", bearer())).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.request_id").isString());
        }
    }

    @Test void paginationAbsentAndEmptyUseDefaultsWhileWhitespaceIsValidationFailed() throws Exception {
        mvc.perform(get(HISTORY).header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        for (String name : List.of("page", "size")) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param(name, ""))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        }
        mvc.perform(get(HISTORY).header("Authorization", bearer()).param("page", "").param("size", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        for (String name : List.of("page", "size")) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param(name, " "))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.request_id").isString());
        }
        verify(queries, times(4)).history(0, 20);
    }

    @Test void paginationBoundsAre422AndSupportedBoundaryValuesAreAccepted() throws Exception {
        for (String[] parameters : List.of(new String[]{"-1", "20"}, new String[]{"0", "0"}, new String[]{"0", "101"})) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param("page", parameters[0]).param("size", parameters[1]))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        for (String size : List.of("1", "100")) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param("page", "2147483647").param("size", size))
                    .andExpect(status().isOk());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"x", "1.5", "2147483648"})
    void nonConvertibleScalarsAre422WithoutLeakingTheirValue(String value) throws Exception {
        for (String name : List.of("page", "size")) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param(name, value))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.request_id").isString());
        }
        verifyNoInteractions(queries, redeem);
    }

    @Test void repeatedParametersThatCannotConvertAre422() throws Exception {
        for (String name : List.of("page", "size")) {
            mvc.perform(get(HISTORY).header("Authorization", bearer()).param(name, "invalid", "also-invalid"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(queries, redeem);
    }

    private String bearer() { return "Bearer " + token(List.of("LEARNER")); }
    private String token(List<String> roles) { return jwt.issueAccessToken(42, roles, false, UUID.randomUUID()); }
}
