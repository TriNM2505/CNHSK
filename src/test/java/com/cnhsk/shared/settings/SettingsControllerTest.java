package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class SettingsControllerTest {
    private final SettingsAdminService service = mock(SettingsAdminService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private SettingWrite submitted;
    private static final String KEY = "billing.free-monthly-limit";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SettingsController(service, mapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        var user = new AuthenticatedUser(42, Set.of("ADMIN"), true, "fixture", null, Instant.EPOCH);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(service.list()).thenReturn(List.of(item()));
        when(service.change(anyString(), anyLong(), any())).thenAnswer(call -> {
            Supplier<SettingWrite> body = call.getArgument(2); submitted = body.get(); return item();
        });
        when(service.reset(anyString(), anyLong(), any())).thenAnswer(call -> {
            Supplier<SettingReset> body = call.getArgument(2); body.get(); return item();
        });
        when(service.history(anyString(), anyInt(), anyInt())).thenReturn(new SettingHistoryPage(List.of(), 0, 20, 0));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void listAndHistoryUseTheContractShapeAndPaginationDefaults() throws Exception {
        mvc.perform(get("/api/admin/settings")).andExpect(status().isOk()).andExpect(jsonPath("$[0].key").value(KEY));
        mvc.perform(get("/api/admin/settings/" + KEY + "/history")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        verify(service).history(KEY, 0, 20);
        mvc.perform(get("/api/admin/settings/" + KEY + "/history").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());
        verify(service).history(KEY, 2, 50);
    }

    @Test
    void actorComesFromThePrincipalAndTheBodyIsReadInsideTheService() throws Exception {
        mvc.perform(put("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":13,\"reason\":\"Điều chỉnh\",\"updatedBy\":999}"))
                .andExpect(status().isOk());
        verify(service).change(eq(KEY), eq(42L), any());
        assertThat(submitted).isEqualTo(new SettingWrite(13, "Điều chỉnh"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"13\"", "13.5", "null", "true", "{}", "[]", "2147483648", "-2147483649"})
    void valuesAreNeverCoercedIntoAnInteger(String raw) throws Exception {
        mvc.perform(put("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":" + raw + ",\"reason\":\"Thử\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("SETTING_OUT_OF_RANGE"));
    }

    @Test
    void missingValueReturnsTheSettingRangeError() throws Exception {
        mvc.perform(put("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("SETTING_OUT_OF_RANGE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{} {}"})
    void malformedJsonReturns422WithoutInternalDetails(String body) throws Exception {
        mvc.perform(put("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "13", "true", "{}", "[]"})
    void reasonMustBeAString(String raw) throws Exception {
        mvc.perform(put("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":13,\"reason\":" + raw + "}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownKeyIsRejectedBeforeReadingMalformedBody() throws Exception {
        doThrow(new ApiException(ErrorCode.SETTING_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy cài đặt.", Map.of()))
                .when(service).change(eq("jwt.secret"), anyLong(), any());
        mvc.perform(put("/api/admin/settings/jwt.secret").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("SETTING_NOT_FOUND"));
    }

    @Test
    void resetPassesTheReasonAndPrincipalAndReturns200() throws Exception {
        mvc.perform(delete("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Về mặc định\"}"))
                .andExpect(status().isOk());
        verify(service).reset(eq(KEY), eq(42L), any());
        mvc.perform(delete("/api/admin/settings/" + KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void paginationMustBeAnInteger(String field) throws Exception {
        mvc.perform(get("/api/admin/settings/" + KEY + "/history").param(field, "invalid"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void exactlyFourOperationsMatchTheApprovedContract() {
        var parsed = new OpenAPIV3Parser().readLocation(Path.of("specs/023-content-admin-settings/contracts/openapi-content-admin.yaml")
                .toAbsolutePath().toUri().toString(), null, null);
        assertThat(parsed.getMessages()).isEmpty();
        var contract = parsed.getOpenAPI();
        Set<String> operations = new TreeSet<>();
        for (var method : SettingsController.class.getDeclaredMethods()) {
            String verb;
            String[] values;
            if (method.isAnnotationPresent(GetMapping.class)) { verb = "GET"; values = method.getAnnotation(GetMapping.class).value(); }
            else if (method.isAnnotationPresent(PutMapping.class)) { verb = "PUT"; values = method.getAnnotation(PutMapping.class).value(); }
            else if (method.isAnnotationPresent(DeleteMapping.class)) { verb = "DELETE"; values = method.getAnnotation(DeleteMapping.class).value(); }
            else continue;
            String path = "/api/admin/settings" + (values.length == 0 ? "" : values[0]);
            operations.add(verb + " " + path);
            var expected = contract.getPaths().get(path).readOperationsMap().get(io.swagger.v3.oas.models.PathItem.HttpMethod.valueOf(verb));
            assertThat(method.getAnnotation(Operation.class).operationId()).isEqualTo(expected.getOperationId());
            assertThat(Arrays.stream(method.getAnnotation(ApiResponses.class).value()).map(value -> value.responseCode()).toList())
                    .containsExactlyInAnyOrderElementsOf(expected.getResponses().keySet());
        }
        assertThat(operations).containsExactlyInAnyOrder("GET /api/admin/settings", "PUT /api/admin/settings/{key}",
                "DELETE /api/admin/settings/{key}", "GET /api/admin/settings/{key}/history");
    }

    private SettingItem item() {
        return new SettingItem(KEY, "Thanh toán", "Lượt miễn phí mỗi tháng", "INT", 13, 10, 0, 1000, true,
                new UserRef(42, "Quản trị"), Instant.EPOCH);
    }
}
