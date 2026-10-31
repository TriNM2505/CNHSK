package com.cnhsk.learning.grammar.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.grammar.GrammarQueryService;
import com.cnhsk.learning.grammar.GrammarQueryService.GrammarLesson;
import com.cnhsk.learning.grammar.GrammarQueryService.GrammarSummary;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

    private final GrammarQueryService grammar;
    private final AuthApi auth;

    public GrammarController(GrammarQueryService grammar, AuthApi auth) {
        this.grammar = grammar;
        this.auth = auth;
    }

    @GetMapping
    @Operation(operationId = "listGrammarPoints", tags = "grammar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách điểm ngữ pháp"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public List<GrammarSummary> list(
            @Parameter(array = @ArraySchema(maxItems = 3, schema = @Schema(type = "integer", minimum = "1",
                    maximum = "9"))) @RequestParam(required = false) List<String> hskLevel) {
        List<String> requested = hskLevel == null ? List.of() : hskLevel;
        if (requested.size() > 3) throw validation("hskLevel");
        List<Integer> levels = requested.stream().map(value -> level(value, "hskLevel")).toList();
        return grammar.list(auth.currentUser().userId(), levels);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGrammarLesson", tags = "grammar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bài học ngữ pháp"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "404", description = "GRAMMAR_POINT_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public GrammarLesson detail(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return grammar.detail(auth.currentUser().userId(), positiveNumber(id, "id"));
    }

    private static int level(String value, String field) {
        long parsed = positiveNumber(value, field);
        if (parsed > 9) throw validation(field);
        return (int) parsed;
    }

    private static long positiveNumber(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw validation(field);
            return parsed;
        } catch (NumberFormatException exception) {
            throw validation(field);
        }
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }
}
