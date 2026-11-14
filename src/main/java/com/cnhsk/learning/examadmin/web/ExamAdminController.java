package com.cnhsk.learning.examadmin.web;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static com.cnhsk.learning.examadmin.ExamAdminService.invalid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.examadmin.ExamAdminService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/admin/exams", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExamAdminController {
    private final ExamAdminService service;
    private final ObjectMapper mapper;

    public ExamAdminController(ExamAdminService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "adminListExams", tags = "exam-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Danh sách đề"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public ExamPage list(
            @RequestParam(required = false) @Parameter(schema = @Schema(implementation = ExamStatus.class)) String status,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "6")) String hsk,
            @RequestParam(required = false) @Parameter(schema = @Schema(maxLength = 100)) String q,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) String page,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) String size) {
        ExamStatus filter = null;
        if (status != null) {
            try { filter = ExamStatus.valueOf(status); }
            catch (IllegalArgumentException exception) { throw invalid("status"); }
        }
        return service.list(filter, hsk == null ? null : number(hsk, "hsk"), q,
                page == null ? 0 : number(page, "page"), size == null ? 20 : number(size, "size"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminCreateExam", tags = "exam-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ExamWrite.class))))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đề nháp mới"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EXAM_CODE_TAKEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public ExamDetail create(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf) {
        return service.create(user.userId(), write(read(request)));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "adminGetExam", tags = "exam-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chi tiết đề"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "404", description = "EXAM_NOT_FOUND")})
    public ExamDetail detail(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id) {
        return service.detail(id(id));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "adminUpdateExam", tags = "exam-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ExamUpdate.class))))
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã lưu đề nháp"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "EXAM_NOT_FOUND hoặc QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EXAM_NOT_EDITABLE, EDIT_CONFLICT hoặc EXAM_CODE_TAKEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public ExamDetail update(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id,
            HttpServletRequest request, @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf) {
        return service.update(id(id), () -> {
            JsonNode body = read(request);
            String etag = text(body, "etag");
            ExamWrite value = write(body);
            return new ExamUpdate(value.code(), value.title(), value.hskLevel(), value.totalScore(), value.sections(), value.questions(), etag);
        });
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminCloneExam", tags = "exam-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CloneWrite.class))))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Bản sao nháp"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "EXAM_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "EXAM_CODE_TAKEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public ExamDetail clone(@PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf) {
        JsonNode body = read(request);
        return service.clone(id(id), user.userId(), new CloneWrite(text(body, "code"),
                body.hasNonNull("title") ? text(body, "title") : null));
    }

    private JsonNode read(HttpServletRequest request) {
        try {
            JsonNode node = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(request.getInputStream());
            if (node == null || !node.isObject()) throw invalid("body");
            return node;
        } catch (IOException exception) {
            throw invalid("body");
        }
    }

    private ExamWrite write(JsonNode body) {
        JsonNode sections = body.path("sections");
        JsonNode questions = body.path("questions");
        if (!sections.isArray()) throw invalid("sections");
        if (!questions.isArray()) throw invalid("questions");
        List<Section> parts = new ArrayList<>();
        for (JsonNode section : sections) {
            SectionType type;
            try { type = SectionType.valueOf(text(section, "type")); }
            catch (IllegalArgumentException exception) { throw invalid("sections.type"); }
            parts.add(new Section(text(section, "name"), type, integer(section, "durationMin")));
        }
        List<QuestionWrite> items = new ArrayList<>();
        for (JsonNode question : questions) {
            JsonNode id = question.path("questionId");
            if (!id.isIntegralNumber() || !id.canConvertToLong()) throw invalid("questions.questionId");
            items.add(new QuestionWrite(id.longValue(), integer(question, "sectionIndex"), integer(question, "points")));
        }
        return new ExamWrite(text(body, "code"), text(body, "title"), integer(body, "hskLevel"),
                integer(body, "totalScore"), List.copyOf(parts), List.copyOf(items));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw invalid(field);
        return value.textValue();
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid(field);
        return value.intValue();
    }

    private int number(String raw, String field) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException exception) { throw invalid(field); }
    }

    private long id(String raw) {
        try { long value = Long.parseLong(raw); if (value > 0) return value; }
        catch (NumberFormatException exception) { /* Invalid identifiers have the same response as missing exams. */ }
        throw new ApiException(ErrorCode.EXAM_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy đề thi.", Map.of());
    }
}
