package com.cnhsk.shared.exception;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cnhsk.shared.config.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception,
            HttpServletRequest request) {
        Map<String, Object> body = errorBody(exception.getCode(), exception.getMessage());
        exception.getExtra().forEach((key, value) -> {
            if (!"error_code".equals(key) && !"message".equals(key) && !"request_id".equals(key)) {
                if ("details".equals(key) && exception.getCode() == ErrorCode.TEMPLATE_MISMATCH) {
                    body.put(key, value);
                }
                if (!"details".equals(key) || exception.getCode() == ErrorCode.VALIDATION_FAILED) {
                    body.put(key, value);
                }
            }
        });
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleBindingException(BindException exception,
            HttpServletRequest request) {
        List<Map<String, String>> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::validationDetail)
                .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<Map<String, String>> details = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();
        return validationResponse(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException exception,
            HttpServletRequest request) {
        List<Map<String, String>> details = new ArrayList<>(exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                    String field = result.getMethodParameter().getParameterName();
                    if (field == null) {
                        field = "arg" + result.getMethodParameter().getParameterIndex();
                    }
                    return Map.of("field", field,
                            "message", error.getDefaultMessage() == null
                                    ? "Giá trị không hợp lệ." : error.getDefaultMessage());
                }))
                .toList());
        exception.getCrossParameterValidationResults().stream()
                .map(error -> Map.of("field", "request",
                        "message", error.getDefaultMessage() == null
                                ? "Giá trị không hợp lệ." : error.getDefaultMessage()))
                .forEach(details::add);
        return validationResponse(details, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception,
            HttpServletRequest request) {
        String requestId = RequestIdFilter.currentRequestId();
        log.error("Unhandled request failure request_id={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(ErrorCode.INTERNAL_ERROR, "Đã xảy ra lỗi nội bộ."));
    }

    private ResponseEntity<Map<String, Object>> validationResponse(List<Map<String, String>> details,
            HttpServletRequest request) {
        Map<String, Object> body = errorBody(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ.");
        body.put("details", details);
        return ResponseEntity.unprocessableEntity().body(body);
    }

    private Map<String, String> validationDetail(FieldError fieldError) {
        return Map.of("field", fieldError.getField(), "message",
                fieldError.getDefaultMessage() == null ? "Giá trị không hợp lệ." : fieldError.getDefaultMessage());
    }

    private Map<String, Object> errorBody(ErrorCode code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", code.name());
        body.put("message", message == null ? "Yêu cầu không thể xử lý." : message);
        body.put("request_id", RequestIdFilter.currentRequestId());
        return body;
    }
}
