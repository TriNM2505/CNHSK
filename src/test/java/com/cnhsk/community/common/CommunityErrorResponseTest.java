package com.cnhsk.community.common;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies shared error transport for the community contract. */
class CommunityErrorResponseTest {

    private static final String REQUEST_ID = "community-error-request-123";
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}: {1} returns {2}")
    @MethodSource("communityErrors")
    void FR041_communityErrorResponsesFollowSpecificationMatrix(String requirement, ErrorCode code,
            HttpStatus expectedStatus, String message) throws Exception {
        HandledError handled = handle(new ApiException(code, expectedStatus, message, Map.of()), REQUEST_ID);

        assertThat(handled.response().getStatusCode()).as(requirement).isEqualTo(expectedStatus);
        assertThat(handled.response().getBody()).containsOnlyKeys("error_code", "message", "request_id");
        JsonNode json = serialize(handled.response());
        assertThat(json.path("error_code").asText()).isEqualTo(code.name());
        assertThat(json.path("message").asText()).isEqualTo(message).isNotBlank();
        assertThat(json.path("request_id").asText()).isEqualTo(REQUEST_ID);
        assertThat(handled.responseRequestId()).isEqualTo(REQUEST_ID);
        assertThat(json.toString()).doesNotContain("stackTrace", "trace", "exception", "sql", "email",
                "reporterId", "com.cnhsk");
    }

    @Test
    void FR008_FR019_FR035_rateLimitResponsePreservesRetryAfterSeconds() throws Exception {
        HandledError handled = handle(new ApiException(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS,
                "Bạn đã vượt giới hạn thao tác. Vui lòng thử lại sau.", Map.of("retryAfterSeconds", 60)), REQUEST_ID);

        assertThat(handled.response().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handled.response().getBody())
                .containsOnlyKeys("error_code", "message", "request_id", "retryAfterSeconds");
        JsonNode json = serialize(handled.response());
        assertThat(json.path("error_code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(json.path("message").asText()).isEqualTo("Bạn đã vượt giới hạn thao tác. Vui lòng thử lại sau.");
        assertThat(json.path("request_id").asText()).isEqualTo(REQUEST_ID);
        assertThat(json.path("retryAfterSeconds").isIntegralNumber()).isTrue();
        assertThat(json.path("retryAfterSeconds").asInt()).isEqualTo(60);
    }

    @Test
    void FR041_errorExtrasCannotOverwriteProtectedEnvelopeFields() throws Exception {
        HandledError handled = handle(new ApiException(ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN,
                "Bạn không thể kiểm duyệt nội dung của mình.",
                Map.of("error_code", "REPLACED", "message", "Replaced", "request_id", "replaced-request")),
                REQUEST_ID);

        assertThat(handled.response().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handled.response().getBody()).containsOnlyKeys("error_code", "message", "request_id");
        JsonNode json = serialize(handled.response());
        assertThat(json.path("error_code").asText()).isEqualTo("SELF_MODERATION");
        assertThat(json.path("message").asText()).isEqualTo("Bạn không thể kiểm duyệt nội dung của mình.");
        assertThat(json.path("request_id").asText()).isEqualTo(REQUEST_ID);
    }

    @Test
    void FR015_errorResponseUsesGeneratedRequestIdWhenHeaderIsInvalid() throws Exception {
        HandledError handled = handle(new ApiException(ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy bài viết.", Map.of()), "invalid/request/id");

        JsonNode json = serialize(handled.response());
        assertThat(json.path("request_id").asText()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(handled.responseRequestId()).isEqualTo(json.path("request_id").asText());
    }

    private HandledError handle(ApiException exception, String suppliedRequestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, suppliedRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ResponseEntity<Map<String, Object>>> captured = new AtomicReference<>();

        new RequestIdFilter().doFilter(request, response, (filteredRequest, filteredResponse) ->
                captured.set(handler.handleApiException(exception, (HttpServletRequest) filteredRequest)));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(captured.get()).isNotNull();
        return new HandledError(captured.get(), response.getHeader(RequestIdFilter.HEADER_NAME));
    }

    private JsonNode serialize(ResponseEntity<Map<String, Object>> response) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsBytes(response.getBody()));
    }

    private static Stream<Arguments> communityErrors() {
        return Stream.of(
                Arguments.of("FR-041, FR-043, FR-049, FR-054", ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN,
                        "Bạn không thể kiểm duyệt nội dung của mình."),
                Arguments.of("FR-035", ErrorCode.SELF_REPORT, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Bạn không thể báo cáo nội dung của mình."),
                Arguments.of("FR-015, FR-017, FR-028, FR-040, FR-042", ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết."),
                Arguments.of("FR-021, FR-052", ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy bình luận."),
                Arguments.of("FR-033", ErrorCode.TARGET_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy nội dung cần báo cáo."),
                Arguments.of("FR-047", ErrorCode.REPORT_CASE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ báo cáo."),
                Arguments.of("FR-010", ErrorCode.POST_NOT_EDITABLE, HttpStatus.CONFLICT,
                        "Bài viết không thể chỉnh sửa."),
                Arguments.of("FR-042", ErrorCode.POST_NOT_PENDING, HttpStatus.CONFLICT,
                        "Bài viết không còn chờ duyệt."),
                Arguments.of("FR-024", ErrorCode.COMMENT_NOT_EDITABLE, HttpStatus.CONFLICT,
                        "Bình luận không thể chỉnh sửa."),
                Arguments.of("FR-024", ErrorCode.COMMENT_EDIT_WINDOW_EXPIRED, HttpStatus.CONFLICT,
                        "Đã hết thời gian chỉnh sửa bình luận."),
                Arguments.of("FR-053", ErrorCode.COMMENT_NOT_RESTORABLE, HttpStatus.CONFLICT,
                        "Bình luận không thể khôi phục."),
                Arguments.of("FR-036", ErrorCode.DUPLICATE_REPORT, HttpStatus.CONFLICT,
                        "Bạn đã báo cáo nội dung này."),
                Arguments.of("FR-050", ErrorCode.REPORT_ALREADY_HANDLED, HttpStatus.CONFLICT,
                        "Hồ sơ báo cáo đã được xử lý."));
    }

    private record HandledError(ResponseEntity<Map<String, Object>> response, String responseRequestId) {
    }
}
