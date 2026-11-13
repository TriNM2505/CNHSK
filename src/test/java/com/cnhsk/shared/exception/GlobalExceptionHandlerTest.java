package com.cnhsk.shared.exception;

import com.cnhsk.shared.config.RequestIdFilter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.Map;
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
    void assistantErrorCodesUseExpectedStatusesAndMessages() throws Exception {
        mockMvc.perform(get("/test/assistant-blocked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ASSISTANT_BLOCKED_DURING_TEST"));
        mockMvc.perform(get("/test/assistant-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("ASSISTANT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Trợ lý tạm thời chưa trả lời được, bạn thử lại sau"));
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
    void translationUnavailableReturnsTheFixed503WithoutPrivateDetails() throws Exception {
        mockMvc.perform(get("/test/translation-unavailable").header(RequestIdFilter.HEADER_NAME, "translation-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_code").value("TRANSLATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Dịch tạm thời lỗi — lượt đã được hoàn."))
                .andExpect(jsonPath("$.request_id").value("translation-error"))
                .andExpect(jsonPath("$.details").doesNotExist());
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
    void knowledgeNodeNotFoundReturns404AndStandardError() throws Exception {
        mockMvc.perform(get("/test/knowledge-node-missing").header(RequestIdFilter.HEADER_NAME, "t10-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("KNOWLEDGE_NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy nút năng lực."))
                .andExpect(jsonPath("$.request_id").value("t10-not-found"));
    }

    @Test
    void lexemeNotFoundReturns404AndStandardError() throws Exception {
        mockMvc.perform(get("/test/lexeme-missing").header(RequestIdFilter.HEADER_NAME, "t07-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("LEXEME_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy mục từ."))
                .andExpect(jsonPath("$.request_id").value("t07-not-found"));
    }

    @Test
    void questionNotFoundReturns404AndStandardError() throws Exception {
        mockMvc.perform(get("/test/question-missing").header(RequestIdFilter.HEADER_NAME, "t11-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("QUESTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy câu hỏi."))
                .andExpect(jsonPath("$.request_id").value("t11-not-found"));
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
                "OPTION_NOT_IN_ITEM", "TOO_MANY_REPLAYS", "AUDIO_NOT_IN_ITEM", "SKIP_NOT_ALLOWED",
                "TOPIC_NOT_FOUND", "STAGE_NOT_FOUND", "STAGE_LOCKED", "STAGE_HAS_NO_UNITS", "STAGE_INCOMPLETE",
 "CHARACTER_NOT_FOUND", "NO_LEARNED_WORDS", "NO_AUDIO_WORDS", "NOT_ENOUGH_WORDS", "INVALID_FOCUS_WORDS",
                "NOT_ENOUGH_LEARNED", "RETRY_TOO_SOON", "GRAMMAR_POINT_NOT_FOUND", "STROKE_DATA_MISSING",
                "INVALID_ACCURACY",
                "IMPOSSIBLE_DURATION", "MODE_LOCKED", "HINT_USED_IN_RECALL", "PREVIEW_TOO_LONG",
                "AUDIO_NOT_AVAILABLE", "CHARACTER_REVEALED", "NOT_ENOUGH_CHARACTERS", "RESULT_COUNT_MISMATCH",
                "SELF_MODERATION", "SELF_REPORT", "POST_NOT_FOUND", "COMMENT_NOT_FOUND", "TARGET_NOT_FOUND",
                "REPORT_CASE_NOT_FOUND", "POST_NOT_EDITABLE", "POST_NOT_PENDING", "COMMENT_NOT_EDITABLE",
                "COMMENT_EDIT_WINDOW_EXPIRED", "COMMENT_NOT_RESTORABLE", "DUPLICATE_REPORT", "REPORT_ALREADY_HANDLED",
                "KNOWLEDGE_NODE_NOT_FOUND", "EXAM_NOT_FOUND", "EXAM_NOT_PUBLISHED", "EXAM_HAS_NO_QUESTIONS",
                "ATTEMPT_NOT_SUBMITTED", "PLACEMENT_BANK_INSUFFICIENT", "PLACEMENT_NOT_FINISHED",
                "LEXEME_NOT_FOUND", "QUESTION_NOT_FOUND", "MALFORMED_QUESTION", "KNOWLEDGE_NODE_REQUIRED",
                "GRADING_CHANGE_NOT_ALLOWED", "QUESTION_ARCHIVED", "COLLECTION_NOT_OWNED", "LIBRARY_LIMIT_REACHED",
                "NOTE_DUPLICATE", "NOTE_NOT_RESTORABLE", "CARD_DUPLICATE", "ASSISTANT_BLOCKED_DURING_TEST", "ASSISTANT_UNAVAILABLE",
                "CARD_BATCH_NOT_FOUND", "PLAN_NOT_AVAILABLE", "INSUFFICIENT_CREDITS", "TRANSLATE_UNAVAILABLE", "INVALID_CARD_CODE", "EMAIL_NOT_VERIFIED", "REFERENCE_NOT_FOUND", "INTERNAL_ERROR");
    }

    @Test
    void cardBatchErrorsKeepTheirStatusMessageAndRequestId() throws Exception {
        mockMvc.perform(get("/test/card-batch-missing").header(RequestIdFilter.HEADER_NAME, "batch-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("CARD_BATCH_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy lô mã."))
                .andExpect(jsonPath("$.request_id").value("batch-missing"));
        mockMvc.perform(get("/test/plan-unavailable").header(RequestIdFilter.HEADER_NAME, "plan-unavailable"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("PLAN_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("Gói PREMIUM hiện không khả dụng."))
                .andExpect(jsonPath("$.request_id").value("plan-unavailable"));
    }

    @Test
    void redeemErrorsPreserveStatusAndRetryWithoutLeakingProtectedFields() throws Exception {
        mockMvc.perform(get("/test/invalid-card").header(RequestIdFilter.HEADER_NAME, "redeem-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_CARD_CODE"))
                .andExpect(jsonPath("$.message").value("Mã thẻ không hợp lệ hoặc không thể sử dụng."))
                .andExpect(jsonPath("$.request_id").value("redeem-invalid"));
        mockMvc.perform(get("/test/redeem-limited").header(RequestIdFilter.HEADER_NAME, "redeem-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(17))
                .andExpect(jsonPath("$.request_id").value("redeem-limited"));
    }
    @Test
    void insufficientCreditsKeeps402CodeMessageAndRequestId() throws Exception {
        mockMvc.perform(get("/test/insufficient-credits").header(RequestIdFilter.HEADER_NAME, "billing-empty"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error_code").value("INSUFFICIENT_CREDITS"))
                .andExpect(jsonPath("$.message").value("Đã hết hạn mức và điểm."))
                .andExpect(jsonPath("$.request_id").value("billing-empty"));
    }

    @Test
    void malformedQuestionKeepsFieldDetailsWithoutOverridingProtectedErrorFields() throws Exception {
        mockMvc.perform(get("/test/malformed-question").header(RequestIdFilter.HEADER_NAME, "t12-protected"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("MALFORMED_QUESTION"))
                .andExpect(jsonPath("$.message").value("Câu hỏi không hợp lệ."))
                .andExpect(jsonPath("$.request_id").value("t12-protected"))
                .andExpect(jsonPath("$.details[0].field").value("options"))
                .andExpect(jsonPath("$.details[0].message").value("Chỉ có một đáp án đúng."));
    }

    @Test
    void noteLimitPreservesDetailsAndRejectsReservedExtraFields() throws Exception {
        mockMvc.perform(get("/test/note-limit").header(RequestIdFilter.HEADER_NAME, "notes-limit-test"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.kind").value("NOTES")).andExpect(jsonPath("$.details.limit").value(1000))
                .andExpect(jsonPath("$.request_id").value("notes-limit-test"));
    }

    @Test
    void noteDuplicatePreservesExistingNoteId() throws Exception {
        mockMvc.perform(get("/test/note-duplicate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("NOTE_DUPLICATE"))
                .andExpect(jsonPath("$.details.noteId").value(42)).andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void noteNotRestorableReturns409WithRequestId() throws Exception {
        mockMvc.perform(get("/test/note-not-restorable").header(RequestIdFilter.HEADER_NAME, "notes-restore-test"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("NOTE_NOT_RESTORABLE"))
                .andExpect(jsonPath("$.message").value("Ghi chú đã quá hạn khôi phục."))
                .andExpect(jsonPath("$.request_id").value("notes-restore-test"));
    }

    @Test
    void cardDuplicateReturns409WithoutDetailsAndProtectsStandardFields() throws Exception {
        mockMvc.perform(get("/test/card-duplicate").header(RequestIdFilter.HEADER_NAME, "card-duplicate-test"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("CARD_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Thẻ đã có trong bộ."))
                .andExpect(jsonPath("$.request_id").value("card-duplicate-test")).andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void cardLimitReturns422WithKindAndLimitDetails() throws Exception {
        mockMvc.perform(get("/test/card-limit").header(RequestIdFilter.HEADER_NAME, "card-limit-test"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LIBRARY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.request_id").value("card-limit-test"))
                .andExpect(jsonPath("$.details.kind").value("CARDS")).andExpect(jsonPath("$.details.limit").value(500));
    }

    @RestController
    static class ErrorTestController {
        @GetMapping("/test/invalid-card")
        void invalidCard() {
            throw new ApiException(ErrorCode.INVALID_CARD_CODE, HttpStatus.BAD_REQUEST,
                    "Mã thẻ không hợp lệ hoặc không thể sử dụng.", Map.of());
        }

        @GetMapping("/test/redeem-limited")
        void redeemLimited() {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Nhập sai quá nhiều lần.", Map.of("retryAfterSeconds", 17,
                            "error_code", "FORGED", "message", "FORGED", "request_id", "FORGED"));
        }

        @GetMapping("/test/translation-unavailable")
        void translationUnavailable() {
            throw new ApiException(ErrorCode.TRANSLATE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch tạm thời lỗi — lượt đã được hoàn.", Map.of());
        }

        @GetMapping("/test/insufficient-credits")
        void insufficientCredits() {
            throw new ApiException(ErrorCode.INSUFFICIENT_CREDITS, HttpStatus.PAYMENT_REQUIRED,
                    "Đã hết hạn mức và điểm.", Map.of());
        }

        @GetMapping("/test/card-batch-missing")
        void missingCardBatch() {
            throw new ApiException(ErrorCode.CARD_BATCH_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy lô mã.", Map.of());
        }

        @GetMapping("/test/plan-unavailable")
        void unavailablePlan() {
            throw new ApiException(ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Gói PREMIUM hiện không khả dụng.", Map.of());
        }
        @GetMapping("/test/assistant-blocked")
        void assistantBlocked() {
            throw new ApiException(ErrorCode.ASSISTANT_BLOCKED_DURING_TEST, HttpStatus.FORBIDDEN,
                    "Trợ lý tắt trong lúc làm bài.", Map.of());
        }

        @GetMapping("/test/assistant-unavailable")
        void assistantUnavailable() {
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Trợ lý tạm thời chưa trả lời được, bạn thử lại sau", Map.of());
        }

        @GetMapping("/test/card-duplicate")
        void cardDuplicate() {
            throw new ApiException(ErrorCode.CARD_DUPLICATE, HttpStatus.CONFLICT, "Thẻ đã có trong bộ.",
                    Map.of("details", Map.of("cardId", 42), "error_code", "FORGED", "message", "FORGED", "request_id", "FORGED"));
        }

        @GetMapping("/test/card-limit")
        void cardLimit() {
            throw new ApiException(ErrorCode.LIBRARY_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY, "Giới hạn thẻ.",
                    Map.of("details", Map.of("kind", "CARDS", "limit", 500)));
        }

        @GetMapping("/test/note-not-restorable")
        void noteNotRestorable() {
            throw new ApiException(ErrorCode.NOTE_NOT_RESTORABLE, HttpStatus.CONFLICT,
                    "Ghi chú đã quá hạn khôi phục.", Map.of());
        }

        @GetMapping("/test/malformed-question")
        void malformedQuestion() {
            throw new ApiException(ErrorCode.MALFORMED_QUESTION, HttpStatus.UNPROCESSABLE_ENTITY, "Câu hỏi không hợp lệ.",
                    Map.of("error_code", "FORGED", "message", "FORGED", "request_id", "FORGED",
                            "details", java.util.List.of(Map.of("field", "options", "message", "Chỉ có một đáp án đúng."))));
        }

        @GetMapping("/test/note-limit")
        void noteLimit() {
            throw new ApiException(ErrorCode.LIBRARY_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY, "Giới hạn ghi chú.",
                    Map.of("details", Map.of("kind", "NOTES", "limit", 1000), "error_code", "forged", "request_id", "forged"));
        }

        @GetMapping("/test/note-duplicate")
        void noteDuplicate() {
            throw new ApiException(ErrorCode.NOTE_DUPLICATE, HttpStatus.CONFLICT, "Ghi chú trùng.", Map.of("details", Map.of("noteId", 42)));
        }

        @GetMapping("/test/lexeme-missing")
        void missingLexeme() {
            throw new ApiException(ErrorCode.LEXEME_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy mục từ.", Map.of());
        }

        @GetMapping("/test/question-missing")
        void missingQuestion() {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi.", Map.of());
        }

        @GetMapping("/test/knowledge-node-missing")
        void missingKnowledgeNode() {
            throw new ApiException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy nút năng lực.", Map.of());
        }

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
