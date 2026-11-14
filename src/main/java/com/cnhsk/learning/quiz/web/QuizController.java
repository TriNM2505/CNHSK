package com.cnhsk.learning.quiz.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.quiz.QuizCatalog;
import com.cnhsk.learning.quiz.QuizCatalog.QuizListingPage;
import com.cnhsk.learning.quiz.QuizSessionService;
import com.cnhsk.learning.quiz.QuizSessionService.QuizSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "cookieAuth")
public class QuizController {
    private final QuizCatalog catalog;
    private final AuthApi auth;
    private final QuizSessionService sessions;

    public QuizController(QuizCatalog catalog, AuthApi auth, QuizSessionService sessions) {
        this.catalog = catalog;
        this.auth = auth;
        this.sessions = sessions;
    }

    @PostMapping(value = "/api/quizzes/{topicId}/attempts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "startQuiz", tags = "quiz")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phiên đang mở",
                    content = @Content(schema = @Schema(implementation = QuizSession.class))),
            @ApiResponse(responseCode = "201", description = "Phiên mới",
                    content = @Content(schema = @Schema(implementation = QuizSession.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN / CSRF_TOKEN_INVALID",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "QUIZ_NOT_AVAILABLE / VALIDATION_FAILED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public ResponseEntity<QuizSession> start(
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, format = "int64", minimum = "1"))
            @PathVariable String topicId) {
        long userId = auth.currentUser().userId();
        long id;
        try {
            id = Long.parseLong(topicId);
        } catch (NumberFormatException exception) {
            throw QuizCatalog.invalid("topicId");
        }
        var started = sessions.start(userId, id);
        return ResponseEntity.status(started.created() ? 201 : 200).body(started.session());
    }

    @GetMapping(value = "/api/quizzes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listQuizzes", tags = "quiz")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trang quiz"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))})
    public QuizListingPage list(
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hsk,
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", types = {"integer"}, minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(required = false) String size) {
        long userId = auth.currentUser().userId();
        return catalog.list(userId, hsk == null ? null : integer(hsk, "hsk"),
                page == null ? 0 : integer(page, "page"), size == null ? 20 : integer(size, "size"));
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw QuizCatalog.invalid(field);
        }
    }
}
