package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.cnhsk.learning.api.ExamUsageGuard;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAdminService {
    private final ExamAdminStore store;
    private final ExamPublicationValidator validator;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final List<ExamUsageGuard> guards;

    public ExamAdminService(ExamAdminStore store, ExamPublicationValidator validator, AuditService audit,
            ObjectMapper mapper, List<ExamUsageGuard> guards) {
        this.store = store;
        this.validator = validator;
        this.audit = audit;
        this.mapper = mapper;
        this.guards = List.copyOf(guards);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExamPage list(ExamStatus status, Integer hsk, String query, int page, int size) {
        if (hsk != null && (hsk < 1 || hsk > 6)) throw invalid("hsk");
        if (query != null && query.codePointCount(0, query.length()) > 100) throw invalid("q");
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        Listing result = store.list(status, hsk, query, page, size);
        return new ExamPage(result.items().stream().map(row -> {
            ExamRow exam = row.exam();
            return new ExamSummary(exam.id(), exam.code(), exam.title(), exam.hskLevel(), exam.status(),
                    exam.publishedAt(), row.questionCount(), guards.stream().anyMatch(guard -> guard.isInUse(exam.id())));
        }).toList(), page, size, result.totalElements());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExamDetail detail(long id) {
        return view(required(store.find(id, false)));
    }

    @Transactional
    public ExamDetail create(long actorId, ExamWrite input) {
        validate(input);
        requireQuestions(input.questions());
        long id = insert(actorId, input.code(), input.title(), input.hskLevel(), input.totalScore(), store.sections(input.sections()));
        store.replaceLinks(id, links(input));
        return view(required(store.find(id, false)));
    }

    @Transactional
    public ExamDetail update(long id, Supplier<ExamUpdate> body) {
        ExamRow current = required(store.find(id, true));
        if (current.status() != ExamStatus.DRAFT) {
            throw new ApiException(ErrorCode.EXAM_NOT_EDITABLE, HttpStatus.CONFLICT, "Chỉ được sửa đề nháp.", Map.of());
        }
        ExamUpdate input = body.get();
        if (input == null || input.etag() == null) throw invalid("etag");
        if (!etag(current, store.links(id)).equals(input.etag())) {
            throw new ApiException(ErrorCode.EDIT_CONFLICT, HttpStatus.CONFLICT,
                    "Đề vừa được người khác sửa, tải lại.", Map.of());
        }
        validate(input.write());
        requireQuestions(input.questions());
        try {
            store.update(id, input.write());
        } catch (DuplicateKeyException exception) {
            throw codeConflict(exception);
        }
        store.replaceLinks(id, links(input.write()));
        return view(required(store.find(id, false)));
    }

    @Transactional
    public ExamDetail clone(long sourceId, long actorId, CloneWrite input) {
        ExamRow source = required(store.find(sourceId, true));
        if (input == null) throw invalid("body");
        validateCode(input.code());
        String title = input.title() == null ? source.title() : input.title();
        validateText(title, 200, "title");
        long id = insert(actorId, input.code(), title, source.hskLevel(), source.totalScore(), source.sections());
        store.replaceLinks(id, store.links(sourceId));
        audit.recordDetail(actorId, "EXAM_CLONE", "EXAM", id, Map.of("sourceExamId", sourceId));
        return view(required(store.find(id, false)));
    }

    private long insert(long actorId, String code, String title, int hsk, int score, JsonNode sections) {
        try {
            return store.insert(actorId, code, title, hsk, score, sections);
        } catch (DuplicateKeyException exception) {
            throw codeConflict(exception);
        }
    }

    private RuntimeException codeConflict(DuplicateKeyException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                && sql.getMessage() != null && sql.getMessage().contains("\"exams_code_key\"")) {
            return new ApiException(ErrorCode.EXAM_CODE_TAKEN, HttpStatus.CONFLICT, "Mã đề đã được sử dụng.", Map.of());
        }
        return exception;
    }

    private ExamDetail view(ExamRow exam) {
        List<Link> links = store.links(exam.id());
        List<QuestionData> questions = store.questions(links);
        List<SectionView> sections = new ArrayList<>();
        for (JsonNode section : exam.sections()) {
            sections.add(new SectionView(section.path("name").asText(),
                    section.hasNonNull("type") ? SectionType.valueOf(section.get("type").asText()) : null,
                    section.hasNonNull("duration_min") ? section.get("duration_min").intValue() : null));
        }
        List<QuestionView> views = questions.stream().map(item -> {
            var q = item.question();
            Link link = item.link();
            return new QuestionView(link.questionId(), link.sectionIndex(), link.orderIndex(), link.points(),
                    q.getCode(), q.getQtype().name(), q.getStatus().name(), item.kid(),
                    q.getAudioUrl() != null && !q.getAudioUrl().isBlank());
        }).toList();
        return new ExamDetail(exam.id(), exam.code(), exam.title(), exam.hskLevel(), exam.totalScore(), List.copyOf(sections),
                exam.status(), exam.publishedAt(), etag(exam, links), views, validator.validate(exam, questions));
    }

    String etag(ExamRow exam, List<Link> links) {
        var json = mapper.createObjectNode().put("id", exam.id()).put("code", exam.code()).put("title", exam.title())
                .put("hskLevel", exam.hskLevel()).put("totalScore", exam.totalScore()).put("status", exam.status().name());
        json.put("publishedAt", exam.publishedAt() == null ? null : exam.publishedAt().toString());
        json.set("sections", canonical(exam.sections()));
        var items = mapper.createArrayNode();
        for (Link link : links) {
            items.addObject().put("questionId", link.questionId()).put("sectionIndex", link.sectionIndex())
                    .put("orderIndex", link.orderIndex()).put("points", link.points());
        }
        json.set("questions", items);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(field -> result.set(field, canonical(node.get(field))));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }

    private void requireQuestions(List<QuestionWrite> questions) {
        if (store.existingQuestionIds(questions).size() != questions.size()) {
            throw new ApiException(ErrorCode.QUESTION_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi.", Map.of());
        }
    }

    private List<Link> links(ExamWrite input) {
        return IntStream.range(0, input.questions().size()).mapToObj(index -> {
            QuestionWrite question = input.questions().get(index);
            return new Link(question.questionId(), question.sectionIndex(), index, question.points());
        }).toList();
    }

    private void validate(ExamWrite input) {
        if (input == null) throw invalid("body");
        validateCode(input.code());
        validateText(input.title(), 200, "title");
        if (input.hskLevel() < 1 || input.hskLevel() > 6) throw invalid("hskLevel");
        if (input.totalScore() < 1 || input.totalScore() > 1000) throw invalid("totalScore");
        if (input.sections() == null || input.sections().isEmpty() || input.sections().size() > 5) throw invalid("sections");
        for (Section section : input.sections()) {
            if (section == null || section.type() == null || section.durationMin() < 0 || section.durationMin() > 180) {
                throw invalid("sections");
            }
            validateText(section.name(), 100, "sections.name");
        }
        if (input.questions() == null || input.questions().size() > 300) throw invalid("questions");
        var ids = new HashSet<Long>();
        for (QuestionWrite question : input.questions()) {
            if (question == null || question.questionId() < 1 || !ids.add(question.questionId())
                    || question.sectionIndex() < 0 || question.sectionIndex() >= input.sections().size()
                    || question.points() < 1 || question.points() > 100) throw invalid("questions");
        }
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("^[A-Z0-9-]{1,50}$")) throw invalid("code");
    }

    private void validateText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) throw invalid(field);
    }

    private ExamRow required(ExamRow row) {
        if (row == null) throw new ApiException(ErrorCode.EXAM_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy đề thi.", Map.of());
        return row;
    }

    public static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }
}
