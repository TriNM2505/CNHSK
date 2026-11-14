package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.entity.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class ExamAdminStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EntityManager entities;

    public ExamAdminStore(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, EntityManager entities) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.entities = entities;
    }

    Listing list(ExamStatus status, Integer hsk, String query, int page, int size) {
        StringBuilder filter = new StringBuilder(" WHERE true");
        var args = new MapSqlParameterSource();
        if (status != null) { filter.append(" AND e.status=:status"); args.addValue("status", status.name()); }
        if (hsk != null) { filter.append(" AND e.hsk_level=:hsk"); args.addValue("hsk", hsk); }
        if (query != null && !query.isEmpty()) {
            filter.append(" AND (e.code ILIKE :query ESCAPE '!' OR e.title ILIKE :query ESCAPE '!')");
            args.addValue("query", "%" + query.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
        }
        long count = jdbc.queryForObject("SELECT count(*) FROM learning.exams e" + filter, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<SummaryRow> items = jdbc.query("""
                SELECT e.*, count(eq.question_id) AS question_count
                FROM learning.exams e LEFT JOIN learning.exam_questions eq ON eq.exam_id=e.id
                """ + filter + " GROUP BY e.id ORDER BY e.created_at DESC, e.id DESC LIMIT :limit OFFSET :offset",
                args, (rs, row) -> new SummaryRow(exam(rs), rs.getInt("question_count")));
        return new Listing(items, count);
    }

    ExamRow find(long id, boolean lock) {
        return jdbc.query("SELECT * FROM learning.exams WHERE id=:id" + (lock ? " FOR UPDATE" : ""),
                Map.of("id", id), (rs, row) -> exam(rs)).stream().findFirst().orElse(null);
    }

    long insert(long actorId, String code, String title, int hskLevel, int totalScore, JsonNode sections) {
        return jdbc.queryForObject("""
                INSERT INTO learning.exams(code,title,hsk_level,total_score,sections,status,created_by)
                VALUES (:code,:title,:hsk,:score,CAST(:sections AS jsonb),'DRAFT',:actor) RETURNING id
                """, values(code, title, hskLevel, totalScore, sections).addValue("actor", actorId), Long.class);
    }

    void update(long id, ExamWrite input) {
        jdbc.update("""
                UPDATE learning.exams SET code=:code,title=:title,hsk_level=:hsk,total_score=:score,
                    sections=CAST(:sections AS jsonb) WHERE id=:id
                """, values(input.code(), input.title(), input.hskLevel(), input.totalScore(), sections(input.sections()))
                .addValue("id", id));
    }

    List<Link> links(long id) {
        return jdbc.query("""
                SELECT question_id,section_index,order_index,points FROM learning.exam_questions
                WHERE exam_id=:id ORDER BY order_index,section_index,question_id
                """, Map.of("id", id), (rs, row) -> new Link(rs.getLong("question_id"),
                rs.getInt("section_index"), rs.getInt("order_index"), rs.getInt("points")));
    }

    void replaceLinks(long id, List<Link> links) {
        jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id=:id", Map.of("id", id));
        SqlParameterSource[] rows = links.stream().map(link -> new MapSqlParameterSource()
                .addValue("exam", id).addValue("question", link.questionId()).addValue("section", link.sectionIndex())
                .addValue("order", link.orderIndex()).addValue("points", link.points())).toArray(SqlParameterSource[]::new);
        if (rows.length > 0) {
            jdbc.batchUpdate("""
                    INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index,points)
                    VALUES (:exam,:question,:section,:order,:points)
                    """, rows);
        }
    }

    Set<Long> existingQuestionIds(List<QuestionWrite> questions) {
        if (questions.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.queryForList("SELECT id FROM learning.questions WHERE id IN (:ids)",
                Map.of("ids", questions.stream().map(QuestionWrite::questionId).toList()), Long.class));
    }

    List<QuestionData> questions(List<Link> links) {
        if (links.isEmpty()) return List.of();
        List<Long> ids = links.stream().map(Link::questionId).distinct().toList();
        Map<Long, Question> questions = entities.createQuery("SELECT q FROM Question q WHERE q.id IN :ids", Question.class)
                .setParameter("ids", ids).getResultList().stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, String> kids = new HashMap<>();
        jdbc.query("""
                SELECT qkp.question_id,kp.code FROM learning.question_knowledge_points qkp
                JOIN learning.knowledge_points kp ON kp.id=qkp.knowledge_point_id
                WHERE qkp.question_id IN (:ids) AND qkp.role='PRIMARY'
                """, Map.of("ids", ids), rs -> {
                    while (rs.next()) kids.put(rs.getLong(1), rs.getString(2));
                    return null;
                });
        return links.stream().map(link -> new QuestionData(link, questions.get(link.questionId()),
                kids.get(link.questionId()))).toList();
    }

    JsonNode sections(List<Section> sections) {
        var json = mapper.createArrayNode();
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            json.addObject().put("index", index).put("name", section.name()).put("type", section.type().name())
                    .put("duration_min", section.durationMin());
        }
        return json;
    }

    private MapSqlParameterSource values(String code, String title, int hsk, int score, JsonNode sections) {
        return new MapSqlParameterSource().addValue("code", code).addValue("title", title).addValue("hsk", hsk)
                .addValue("score", score).addValue("sections", sections.toString());
    }

    private ExamRow exam(ResultSet rs) throws SQLException {
        OffsetDateTime published = rs.getObject("published_at", OffsetDateTime.class);
        try {
            return new ExamRow(rs.getLong("id"), rs.getString("code"), rs.getString("title"), rs.getInt("hsk_level"),
                    rs.getInt("total_score"), mapper.readTree(rs.getString("sections")),
                    ExamStatus.valueOf(rs.getString("status")), published == null ? null : published.toInstant());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored exam sections", exception);
        }
    }
}
