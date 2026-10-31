package com.cnhsk.learning.hanzi.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.hanzi.WritingService;
import com.cnhsk.learning.hanzi.WritingService.WritingAttemptRequest;
import com.cnhsk.learning.hanzi.WritingService.WritingResult;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/writing/attempts")
public class WritingController {
    private final WritingService writing;
    private final AuthApi auth;

    public WritingController(WritingService writing, AuthApi auth) {
        this.writing = writing;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WritingResult record(@RequestBody(required = false) WritingAttemptRequest request) {
        return writing.record(auth.currentUser().userId(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidJson(HttpMessageNotReadableException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", ErrorCode.VALIDATION_FAILED.name());
        body.put("message", "Dữ liệu không hợp lệ.");
        body.put("request_id", RequestIdFilter.currentRequestId());
        body.put("details", List.of(Map.of("field", "request", "message", "JSON không hợp lệ.")));
        return ResponseEntity.unprocessableEntity().body(body);
    }
}
