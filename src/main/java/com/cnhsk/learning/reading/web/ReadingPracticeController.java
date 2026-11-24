package com.cnhsk.learning.reading.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.reading.ReadingPracticeService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadingPracticeController {
    private final ReadingPracticeService reading;
    private final AuthApi auth;

    public ReadingPracticeController(ReadingPracticeService reading, AuthApi auth) {
        this.reading = reading;
        this.auth = auth;
    }

    @Schema(name = "ReadingPracticeRequest", requiredProperties = "hskLevel")
    public record ReadingPracticeRequest(@Schema(minimum = "1", maximum = "3") Integer hskLevel,
            @Schema(allowableValues = {"MULTIPLE_CHOICE", "TRUE_FALSE"}) String qtype) {}

    @Schema(name = "ReadingPracticeNoQuestions")
    public record NoQuestions(@JsonInclude(JsonInclude.Include.ALWAYS) SessionView session, String reason) {}

    @PostMapping("/api/reading-practice/sessions")
    @Operation(operationId = "startReadingPractice", tags = "reading-practice")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Phiên luyện đọc đã tạo"),
            @ApiResponse(responseCode = "200", description = "Cấp này chưa có câu đọc (reason NO_QUESTIONS)"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public ResponseEntity<?> start(@RequestBody(required = false) ReadingPracticeRequest request) {
        if (request == null || request.hskLevel() == null || request.hskLevel() < 1 || request.hskLevel() > 3) {
            throw validation("hskLevel");
        }
        QuestionType type = switch (request.qtype() == null ? "" : request.qtype()) {
            case "" -> null;
            case "MULTIPLE_CHOICE" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE" -> QuestionType.TRUE_FALSE;
            default -> throw validation("qtype");
        };
        SessionView session = reading.start(auth.currentUser().userId(), request.hskLevel(), type);
        return session == null ? ResponseEntity.ok(new NoQuestions(null, "NO_QUESTIONS"))
                : ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }
}
