package com.cnhsk.learning.quiz;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizCatalog {
    private final QuizRepository repository;
    private final QuizConfig config;

    public QuizCatalog(QuizRepository repository, QuizConfig config) {
        this.repository = repository;
        this.config = config;
    }

    @Transactional(readOnly = true)
    public QuizListingPage list(long userId, Integer hsk, int page, int size) {
        if (hsk != null && (hsk < 1 || hsk > 9)) throw invalid("hsk");
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        int questionCount = config.getQuestionCount();
        var result = repository.list(userId, hsk, page, size, questionCount);
        List<QuizListing> items = result.items().stream().map(row -> new QuizListing(
                row.topicId(), row.topicName(), row.hskLevel(), questionCount, row.availableQuestions(),
                row.firstAttemptId() == null ? null : new FirstAttemptSummary(
                        row.firstScore(), row.firstSubmittedAt(), row.firstSuspicious()), row.bestRetryScore())).toList();
        return new QuizListingPage(items, page, size, result.total());
    }

    public static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(
                        Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "QuizListingPage")
    public record QuizListingPage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuizListing> items,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total) {
        public QuizListingPage { items = List.copyOf(items); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "QuizListing")
    public record QuizListing(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long topicId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String topicName,
            @Schema(minimum = "1", maximum = "9", requiredMode = Schema.RequiredMode.REQUIRED) int hskLevel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int questionCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long availableQuestions,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) FirstAttemptSummary myFirstAttempt,
            @Schema(types = {"integer", "null"}, minimum = "0", maximum = "100",
                    requiredMode = Schema.RequiredMode.REQUIRED) Long myBestRetryScore) { }

    @Schema(name = "FirstAttemptSummary")
    public record FirstAttemptSummary(
            @Schema(minimum = "0", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED) long score,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant submittedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean suspicious) { }
}
