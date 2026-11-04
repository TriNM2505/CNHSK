package com.cnhsk.learning.exam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamQueryService {
    private static final Logger log = LoggerFactory.getLogger(ExamQueryService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entities;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public ExamQueryService(NamedParameterJdbcTemplate jdbc, EntityManager entities, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.entities = entities;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExamSummary> list(long userId, Integer hskLevel) {
        String sql = "SELECT id, code, title, hsk_level, status, sections FROM learning.exams WHERE status = 'PUBLISHED'";
        List<Exam> exams = jdbc.query(sql + (hskLevel == null ? "" : " AND hsk_level = :level")
                        + " ORDER BY hsk_level, code", hskLevel == null ? Map.of() : Map.of("level", hskLevel),
                (rs, row) -> exam(rs));
        if (exams.isEmpty()) return List.of();

        Map<Long, List<ExamQuestion>> questions = questions(exams.stream().map(Exam::id).toList());
        Map<Long, Map<Integer, Integer>> counts = new HashMap<>();
        for (Exam exam : exams) {
            Map<Integer, Integer> bySection = new HashMap<>();
            for (ExamQuestion item : questions.getOrDefault(exam.id(), List.of())) {
                if (factory.isUsable(item.question())) bySection.merge(item.section(), 1, Integer::sum);
            }
            if (bySection.isEmpty()) {
                log.warn("Published exam has no usable questions: examId={}", exam.id());
            } else {
                counts.put(exam.id(), bySection);
            }
        }
        if (counts.isEmpty()) return List.of();

        Map<Long, List<History>> history = history(userId, counts.keySet());
        List<ExamSummary> summaries = new ArrayList<>();
        for (Exam exam : exams) {
            Map<Integer, Integer> bySection = counts.get(exam.id());
            if (bySection == null) continue;
            List<History> attempts = history.getOrDefault(exam.id(), List.of());
            List<History> submitted = attempts.stream().filter(History::submitted).toList();
            BestScore best = submitted.stream().filter(attempt -> attempt.maxScore > 0)
                    .max(Comparator.comparingInt((History attempt) -> attempt.bestScore().percent())
                            .thenComparingLong(attempt -> attempt.score)
                            .thenComparing(attempt -> attempt.submittedAt)
                            .thenComparingLong(attempt -> attempt.id))
                    .map(History::bestScore).orElse(null);
            Long active = attempts.stream().filter(attempt -> !attempt.submitted())
                    .map(attempt -> attempt.id).max(Long::compareTo).orElse(null);
            List<SectionSummary> sections = exam.sections().stream()
                    .map(section -> new SectionSummary(section.index(), section.name(),
                            bySection.getOrDefault(section.index(), 0))).toList();
            summaries.add(new ExamSummary(exam.id(), exam.code(), exam.title(), exam.hskLevel(),
                    bySection.values().stream().mapToInt(Integer::intValue).sum(),
                    sections, submitted.size(), best, active));
        }
        return List.copyOf(summaries);
    }

    Exam find(long id) {
        return jdbc.query("""
                SELECT id, code, title, hsk_level, status, sections
                FROM learning.exams WHERE id = :id
                """, Map.of("id", id), (rs, row) -> exam(rs)).stream().findFirst().orElse(null);
    }

    private Exam exam(ResultSet rs) throws SQLException {
        List<Section> sections = new ArrayList<>();
        for (JsonNode node : json(rs.getString("sections"))) {
            sections.add(new Section(node.path("index").asInt(), node.path("name").asText()));
        }
        return new Exam(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getInt("hsk_level"),
                rs.getString("status"), List.copyOf(sections));
    }

    Map<Long, List<ExamQuestion>> questions(List<Long> examIds) {
        List<QuestionLink> links = jdbc.query("""
                SELECT exam_id, question_id, section_index, points
                FROM learning.exam_questions
                WHERE exam_id IN (:ids)
                ORDER BY exam_id, section_index, order_index, question_id
                """, Map.of("ids", examIds), (rs, row) -> new QuestionLink(rs.getLong("exam_id"),
                rs.getLong("question_id"), rs.getInt("section_index"), rs.getInt("points")));
        if (links.isEmpty()) return Map.of();

        List<Long> questionIds = links.stream().map(QuestionLink::questionId).distinct().toList();
        Map<Long, Question> questions = new HashMap<>();
        entities.createQuery("SELECT q FROM Question q WHERE q.id IN :ids", Question.class)
                .setParameter("ids", questionIds).getResultList()
                .forEach(question -> questions.put(question.getId(), question));
        Map<Long, List<ExamQuestion>> result = new HashMap<>();
        for (QuestionLink link : links) {
            result.computeIfAbsent(link.examId(), ignored -> new ArrayList<>())
                    .add(new ExamQuestion(questions.get(link.questionId()), link.section(), link.points()));
        }
        return result;
    }

    private Map<Long, List<History>> history(long userId, Set<Long> examIds) {
        return jdbc.query("""
                SELECT a.id, a.ref_id, a.submitted_at, a.served_items AS snapshot,
                       array_agg(i.order_index) FILTER (WHERE i.attempt_id IS NOT NULL) AS correct_numbers
                FROM learning.attempts a
                LEFT JOIN learning.attempt_items i
                    ON i.attempt_id = a.id AND a.status = 'SUBMITTED' AND i.is_correct = true
                WHERE a.user_id = :userId AND a.ref_type = 'MOCK_EXAM' AND a.ref_id IN (:ids)
                    AND (a.status = 'SUBMITTED' OR (a.status = 'IN_PROGRESS' AND a.expires_at >= :now))
                GROUP BY a.id
                """, Map.of("userId", userId, "ids", examIds, "now", clock.instant().atOffset(ZoneOffset.UTC)), rs -> {
            Map<Long, List<History>> byExam = new HashMap<>();
            while (rs.next()) {
                OffsetDateTime submittedAt = rs.getObject("submitted_at", OffsetDateTime.class);
                JsonNode points = json(rs.getString("snapshot")).path("meta").path("exam").path("points");
                long maxScore = 0;
                for (JsonNode value : points) maxScore += value.asInt();

                long score = 0;
                var correctNumbers = rs.getArray("correct_numbers");
                if (correctNumbers != null) {
                    for (Integer no : (Integer[]) correctNumbers.getArray()) score += points.path(no.toString()).asInt();
                    correctNumbers.free();
                }
                History attempt = new History(rs.getLong("id"), submittedAt, score, maxScore);
                byExam.computeIfAbsent(rs.getLong("ref_id"), ignored -> new ArrayList<>()).add(attempt);
            }
            return byExam;
        });
    }

    private JsonNode json(String value) {
        if (value == null) return mapper.nullNode();
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored exam data", exception);
        }
    }

    record Exam(long id, String code, String title, int hskLevel, String status, List<Section> sections) {}
    record Section(int index, String name) {}
    record ExamQuestion(Question question, int section, int points) {}
    private record QuestionLink(long examId, long questionId, int section, int points) {}

    private record History(long id, OffsetDateTime submittedAt, long score, long maxScore) {
        private boolean submitted() {
            return submittedAt != null;
        }

        private BestScore bestScore() {
            return new BestScore(score, maxScore, (int) (score * 100 / maxScore));
        }
    }

    public record ExamSummary(long id, String code, String title, int hskLevel, int questionCount,
            List<SectionSummary> sections, long myAttempts, BestScore myBest, Long activeSessionId) {}
    public record SectionSummary(int index, String name, int questionCount) {}
    public record BestScore(long score, long maxScore, int percent) {}
}
