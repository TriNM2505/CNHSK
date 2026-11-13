package com.cnhsk.learning.quiz.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.quiz.QuizCatalog;
import com.cnhsk.learning.quiz.QuizCatalog.QuizListingPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "cookieAuth")
public class QuizController {
    private final QuizCatalog catalog;
    private final AuthApi auth;

    public QuizController(QuizCatalog catalog, AuthApi auth) {
        this.catalog = catalog;
        this.auth = auth;
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
