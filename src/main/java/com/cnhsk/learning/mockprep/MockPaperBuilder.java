package com.cnhsk.learning.mockprep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.exam.MockPaperFormat;
import com.cnhsk.learning.exam.MockPaperFormat.Layout;
import com.cnhsk.learning.exam.MockPaperFormat.Part;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the three HSK-format mock papers of a level from the approved, hand-imported questions of each part (codes
 * {@code MCK{level}-{part}-…}). Each paper follows the real layout part by part and no question appears in two
 * papers. Published papers are left alone; draft papers are rebuilt, and an editor publishes them as usual.
 */
@Service
public class MockPaperBuilder {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final RandomGenerator random;

    public MockPaperBuilder(NamedParameterJdbcTemplate jdbc, AuditService audit, ObjectMapper mapper,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.mapper = mapper;
        this.random = random;
    }

    /** Approved and still-unreviewed questions per part of a level, with how to write them, and that level's papers. */
    @Transactional(readOnly = true)
    public Pool pool(int level) {
        Layout layout = MockPaperFormat.of(level);
        List<PartCount> parts = new ArrayList<>();
        for (Part part : layout.parts()) {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                           count(*) FILTER (WHERE status = 'PENDING_REVIEW') AS pending
                    FROM learning.questions WHERE code LIKE :prefix
                    """, new MapSqlParameterSource("prefix", MockPaperFormat.questionPrefix(level, part.code()) + "%"));
            parts.add(new PartCount(part.code(), part.section(), part.skill(), part.type().name(), part.picture().name(),
                    ((Number) row.get("approved")).intValue(), ((Number) row.get("pending")).intValue(),
                    part.count() * MockPaperFormat.PAPERS, part.options(), part.guide()));
        }
        List<PaperState> papers = jdbc.query("""
                SELECT e.id, e.code, e.status, (SELECT count(*) FROM learning.exam_questions q WHERE q.exam_id = e.id) AS questions
                FROM learning.exams e WHERE e.code LIKE :prefix ORDER BY e.code
                """, new MapSqlParameterSource("prefix", MockPaperFormat.CODE_PREFIX + level + "-%"),
                (rs, row) -> new PaperState(rs.getLong("id"), rs.getString("code"), rs.getString("status"), rs.getInt("questions")));
        return new Pool(level, layout.questions(), List.copyOf(parts), papers);
    }

    @Transactional
    public BuildResult build(long actorId, int level) {
        Layout layout = MockPaperFormat.of(level);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                new MapSqlParameterSource("key", "mock:build:" + level), rs -> null);
        Map<String, List<Long>> approved = new LinkedHashMap<>();
        List<Map<String, Object>> shortages = new ArrayList<>();
        for (Part part : layout.parts()) {
            List<Long> ids = jdbc.queryForList("""
                    SELECT id FROM learning.questions
                    WHERE code LIKE :prefix AND status = 'APPROVED' AND hsk_level = :level AND qtype = :type
                    ORDER BY id
                    """, new MapSqlParameterSource("prefix", MockPaperFormat.questionPrefix(level, part.code()) + "%")
                    .addValue("level", level).addValue("type", part.type().name()), Long.class);
            int needed = part.count() * MockPaperFormat.PAPERS;
            if (ids.size() < needed) shortages.add(Map.of("part", part.code(), "available", ids.size(), "needed", needed));
            approved.put(part.code(), ids);
        }
        if (!shortages.isEmpty()) {
            throw new ApiException(ErrorCode.MOCK_BANK_INSUFFICIENT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Chưa đủ câu đã duyệt để tạo đề thi thử.", Map.of("details", shortages));
        }
        List<List<Slot>> papers = assemble(layout, approved, random);
        Map<String, PaperState> existing = new LinkedHashMap<>();
        pool(level).papers().forEach(paper -> existing.put(paper.code(), paper));
        List<String> built = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (int index = 0; index < MockPaperFormat.PAPERS; index++) {
            String code = MockPaperFormat.paperCode(level, index + 1);
            PaperState current = existing.get(code);
            if (current != null && !"DRAFT".equals(current.status())) {
                realignSections(layout, current.id());
                kept.add(code);
                continue;
            }
            if (current != null) realignSections(layout, current.id());
            long examId = current != null ? current.id() : insert(layout, code, index + 1, actorId);
            // One point per question, so the paper total matches its questions; results rescale each section to 100.
            jdbc.update("UPDATE learning.exams SET total_score = :score WHERE id = :id",
                    new MapSqlParameterSource("score", layout.questions()).addValue("id", examId));
            jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id = :id", new MapSqlParameterSource("id", examId));
            List<Slot> slots = papers.get(index);
            for (int order = 0; order < slots.size(); order++) {
                jdbc.update("""
                        INSERT INTO learning.exam_questions(exam_id, question_id, section_index, order_index, points)
                        VALUES (:exam, :question, :section, :order, 1)
                        """, new MapSqlParameterSource("exam", examId).addValue("question", slots.get(order).questionId())
                        .addValue("section", slots.get(order).section()).addValue("order", order));
            }
            built.add(code);
        }
        audit.recordDetail(actorId, "MOCK_PAPERS_BUILT", "EXAM", Map.of("level", level, "built", built, "kept", kept));
        return new BuildResult(List.copyOf(built), List.copyOf(kept));
    }

    /** Each part's approved questions are shuffled and dealt in blocks, so every paper gets its own questions. */
    static List<List<Slot>> assemble(Layout layout, Map<String, List<Long>> approved, RandomGenerator random) {
        List<List<Slot>> papers = new ArrayList<>();
        for (int index = 0; index < MockPaperFormat.PAPERS; index++) papers.add(new ArrayList<>());
        for (Part part : layout.parts()) {
            List<Long> pool = new ArrayList<>(approved.getOrDefault(part.code(), List.of()));
            for (int index = pool.size() - 1; index > 0; index--) {
                int other = random.nextInt(index + 1);
                Long swap = pool.get(index);
                pool.set(index, pool.get(other));
                pool.set(other, swap);
            }
            for (int paper = 0; paper < MockPaperFormat.PAPERS; paper++) {
                for (int slot = 0; slot < part.count(); slot++) {
                    papers.get(paper).add(new Slot(pool.get(paper * part.count() + slot), part.section()));
                }
            }
        }
        papers.forEach(paper -> paper.sort(Comparator.comparingInt(Slot::section)));
        return papers;
    }

    /**
     * Papers built before sections were numbered from 0 like every other exam: shifts their sections and links back so
     * the exam editor files each question under its own section. Questions, points and attempts stay as they are.
     */
    private void realignSections(Layout layout, long examId) {
        Integer lowest = jdbc.queryForObject("""
                SELECT min((section ->> 'index')::int) FROM learning.exams e, jsonb_array_elements(e.sections) section
                WHERE e.id = :id
                """, new MapSqlParameterSource("id", examId), Integer.class);
        if (lowest == null || lowest == 0) return;
        jdbc.update("UPDATE learning.exams SET sections = CAST(:sections AS jsonb) WHERE id = :id",
                new MapSqlParameterSource("sections", sectionsJson(layout)).addValue("id", examId));
        jdbc.update("UPDATE learning.exam_questions SET section_index = section_index - :shift WHERE exam_id = :id",
                new MapSqlParameterSource("shift", lowest).addValue("id", examId));
    }

    private String sectionsJson(Layout layout) {
        List<Map<String, Object>> sections = layout.sections().stream().map(section -> Map.<String, Object>of(
                "index", section.index(), "name", section.name(), "type", section.name().equals("Nghe") ? "LISTENING"
                        : section.name().equals("Viết") ? "WRITING" : "READING", "duration_min", section.minutes())).toList();
        try {
            return mapper.writeValueAsString(sections);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long insert(Layout layout, String code, int number, long actorId) {
        String json = sectionsJson(layout);
        return jdbc.queryForObject("""
                INSERT INTO learning.exams(code, title, hsk_level, total_score, sections, status, created_by)
                VALUES (:code, :title, :level, :score, CAST(:sections AS jsonb), 'DRAFT', :actor) RETURNING id
                """, new MapSqlParameterSource("code", code).addValue("title", "Đề thi thử HSK " + layout.level() + " – đề " + number)
                .addValue("level", layout.level()).addValue("score", layout.questions())
                .addValue("sections", json).addValue("actor", actorId), Long.class);
    }

    record Slot(long questionId, int section) {}

    @Schema(name = "MockPartCount", requiredProperties = {"part", "section", "skill", "qtype", "picture", "approved", "pending",
            "needed", "options", "guide"})
    public record PartCount(String part, int section, String skill, String qtype, String picture, int approved, int pending,
            int needed, int options, String guide) {}

    @Schema(name = "MockPaperState", requiredProperties = {"id", "code", "status", "questions"})
    public record PaperState(long id, String code, String status, int questions) {}

    @Schema(name = "MockPool", requiredProperties = {"level", "questionsPerPaper", "parts", "papers"})
    public record Pool(int level, int questionsPerPaper, List<PartCount> parts, List<PaperState> papers) {}

    @Schema(name = "MockBuildResult", requiredProperties = {"built", "kept"})
    public record BuildResult(List<String> built, List<String> kept) {}
}
