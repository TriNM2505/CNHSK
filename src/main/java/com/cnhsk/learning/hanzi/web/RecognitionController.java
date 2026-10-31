package com.cnhsk.learning.hanzi.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.hanzi.RecognitionService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/characters")
public class RecognitionController {
    private final RecognitionService recognition;
    private final AuthApi auth;

    public RecognitionController(RecognitionService recognition, AuthApi auth) {
        this.recognition = recognition;
        this.auth = auth;
    }

    @PostMapping("/recognition-sessions")
    public ResponseEntity<?> start(@RequestBody(required = false) RecognitionRequest request) {
        if (request == null) {
            throw validation("request");
        }
        SessionView session = recognition.start(auth.currentUser().userId(), request.mode(), request.hskLevel(),
                request.count());
        return session == null
                ? ResponseEntity.ok(new NoCharacters(null, "NO_CHARACTERS"))
                : ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedRequest() {
        return ResponseEntity.unprocessableEntity().body(Map.of("error_code", ErrorCode.VALIDATION_FAILED.name(),
                "message", "Dữ liệu không hợp lệ.", "request_id", RequestIdFilter.currentRequestId(),
                "details", List.of(Map.of("field", "request", "message", "Dữ liệu không hợp lệ."))));
    }

    private ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }

    public record RecognitionRequest(String mode, Integer hskLevel, Integer count) {
    }

    private record NoCharacters(SessionView session, String reason) {
    }
}
