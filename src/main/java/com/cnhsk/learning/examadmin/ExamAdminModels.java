package com.cnhsk.learning.examadmin;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import com.cnhsk.learning.knowledge.entity.Question;
import com.fasterxml.jackson.databind.JsonNode;

public final class ExamAdminModels {
    private ExamAdminModels() { }

    public enum ExamStatus { DRAFT, PUBLISHED, ARCHIVED }
    public enum SectionType { LISTENING, READING, WRITING }
    public enum PublicationErrorCode {
        EMPTY_SECTION, QUESTION_NOT_APPROVED, QUESTION_WITHOUT_KID,
        QUESTION_UNSUPPORTED, LISTENING_AUDIO_MISSING, SCORE_MISMATCH, SECTION_TYPE_MISSING
    }

    @Schema(name = "ExamSection")
    public record Section(@NotBlank @Size(max = 100) String name, @NotNull SectionType type,
            @Min(0) @Max(180) int durationMin) { }
    @Schema(name = "ExamSectionView")
    public record SectionView(@Schema(minLength = 1, maxLength = 100) String name,
            @Schema(nullable = true, description = "null = đề nhập từ 012 chưa có thông tin") SectionType type,
            @Schema(nullable = true, minimum = "0", maximum = "180", description = "null = đề nhập từ 012 chưa có thông tin") Integer durationMin) { }
    @Schema(name = "ExamQuestionWrite")
    public record QuestionWrite(@Positive long questionId, @Min(0) @Max(4) int sectionIndex,
            @Min(1) @Max(100) int points) { }
    public record ExamWrite(@NotNull @Pattern(regexp = "^[A-Z0-9-]{1,50}$") String code,
            @NotBlank @Size(max = 200) String title, @Min(1) @Max(6) int hskLevel,
            @Min(1) @Max(1000) int totalScore, @NotNull @Size(min = 1, max = 5) List<@Valid Section> sections,
            @NotNull @Size(max = 300) List<@Valid QuestionWrite> questions) { }
    public record ExamUpdate(@NotNull @Pattern(regexp = "^[A-Z0-9-]{1,50}$") String code,
            @NotBlank @Size(max = 200) String title, @Min(1) @Max(6) int hskLevel,
            @Min(1) @Max(1000) int totalScore, @NotNull @Size(min = 1, max = 5) List<@Valid Section> sections,
            @NotNull @Size(max = 300) List<@Valid QuestionWrite> questions, @NotNull String etag) {
        public ExamWrite write() { return new ExamWrite(code, title, hskLevel, totalScore, sections, questions); }
    }
    @Schema(name = "CloneBody")
    public record CloneWrite(@NotNull @Pattern(regexp = "^[A-Z0-9-]{1,50}$") String code,
            @Size(max = 200) String title) { }
    public record EtagBody(@NotNull String etag) { }
    @Schema(name = "ExamQuestionView")
    public record QuestionView(long questionId, int sectionIndex, int orderIndex, int points,
            String code, String qtype, String status, String kid, boolean hasAudio) { }
    public record PublicationError(PublicationErrorCode code, String message, Integer sectionIndex, Long questionId) { }
    public record ExamDetail(long id, String code, String title, int hskLevel, int totalScore,
            List<SectionView> sections, ExamStatus status, Instant publishedAt, String etag,
            List<QuestionView> questions, List<PublicationError> publicationErrors) { }
    @Schema(name = "AdminExamSummary")
    public record ExamSummary(long id, String code, String title, int hskLevel, ExamStatus status,
            Instant publishedAt, int questionCount, boolean inUse) { }
    @Schema(name = "AdminExamPage")
    public record ExamPage(List<ExamSummary> items, int page, int size, long totalElements) { }

    record ExamRow(long id, String code, String title, int hskLevel, int totalScore,
            JsonNode sections, ExamStatus status, Instant publishedAt) { }
    record Link(long questionId, int sectionIndex, int orderIndex, int points) { }
    record QuestionData(Link link, Question question, String kid) { }
    record SummaryRow(ExamRow exam, int questionCount) { }
    record Listing(List<SummaryRow> items, long totalElements) { }
}
