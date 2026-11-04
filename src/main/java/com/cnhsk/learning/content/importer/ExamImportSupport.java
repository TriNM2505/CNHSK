package com.cnhsk.learning.content.importer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.exam.QuestionItemFactory;
import com.cnhsk.learning.knowledge.entity.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExamImportSupport {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public ExamImportSupport(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public static List<Section> sections(String raw) {
        List<Section> result = new ArrayList<>();
        int previous = 0;
        for (String part : raw.split("\\|", -1)) {
            String[] fields = part.split(":", 2);
            if (fields.length != 2 || !fields[0].strip().matches("[1-9]|10")) {
                throw new IllegalArgumentException("Chỉ số phần phải từ 1 đến 10.");
            }
            int index = Integer.parseInt(fields[0].strip());
            String name = fields[1].strip();
            if (index <= previous || name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
                throw new IllegalArgumentException("Chỉ số phần phải tăng dần và tên phần có từ 1 đến 100 ký tự.");
            }
            result.add(new Section(index, name));
            previous = index;
        }
        return List.copyOf(result);
    }

    public static Optional<Conflict> conflict(List<LinkedQuestion> questions, List<Section> sections, int level) {
        var missing = questions.stream().map(LinkedQuestion::sectionIndex).distinct().sorted()
                .filter(index -> sections.stream().noneMatch(section -> section.index() == index)).findFirst();
        if (missing.isPresent()) {
            return Optional.of(new Conflict("INVALID_VALUE", "sections", "Phần " + missing.get()
                    + " đang có câu hỏi — không bỏ hoặc đổi chỉ số được; tạo đề mới nếu cần đổi cấu trúc"));
        }
        if (questions.stream().anyMatch(question -> question.level() != level)) {
            return Optional.of(new Conflict("LEVEL_MISMATCH", "hsk_level", "Cấp HSK phải khớp với các câu đã gắn vào đề."));
        }
        return Optional.empty();
    }

    public Map<String, List<LinkedQuestion>> linkedQuestions(List<String> codes) {
        if (codes.isEmpty()) return Map.of();
        return jdbc.query("""
                SELECT e.code, eq.section_index, q.hsk_level
                FROM learning.exams e JOIN learning.exam_questions eq ON eq.exam_id = e.id
                JOIN learning.questions q ON q.id = eq.question_id WHERE e.code IN (:codes)
                """, Map.of("codes", codes), rs -> {
            Map<String, List<LinkedQuestion>> result = new HashMap<>();
            while (rs.next()) result.computeIfAbsent(rs.getString(1), ignored -> new ArrayList<>())
                    .add(new LinkedQuestion(rs.getInt(2), rs.getInt(3)));
            return result;
        });
    }

    public List<Exam> exams(Collection<String> codes, boolean lock) {
        if (codes.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT code, title, hsk_level, sections, status, published_at FROM learning.exams WHERE code IN (:codes)
                """ + (lock ? " FOR UPDATE" : ""), Map.of("codes", codes),
                (rs, row) -> new Exam(rs.getString("code"), rs.getString("title"), rs.getInt("hsk_level"),
                        readSections(rs.getString("sections")), rs.getString("status"), rs.getObject("published_at") != null));
    }

    public void lock(String code) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "exam:import:" + code), rs -> null);
    }

    public Map<String, Map<String, Slot>> slots(Collection<String> codes) {
        if (codes.isEmpty()) return Map.of();
        return jdbc.query("""
                SELECT e.code, q.code, eq.section_index, eq.order_index, eq.points
                FROM learning.exams e JOIN learning.exam_questions eq ON eq.exam_id = e.id
                JOIN learning.questions q ON q.id = eq.question_id WHERE e.code IN (:codes)
                """, Map.of("codes", codes), rs -> {
            Map<String, Map<String, Slot>> result = new HashMap<>();
            while (rs.next()) result.computeIfAbsent(rs.getString(1), ignored -> new HashMap<>())
                    .put(rs.getString(2), new Slot(rs.getInt(3), rs.getInt(4), rs.getInt(5)));
            return result;
        });
    }

    public Optional<Conflict> questionConflict(Exam exam, Question question, Slot slot) {
        if (exam == null) return Optional.of(new Conflict("REFERENCE_NOT_FOUND", "exam_code", "Không tìm thấy đề thi."));
        if (question == null) return Optional.of(new Conflict("REFERENCE_NOT_FOUND", "question_code", "Không tìm thấy câu hỏi."));
        if (exam.sections().stream().noneMatch(section -> section.index() == slot.section())) {
            return Optional.of(new Conflict("SECTION_NOT_FOUND", "section_index", "Không tìm thấy phần trong đề thi."));
        }
        if (!factory.isUsable(question)) {
            return Optional.of(new Conflict("QUESTION_NOT_USABLE", "question_code", "Câu hỏi không dùng được trong đề thi."));
        }
        if (question.getHskLevel() != exam.level()) {
            return Optional.of(new Conflict("LEVEL_MISMATCH", "question_code", "Cấp HSK của câu hỏi phải khớp với đề."));
        }
        return Optional.empty();
    }

    public static boolean occupied(Map<String, Slot> slots, String question, Slot requested) {
        return slots.entrySet().stream().anyMatch(entry -> !question.equals(entry.getKey())
                && entry.getValue().position().equals(requested.position()));
    }

    public static Conflict duplicateOrder() {
        return new Conflict("DUPLICATE_ORDER", "order_index", "Vị trí này đã thuộc câu hỏi khác trong phần.");
    }

    public static Conflict tooManyQuestions() {
        return new Conflict("INVALID_VALUE", "exam_code", "Mỗi đề có tối đa 200 câu hỏi.");
    }

    public record Position(int section, int order) {}

    public record Slot(int section, int order, int points) {
        public Position position() {
            return new Position(section, order);
        }

        public static Slot from(Map<String, String> values) {
            String points = values.getOrDefault("points", "");
            return new Slot(Integer.parseInt(values.get("section_index")), Integer.parseInt(values.get("order_index")),
                    points.isBlank() ? 1 : Integer.parseInt(points));
        }
    }

    private List<Section> readSections(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored exam sections", exception);
        }
    }

    public String writeSections(List<Section> sections) {
        try {
            return mapper.writeValueAsString(sections);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize exam sections", exception);
        }
    }

    public record Section(int index, String name) {}
    public record LinkedQuestion(int sectionIndex, int level) {}
    public record Conflict(String code, String field, String message) {}
    public record Exam(String code, String title, int level, List<Section> sections, String status, boolean published) {
        public boolean matches(Map<String, String> row) {
            return title.equals(row.get("title")) && level == Integer.parseInt(row.get("hsk_level"))
                    && sections.equals(ExamImportSupport.sections(row.get("sections"))) && status.equals(row.get("status"))
                    && (!"PUBLISHED".equals(status) || published);
        }
    }
}
