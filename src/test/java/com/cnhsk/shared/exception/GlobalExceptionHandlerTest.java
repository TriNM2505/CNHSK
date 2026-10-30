package com.cnhsk.shared.exception;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cnhsk.shared.config.RequestIdFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsContractErrorAndPreservesValidRequestId() throws Exception {
        mockMvc.perform(get("/test/api-error").header(RequestIdFilter.HEADER_NAME, "client-Request-123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Not allowed"))
                .andExpect(jsonPath("$.request_id").value("client-Request-123"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30))
                .andExpect(result -> assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME))
                        .isEqualTo("client-Request-123"));
    }

    @Test
    void validationFailureReturns422AndFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").value("Name is required"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void methodParameterValidationReturns422AndFieldDetails() throws Exception {
        mockMvc.perform(get("/test/parameter-validation").param("page", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"))
                .andExpect(jsonPath("$.details[0].message").value("Page must be positive"));
    }

    @Test
    void unexpectedFailureHidesExceptionDetailsAndGeneratesUuidRequestId() throws Exception {
        mockMvc.perform(get("/test/unexpected").header(RequestIdFilter.HEADER_NAME, "bad/request/id"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi nội bộ."))
                .andExpect(jsonPath("$.request_id").value(org.hamcrest.Matchers.matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("sensitive-database-detail", "trace", "stackTrace");
                    assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME))
                            .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
                });
    }

    @Test
    void errorCodeContainsEveryCodeFromTheSpecification() {
        assertThat(ErrorCode.values()).extracting(Enum::name).containsExactly(
                "VALIDATION_FAILED", "EMAIL_ALREADY_EXISTS", "INVALID_CREDENTIALS", "LOGIN_SOURCE_BLOCKED",
                "ACCOUNT_BANNED", "ACCOUNT_SUSPENDED", "ACCOUNT_UNVERIFIED", "INVALID_VERIFY_TOKEN",
                "EMAIL_ALREADY_VERIFIED", "INVALID_RESET_TOKEN", "UNAUTHENTICATED", "TOKEN_EXPIRED",
                "SESSION_REVOKED", "INVALID_REFRESH_TOKEN", "TOKEN_REUSE_DETECTED",
                "CURRENT_PASSWORD_INCORRECT", "PASSWORD_UNCHANGED", "FORBIDDEN", "SELF_ROLE_GRANT",
                "SELF_ROLE_REVOKE", "SELF_BAN", "ROLE_ALREADY_GRANTED", "ROLE_NOT_GRANTED",
                "REVOKE_LAST_ADMIN", "BAN_LAST_ADMIN", "USER_NOT_FOUND", "CSRF_TOKEN_INVALID",
                          "FILE_TOO_LARGE", "UNSUPPORTED_FILE_FORMAT", "ENCODING_ERROR", "TEMPLATE_MISMATCH",
                "EMPTY_FILE", "TOO_MANY_ROWS", "IMPORT_RUN_NOT_FOUND", "IMPORT_NOT_PENDING",
                "IMPORT_PREVIEW_EXPIRED", "NO_VALID_ROWS", "RERUN_CONFIRMATION_REQUIRED", "CONCURRENT_IMPORT",
                "TOO_MANY_REQUESTS", "ATTEMPT_NOT_OWNED", "RATE_LIMITED",
                "SESSION_EXPIRED", "SESSION_ALREADY_SUBMITTED", "ITEM_NOT_IN_SESSION", "ANSWER_ALREADY_SUBMITTED",
                "OPTION_NOT_IN_ITEM", "TOO_MANY_REPLAYS", "AUDIO_NOT_IN_ITEM", "SKIP_NOT_ALLOWED", "INTERNAL_ERROR");
    }

    @RestController
    static class ErrorTestController {

        @GetMapping("/test/api-error")
        void apiError() {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Not allowed",
                    Map.of("retryAfterSeconds", 30));
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody Input input) {
            // Request is rejected by Bean Validation before reaching this method.
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive-database-detail");
        }

        @GetMapping("/test/parameter-validation")
        String parameterValidation(@RequestParam @Min(value = 1, message = "Page must be positive") int page) {
            return Integer.toString(page);
        }
    }

    static class Input {
        @NotBlank(message = "Name is required")
        public String name;
    }
}
