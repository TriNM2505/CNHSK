package com.cnhsk.learning.knowledge;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.Filters;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository.LabelRow;
import com.cnhsk.learning.knowledge.repository.QuestionAdminQueryRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionAdminService {
    private final QuestionRepository questions;
    private final QuestionAdminQueryRepository queries;
    private final KnowledgePointRepository points;
    private final QuestionKnowledgePointRepository labels;
    private final EntityManager entities;
    private final AuditService audit;
    private final AuthApi auth;

    public QuestionAdminService(QuestionRepository questions, QuestionAdminQueryRepository queries,
            KnowledgePointRepository points, QuestionKnowledgePointRepository labels, EntityManager entities,
            AuditService audit, AuthApi auth) {
        this.questions = questions;
        this.queries = queries;
        this.points = points;
        this.labels = labels;
        this.entities = entities;
        this.audit = audit;
        this.auth = auth;
    }

    @Transactional(readOnly = true)
    public QuestionPage list(Filters filters, int page, int size) {
        var rows = queries.page(filters, page, size);
        return new QuestionPage(views(rows.items()), page, size, rows.totalElements());
    }

    @Transactional(readOnly = true)
    public QuestionView detail(long id) {
        Question question = questions.findById(id).orElseThrow(QuestionAdminService::notFound);
        return views(List.of(question)).get(0);
    }

    @Transactional
    public QuestionView update(long id, QuestionUpdate input) {
        return updateFrom(id, () -> input);
    }

    @Transactional
    public QuestionView updateFrom(long id, Supplier<QuestionUpdate> readBody) {
        Question question = questions.findByIdForUpdate(id).orElseThrow(QuestionAdminService::notFound);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.QUESTION_ARCHIVED, HttpStatus.CONFLICT, "Câu hỏi đã được lưu trữ.", Map.of());
        }
        QuestionUpdate input = readBody.get();
        if (input == null) throw malformed(List.of(Map.of("field", "body", "message", "Thiếu nội dung câu hỏi.")));
        var candidate = draft(question.getQtype(), input);
        Set<String> kids = new HashSet<>();
        kids.add(kid(input.primaryKid()));
        if (input.secondaryKids() != null) input.secondaryKids().forEach(value -> kids.add(kid(value)));
        if (input.options() != null) input.options().stream().filter(Objects::nonNull)
                .forEach(option -> kids.add(kid(option.errorKid())));
        kids.remove("");
        Map<String, Long> pointIds = kids.isEmpty() ? Map.of() : points.findByCodeIn(kids).stream()
                .collect(Collectors.toMap(point -> point.getCode(), point -> point.getId()));
        var checked = QuestionRules.validate(candidate, pointIds.keySet());
        if (checked.errors().stream().anyMatch(error -> error.field().equals("primaryKid") && error.code().equals("REQUIRED_MISSING"))) {
            throw new ApiException(ErrorCode.KNOWLEDGE_NODE_REQUIRED, HttpStatus.UNPROCESSABLE_ENTITY, "Câu hỏi cần có KID chính.", Map.of());
        }
        List<Map<String, String>> errors = new ArrayList<>(checked.errors().stream()
                .map(error -> Map.of("field", error.field(), "message", error.message())).toList());
        if (input.options() == null) errors.add(Map.of("field", "options", "message", "Thiếu danh sách phương án."));
        if (input.secondaryKids() == null) errors.add(Map.of("field", "secondaryKids", "message", "Thiếu danh sách KID phụ."));
        if (input.options() != null) {
            for (int index = 0; index < input.options().size(); index++) {
                var option = input.options().get(index);
                if (option != null && option.isCorrect() == null) errors.add(Map.of("field", "options[" + index + "].isCorrect", "message", "Thiếu cờ đáp án đúng."));
            }
        }
        if (!errors.isEmpty()) throw malformed(errors);
        var value = checked.value();
        Boolean used = null;
        var previous = new QuestionRules.Draft(question.getQtype(), question.getHskLevel(), question.getDifficulty(),
                question.getStem(), question.getOptions().stream().map(option -> new QuestionRules.Option(option.key(), option.text(),
                        option.isCorrect(), option.errorKid(), option.errorNote())).toList(), question.getCorrectKey(),
                question.getExplanation(), question.getAudioUrl(), question.getImageUrl(), "", List.of());
        if (!QuestionRules.gradingSignature(previous).equals(QuestionRules.gradingSignature(value))) {
            used = !questions.usedQuestionIds(Set.of(id)).isEmpty();
            if (used) throw new ApiException(ErrorCode.GRADING_CHANGE_NOT_ALLOWED, HttpStatus.CONFLICT,
                    "Câu đã có người làm — không đổi được cách chấm; tạo câu mới", Map.of());
        }
        var before = snapshot(question, queries.labels(Set.of(id)));
        question.setStem(value.stem()); question.setHskLevel((short) value.hskLevel()); question.setDifficulty((short) value.difficulty());
        question.setExplanation(value.explanation()); question.setCorrectKey(value.correctKey());
        question.setAudioUrl(value.audioUrl()); question.setImageUrl(value.imageUrl());
        question.setOptions(value.options().stream().map(option -> new QuestionOption(option.key(), option.text(), option.isCorrect(),
                option.errorKid(), option.errorNote())).toList());
        var oldLabels = labels.findByQuestionIdIn(Set.of(id));
        if (!oldLabels.isEmpty()) { labels.deleteAll(oldLabels); labels.flush(); }
        entities.persist(new QuestionKnowledgePoint(id, pointIds.get(value.primaryKid()), KnowledgeLabelRole.PRIMARY));
        value.secondaryKids().forEach(label -> entities.persist(new QuestionKnowledgePoint(id, pointIds.get(label), KnowledgeLabelRole.SECONDARY)));
        entities.flush();
        var afterLabels = queries.labels(Set.of(id));
        audit.record(auth.currentUser().userId(), "QUESTION_UPDATE", "question:" + id, before, snapshot(question, afterLabels));
        return new QuestionView(question, afterLabels, used == null ? !questions.usedQuestionIds(Set.of(id)).isEmpty() : used);
    }

    @Transactional
    public QuestionView archive(long id) {
        Question question = questions.findByIdForUpdate(id).orElseThrow(QuestionAdminService::notFound);
        var currentLabels = queries.labels(Set.of(id));
        if (question.getStatus() != QuestionStatus.ARCHIVED) {
            var before = snapshot(question, currentLabels);
            question.setStatus(QuestionStatus.ARCHIVED);
            audit.record(auth.currentUser().userId(), "QUESTION_ARCHIVE", "question:" + id, before, snapshot(question, currentLabels));
        }
        return new QuestionView(question, currentLabels, !questions.usedQuestionIds(Set.of(id)).isEmpty());
    }

    public static ApiException malformed(List<Map<String, String>> details) {
        return new ApiException(ErrorCode.MALFORMED_QUESTION, HttpStatus.UNPROCESSABLE_ENTITY,
                "Nội dung câu hỏi không hợp lệ.", Map.of("details", details));
    }

    private static String kid(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "").toUpperCase(Locale.ROOT);
    }

    private static QuestionRules.Draft draft(QuestionType type, QuestionUpdate input) {
        return new QuestionRules.Draft(type, input.hskLevel() == null ? 0 : input.hskLevel(), input.difficulty() == null ? 0 : input.difficulty(),
                input.stem(), input.options() == null ? List.of() : input.options().stream().map(option -> option == null ? null :
                        new QuestionRules.Option(option.key(), option.text(), Boolean.TRUE.equals(option.isCorrect()), option.errorKid(), option.errorNote())).toList(),
                input.correctKey(), input.explanation(), input.audioUrl(), input.imageUrl(), input.primaryKid(), input.secondaryKids());
    }

    private static QuestionSnapshot snapshot(Question question, List<LabelRow> labels) {
        return new QuestionSnapshot(question.getStem(), List.copyOf(question.getOptions()), question.getCorrectKey(), question.getExplanation(),
                question.getHskLevel(), question.getDifficulty(), question.getAudioUrl(), question.getImageUrl(), question.getStatus(), List.copyOf(labels));
    }

    private record QuestionSnapshot(String stem, List<QuestionOption> options, String correctKey, String explanation,
            int hskLevel, int difficulty, String audioUrl, String imageUrl, QuestionStatus status, List<LabelRow> labels) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "QuestionUpdate", requiredProperties = {"stem", "options", "explanation", "hskLevel", "difficulty", "primaryKid", "secondaryKids"})
    public record QuestionUpdate(@Schema(minLength = 1, maxLength = 2000) String stem,
            @ArraySchema(maxItems = 6) List<QuestionUpdateOption> options, @Schema(maxLength = 50) String correctKey,
            @Schema(minLength = 1, maxLength = 3000) String explanation, @Schema(minimum = "1", maximum = "9") Integer hskLevel,
            @Schema(minimum = "1", maximum = "3") Integer difficulty, @Schema(maxLength = 500) String audioUrl,
            @Schema(maxLength = 500) String imageUrl, String primaryKid, @ArraySchema(maxItems = 2) List<String> secondaryKids) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "QuestionUpdateOption", requiredProperties = {"key", "text", "isCorrect"})
    public record QuestionUpdateOption(@Schema(pattern = "^[A-F]$") String key,
            @Schema(minLength = 1, maxLength = 300) String text, Boolean isCorrect, String errorKid,
            @Schema(maxLength = 200) String errorNote) { }

    private List<QuestionView> views(List<Question> items) {
        if (items.isEmpty()) return List.of();
        Set<Long> ids = items.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, List<LabelRow>> labels = queries.labels(ids).stream().collect(Collectors.groupingBy(LabelRow::questionId));
        Set<Long> used = questions.usedQuestionIds(ids);
        return items.stream().map(question -> new QuestionView(question,
                labels.getOrDefault(question.getId(), List.of()), used.contains(question.getId()))).toList();
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.QUESTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy câu hỏi.", Map.of());
    }

    public record QuestionView(Question question, List<LabelRow> labels, boolean used) {
        public QuestionView { labels = List.copyOf(labels); }
    }
    public record QuestionPage(List<QuestionView> items, int page, int size, long totalElements) { }
}
