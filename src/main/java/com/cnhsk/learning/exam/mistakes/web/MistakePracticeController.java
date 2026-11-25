package com.cnhsk.learning.exam.mistakes.web;

import java.util.List;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.exam.MistakePracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Luyện lỗi của tôi": the learner's own recent mistakes and a practice session built from them. */
@RestController
public class MistakePracticeController {
    private final MistakePracticeService mistakes;
    private final AuthApi auth;

    public MistakePracticeController(MistakePracticeService mistakes, AuthApi auth) {
        this.mistakes = mistakes;
        this.auth = auth;
    }

    @GetMapping("/api/mistake-practice")
    @Operation(operationId = "getMyMistakes", tags = "mistake-practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public MistakeSummary summary() {
        return new MistakeSummary(mistakes.recentMistakes(auth.currentUser().userId()));
    }

    @PostMapping("/api/mistake-practice/sessions")
    @Operation(operationId = "startMistakePractice", tags = "mistake-practice")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "No session: no recent mistakes or no questions"),
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "422", description = "Request cannot be processed"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<?> start() {
        var result = mistakes.start(auth.currentUser().userId());
        return result.session() == null ? ResponseEntity.ok(new NoSession(null, result.reason()))
                : ResponseEntity.status(HttpStatus.CREATED).body(result.session());
    }

    public record MistakeSummary(List<MistakePracticeService.MistakeNode> kids) { }

    public record NoSession(@JsonInclude(JsonInclude.Include.ALWAYS) SessionView session, String reason) { }
}
