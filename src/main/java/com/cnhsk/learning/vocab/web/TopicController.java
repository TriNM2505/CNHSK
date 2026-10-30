package com.cnhsk.learning.vocab.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.vocab.TopicQueryService;
import com.cnhsk.learning.vocab.TopicQueryService.TopicDetail;
import com.cnhsk.learning.vocab.TopicQueryService.TopicSummary;
import com.cnhsk.learning.vocab.TopicStudyService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicQueryService topics;
    private final AuthApi auth;
    private final TopicStudyService study;

    public TopicController(TopicQueryService topics, AuthApi auth, TopicStudyService study) {
        this.topics = topics;
        this.auth = auth;
        this.study = study;
    }

    @GetMapping
    @Operation(operationId = "listTopics", tags = "topics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách chủ đề"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public List<TopicSummary> list(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9"))
            @RequestParam(required = false) String hskLevel) {
        long userId = auth.currentUser().userId();
        Integer level = null;
        if (hskLevel != null) {
            long parsed = positiveNumber(hskLevel, "hskLevel");
            if (parsed > 9) throw validation("hskLevel");
            level = (int) parsed;
        }
        return topics.list(userId, level);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTopic", tags = "topics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết chủ đề"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER được truy cập"),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public TopicDetail detail(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        long userId = auth.currentUser().userId();
        return topics.detail(userId, positiveNumber(id, "id"));
    }

    @PostMapping("/{id}/study-sessions")
    @Operation(operationId = "startTopicStudy", tags = "topics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tiếp tục lượt hoặc đã học hết từ",
                    content = @Content(schema = @Schema(oneOf = {SessionView.class, NoNewWords.class}))),
            @ApiResponse(responseCode = "201", description = "Lượt học mới",
                    content = @Content(schema = @Schema(implementation = SessionView.class))),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER; cookie yêu cầu CSRF"),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED")
    })
    public ResponseEntity<?> startStudy(
            @Parameter(schema = @Schema(type = "integer", format = "int64")) @PathVariable String id) {
        long userId = auth.currentUser().userId();
        var result = study.start(userId, positiveNumber(id, "id"));
        if (result.session() == null) return ResponseEntity.ok(new NoNewWords(null, "NO_NEW_WORDS"));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.session());
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NoNewWords(SessionView session, String reason) {
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
