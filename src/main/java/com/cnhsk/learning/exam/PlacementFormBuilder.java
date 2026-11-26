package com.cnhsk.learning.exam;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the five placement forms from approved placement drafts (questions coded {@code PLC1-}, {@code PLC2-},
 * {@code PLC3-}). Each form is one mixed sheet of ten questions per level, balanced across knowledge domains, and
 * no question appears in two forms. Forms already published are left alone; draft forms are rebuilt.
 */
@Service
public class PlacementFormBuilder {
    static final int FORMS = 5;
    static final int PER_LEVEL = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final PlacementConfig config;
    private final AuditService audit;
    private final RandomGenerator random;

    public PlacementFormBuilder(NamedParameterJdbcTemplate jdbc, PlacementConfig config, AuditService audit,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.jdbc = jdbc;
        this.config = config;
        this.audit = audit;
        this.random = random;
    }

    /** Approved and still-reviewed placement drafts per level, and the placement forms that exist. */
    @Transactional(readOnly = true)
    public Pool pool() {
        List<LevelCount> levels = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                           count(*) FILTER (WHERE status = 'PENDING_REVIEW') AS pending
                    FROM learning.questions WHERE code LIKE :prefix
                    """, new MapSqlParameterSource("prefix", "PLC" + level + "-%"));
            levels.add(new LevelCount(level, ((Number) row.get("approved")).intValue(), ((Number) row.get("pending")).intValue(),
                    FORMS * PER_LEVEL));
        }
        List<FormState> forms = jdbc.query("""
                SELECT e.id, e.code, e.status, (SELECT count(*) FROM learning.exam_questions q WHERE q.exam_id = e.id) AS questions
                FROM learning.exams e WHERE e.code LIKE :prefix ORDER BY e.code
                """, new MapSqlParameterSource("prefix", config.getFormCodePrefix() + "%"),
                (rs, row) -> new FormState(rs.getLong("id"), rs.getString("code"), rs.getString("status"), rs.getInt("questions")));
        return new Pool(List.copyOf(levels), forms);
    }

    @Transactional
    public BuildResult build(long actorId) {
        Map<Integer, List<Draft>> drafts = new LinkedHashMap<>();
        List<Map<String, Integer>> shortages = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            List<Draft> approved = jdbc.query("""
                    SELECT q.id, kp.domain, q.hsk_level, q.difficulty FROM learning.questions q
                    JOIN learning.question_knowledge_points l ON l.question_id = q.id AND l.role = 'PRIMARY'
                    JOIN learning.knowledge_points kp ON kp.id = l.knowledge_point_id
                    WHERE q.code LIKE :prefix AND q.status = 'APPROVED' AND q.hsk_level = :level
                      AND q.qtype IN ('MULTIPLE_CHOICE', 'TRUE_FALSE')
                    ORDER BY q.id
                    """, new MapSqlParameterSource("prefix", "PLC" + level + "-%").addValue("level", level),
                    (rs, row) -> new Draft(rs.getLong("id"), rs.getString("domain"),
                            PlacementFormScoring.priorDifficulty(rs.getInt("hsk_level"), rs.getInt("difficulty"))));
            if (approved.size() < FORMS * PER_LEVEL) {
                shortages.add(Map.of("level", level, "available", approved.size(), "needed", FORMS * PER_LEVEL));
            }
            drafts.put(level, approved);
        }
        if (!shortages.isEmpty()) {
            throw new ApiException(ErrorCode.PLACEMENT_BANK_INSUFFICIENT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chưa đủ câu đã duyệt để tạo đề xếp trình độ.", Map.of("details", shortages));
        }
        List<List<Long>> forms = assemble(drafts, random);
        Map<String, FormState> existing = new LinkedHashMap<>();
        pool().forms().forEach(form -> existing.put(form.code(), form));
        List<String> built = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (int index = 0; index < FORMS; index++) {
            String code = "%s%02d".formatted(config.getFormCodePrefix(), index + 1);
            FormState current = existing.get(code);
            if (current != null && !"DRAFT".equals(current.status())) {
                kept.add(code);
                continue;
            }
            long examId = current != null ? current.id() : insert(code, index + 1, actorId);
            jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id = :id", new MapSqlParameterSource("id", examId));
            List<Long> questions = forms.get(index);
            for (int order = 0; order < questions.size(); order++) {
                jdbc.update("""
                        INSERT INTO learning.exam_questions(exam_id, question_id, section_index, order_index, points)
                        VALUES (:exam, :question, :section, :order, 1)
                        """, new MapSqlParameterSource("exam", examId).addValue("question", questions.get(order))
                        .addValue("section", 0).addValue("order", order));
            }
            built.add(code);
        }
        audit.recordDetail(actorId, "PLACEMENT_FORMS_BUILT", "EXAM", Map.of("built", built, "kept", kept));
        return new BuildResult(List.copyOf(built), List.copyOf(kept));
    }

    private long insert(String code, int number, long actorId) {
        // One mixed part: learners never see which level a question belongs to.
        String sections = """
                [{"index":0,"name":"Kiểm tra trình độ","type":"READING","duration_min":15}]
                """;
        return jdbc.queryForObject("""
                INSERT INTO learning.exams(code, title, hsk_level, total_score, sections, status, created_by)
                VALUES (:code, :title, 3, :score, CAST(:sections AS jsonb), 'DRAFT', :actor) RETURNING id
                """, new MapSqlParameterSource("code", code).addValue("title", "Kiểm tra trình độ – đề " + number)
                .addValue("score", 3 * PER_LEVEL).addValue("sections", sections).addValue("actor", actorId), Long.class);
    }

    /**
     * Deals each level's drafts to the forms in turn after interleaving them across domains, so every form gets
     * {@link #PER_LEVEL} questions per level with a similar domain mix and no question is used twice. Each form
     * is then one mixed sheet ordered from easier to harder by starting difficulty (ties shuffled), so the levels
     * interleave and the learner warms up before the hardest questions.
     */
    static List<List<Long>> assemble(Map<Integer, List<Draft>> drafts, RandomGenerator random) {
        List<List<Draft>> forms = new ArrayList<>();
        for (int index = 0; index < FORMS; index++) forms.add(new ArrayList<>());
        for (int level = 1; level <= 3; level++) {
            Map<String, List<Draft>> byDomain = new LinkedHashMap<>();
            for (Draft draft : drafts.getOrDefault(level, List.of())) {
                byDomain.computeIfAbsent(draft.domain(), ignored -> new ArrayList<>()).add(draft);
            }
            List<Deque<Draft>> queues = new ArrayList<>();
            for (List<Draft> group : byDomain.values()) {
                List<Draft> shuffled = new ArrayList<>(group);
                shuffle(shuffled, random);
                queues.add(new ArrayDeque<>(shuffled));
            }
            List<Draft> interleaved = new ArrayList<>();
            while (queues.stream().anyMatch(queue -> !queue.isEmpty())) {
                for (Deque<Draft> queue : queues) if (!queue.isEmpty()) interleaved.add(queue.removeFirst());
            }
            for (int index = 0; index < FORMS * PER_LEVEL; index++) {
                forms.get(index % FORMS).add(interleaved.get(index));
            }
        }
        return forms.stream().map(form -> {
            List<Draft> sheet = new ArrayList<>(form);
            shuffle(sheet, random);
            sheet.sort(java.util.Comparator.comparingDouble(Draft::difficulty));
            return sheet.stream().map(Draft::id).toList();
        }).toList();
    }

    private static <T> void shuffle(List<T> items, RandomGenerator random) {
        for (int index = items.size() - 1; index > 0; index--) Collections.swap(items, index, random.nextInt(index + 1));
    }

    record Draft(long id, String domain, double difficulty) {}
    public record LevelCount(int level, int approved, int pending, int needed) {}
    public record FormState(long id, String code, String status, int questions) {}
    public record Pool(List<LevelCount> levels, List<FormState> forms) {}
    public record BuildResult(List<String> built, List<String> kept) {}
}
