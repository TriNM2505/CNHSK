package com.cnhsk.learning.knowledge.web;

import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdate;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionView;
import com.cnhsk.learning.knowledge.QuestionAdminService;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/questions", produces = MediaType.APPLICATION_JSON_VALUE)
public class QuestionAdminController {
    private final QuestionAdminService questions;
    private final ObjectMapper mapper;

    public QuestionAdminController(QuestionAdminService questions, ObjectMapper mapper) {
        this.questions = questions;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "listQuestions", tags = "question-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kho câu hỏi"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ CONTENT_EDITOR, TEACHER"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public QuestionPage list(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9")) @RequestParam(required = false) String hskLevel,
            @Parameter(schema = @Schema(implementation = QuestionType.class)) @RequestParam(required = false) String qtype,
            @Parameter(schema = @Schema(implementation = QuestionStatus.class)) @RequestParam(required = false) String status,
            @RequestParam(required = false) String kid,
            @Parameter(schema = @Schema(minLength = 2, maxLength = 100)) @RequestParam(required = false) String q,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) @RequestParam(required = false) String page,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "10")) @RequestParam(required = false) String size) {
        if (q != null && (q.codePointCount(0, q.length()) < 2 || q.codePointCount(0, q.length()) > 100)) throw validation("q");
        Filters filters = new Filters(hskLevel == null ? null : integer(hskLevel, "hskLevel", 1, 9),
                enumeration(qtype, "qtype", QuestionType.class), enumeration(status, "status", QuestionStatus.class), kid, q);
        var result = questions.list(filters, page == null ? 0 : integer(page, "page", 0, Integer.MAX_VALUE),
                size == null ? 10 : integer(size, "size", 1, 100));
        return new QuestionPage(result.items().stream().map(QuestionAdminController::summary).toList(),
                result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getQuestion", tags = "question-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết câu hỏi"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ CONTENT_EDITOR, TEACHER"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND")
    })
    public QuestionDetail detail(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        return detail(questions.detail(questionId(id)));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateQuestion", tags = "question-admin",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @Schema(implementation = QuestionUpdate.class))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã lưu câu hỏi"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Sai quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "QUESTION_ARCHIVED hoặc GRADING_CHANGE_NOT_ALLOWED"),
            @ApiResponse(responseCode = "422", description = "MALFORMED_QUESTION hoặc KNOWLEDGE_NODE_REQUIRED")
    })
    public QuestionDetail update(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            HttpServletRequest request,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        // Read the body after ID lookup, row locking and the archived guard, including malformed JSON.
        return detail(questions.updateFrom(questionId(id), () -> readUpdate(request)));
    }

    @PatchMapping("/{id}/archive")
    @Operation(operationId = "archiveQuestion", tags = "question-admin")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Câu hỏi ở trạng thái ARCHIVED"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Sai quyền hoặc CSRF"),
            @ApiResponse(responseCode = "404", description = "QUESTION_NOT_FOUND")
    })
    public QuestionDetail archive(@Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrfToken) {
        return detail(questions.archive(questionId(id)));
    }

    private QuestionUpdate readUpdate(HttpServletRequest request) {
        try {
            return mapper.readValue(request.getInputStream(), QuestionUpdate.class);
        } catch (IOException exception) {
            throw QuestionAdminService.malformed(List.of(Map.of("field", "body", "message", "Nội dung JSON không hợp lệ.")));
        }
    }

    private static long questionId(String id) {
        long parsed;
        try {
            parsed = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw QuestionAdminService.notFound();
        }
        if (parsed <= 0) throw QuestionAdminService.notFound();
        return parsed;
    }

    private static int integer(String value, String field, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= minimum && parsed <= maximum) return parsed;
        } catch (NumberFormatException exception) {
            throw validation(field);
        }
        throw validation(field);
    }

    private static <E extends Enum<E>> E enumeration(String value, String field, Class<E> type) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw validation(field);
        }
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    private static QuestionSummary summary(QuestionView view) {
        Question question = view.question();
        String stem = question.getStem();
        String summary = stem.codePointCount(0, stem.length()) <= 120 ? stem : stem.substring(0, stem.offsetByCodePoints(0, 120));
        String primary = view.labels().stream().filter(label -> label.role() == KnowledgeLabelRole.PRIMARY)
                .map(label -> label.kid()).findFirst().orElse(null);
        List<String> secondary = view.labels().stream().filter(label -> label.role() == KnowledgeLabelRole.SECONDARY)
                .map(label -> label.kid()).toList();
        return new QuestionSummary(question.getId(), question.getCode(), question.getQtype(), question.getHskLevel(),
                question.getDifficulty(), summary, question.getSource(), question.getStatus(), primary, secondary, view.used());
    }

    private static QuestionDetail detail(QuestionView view) {
        Question question = view.question();
        List<QuestionOption> options = question.getOptions().stream().map(option -> new QuestionOption(option.key(),
                option.text(), option.isCorrect(), option.errorKid(), option.errorNote())).toList();
        List<QuestionLabel> labels = view.labels().stream().map(label -> new QuestionLabel(label.kid(), label.nameVi(), label.role())).toList();
        return new QuestionDetail(question.getId(), question.getCode(), question.getQtype(), question.getHskLevel(), question.getDifficulty(),
                question.getStem(), options, question.getCorrectKey(), question.getExplanation(), question.getAudioUrl(), question.getImageUrl(),
                question.getSource(), question.getStatus(), question.getReviewReason(), labels, view.used());
    }

    @Schema(name = "QuestionPage")
    public record QuestionPage(List<QuestionSummary> items, @Schema(minimum = "0") int page,
                               @Schema(minimum = "1", maximum = "100") int size, long totalElements) { }
    @Schema(name = "QuestionSummary")
    public record QuestionSummary(long id, String code, QuestionType qtype, @Schema(minimum = "1", maximum = "9") int hskLevel,
                                  @Schema(minimum = "1", maximum = "3") int difficulty, @Schema(maxLength = 120) String stemSummary,
                                  QuestionSource source, QuestionStatus status, String primaryKid, List<String> secondaryKids, boolean used) { }
    @Schema(name = "QuestionOption")
    public record QuestionOption(@Schema(pattern = "^[A-F]$") String key, @Schema(maxLength = 300) String text,
                                 boolean isCorrect, String errorKid, @Schema(maxLength = 200) String errorNote) { }
    @Schema(name = "QuestionLabel")
    public record QuestionLabel(String kid, String nameVi, KnowledgeLabelRole role) { }
    @Schema(name = "QuestionDetail")
    public record QuestionDetail(long id, String code, QuestionType qtype, @Schema(minimum = "1", maximum = "9") int hskLevel,
                                 @Schema(minimum = "1", maximum = "3") int difficulty, String stem, List<QuestionOption> options,
                                 String correctKey, String explanation, String audioUrl, String imageUrl, QuestionSource source,
                                 QuestionStatus status, String reviewReason, List<QuestionLabel> labels, boolean used) { }
}
