package com.cnhsk.learning.grammar.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.grammar.GrammarLessonService;
import com.cnhsk.learning.grammar.GrammarLessonService.LessonStart;
import com.cnhsk.learning.grammar.GrammarQueryService;
import com.cnhsk.learning.grammar.GrammarQueryService.GrammarLesson;
import com.cnhsk.learning.grammar.GrammarQueryService.GrammarSummary;
import com.cnhsk.learning.practice.PracticeService.FinishResult;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

    private final GrammarQueryService grammar;
    private final GrammarLessonService lessons;
    private final AuthApi auth;
    private final ObjectMapper mapper;

    public GrammarController(GrammarQueryService grammar, GrammarLessonService lessons, AuthApi auth,
            ObjectMapper mapper) {
        this.grammar = grammar;
        this.lessons = lessons;
        this.auth = auth;
        this.mapper = mapper;
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

    @PostMapping(value = "/{id}/lessons", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "startGrammarLesson", tags = "grammar")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tiếp tục lượt đang làm",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SessionView.class))),
            @ApiResponse(responseCode = "201", description = "Lượt mới; không có bài tập thì kèm finish",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(oneOf = {SessionView.class, FinishedLesson.class}))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER; cookie yêu cầu CSRF"),
            @ApiResponse(responseCode = "404", description = "GRAMMAR_POINT_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
    })
    public ResponseEntity<?> startLesson(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LessonRequest.class)))
            @RequestBody(required = false) String body) {
        long grammarId = positiveNumber(id, "id");
        int readDurationMs = readDurationMs(body);
        LessonStart result = lessons.start(auth.currentUser().userId(), grammarId, readDurationMs);
        if (!result.created()) return ResponseEntity.ok(result.session());
        if (result.finish() == null) return ResponseEntity.status(HttpStatus.CREATED).body(result.session());
        return ResponseEntity.status(HttpStatus.CREATED).body(new FinishedLesson(result.session(), result.finish()));
    }

    @Schema(name = "GrammarLessonRequest")
    public record LessonRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "3600000")
            int readDurationMs) {
    }

    public record FinishedLesson(SessionView session, FinishResult finish) {
    }

    /** Accepts only a JSON integer so "123" or 1.5 are not coerced; the range check stays in the service. */
    private int readDurationMs(String body) {
        if (body == null) throw validation("readDurationMs");
        JsonNode input;
        try {
            input = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (JsonProcessingException exception) {
            throw validation("readDurationMs");
        }
        if (input == null || !input.isObject()) throw validation("readDurationMs");
        JsonNode value = input.path("readDurationMs");
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw validation("readDurationMs");
        return value.intValue();
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
