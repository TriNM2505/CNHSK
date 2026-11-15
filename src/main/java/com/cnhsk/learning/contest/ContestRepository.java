package com.cnhsk.learning.contest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.exam.QuestionItemFactory;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContestRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entities;
    private final ObjectMapper mapper;
    private final QuestionItemFactory factory = new QuestionItemFactory();

    public ContestRepository(NamedParameterJdbcTemplate jdbc, EntityManager entities, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.entities = entities;
        this.mapper = mapper;
    }

    void lockStart(long contestId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "contest:start:" + contestId), result -> null);
    }

    Optional<ContestAttempt> attempt(long userId, long contestId) {
        return jdbc.query("""
                SELECT id, status, expires_at, submitted_at, served_items
                FROM learning.attempts WHERE user_id = :userId AND kind = 'CONTEST' AND ref_id = :contestId
                """, Map.of("userId", userId, "contestId", contestId), (rs, row) -> {
            OffsetDateTime expiry = rs.getObject("expires_at", OffsetDateTime.class);
            OffsetDateTime submitted = rs.getObject("submitted_at", OffsetDateTime.class);
            return new ContestAttempt(rs.getLong("id"), rs.getString("status"),
                    expiry == null ? null : expiry.toInstant(), submitted == null ? null : submitted.toInstant(),
                    json(rs.getString("served_items")));
        }).stream().findFirst();
    }

    long countAttempts(long contestId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM learning.attempts WHERE kind = 'CONTEST' AND ref_id = :contestId
                """, Map.of("contestId", contestId), Long.class);
    }

    Optional<ContestExam> exam(long examId) {
        return jdbc.query("SELECT id, title, status, sections FROM learning.exams WHERE id = :id",
                Map.of("id", examId), (rs, row) -> {
                    int duration = 0;
                    for (JsonNode section : json(rs.getString("sections"))) {
                        duration += section.path("duration_min").asInt();
                    }
                    return new ContestExam(rs.getLong("id"), rs.getString("title"), rs.getString("status"), duration);
                }).stream().findFirst();
    }

    List<ServedItem> items(long examId) {
        List<Long> ids = jdbc.query("""
                SELECT question_id FROM learning.exam_questions WHERE exam_id = :id
                ORDER BY section_index, order_index, question_id
                """, Map.of("id", examId), (rs, row) -> rs.getLong("question_id"));
        if (ids.isEmpty()) return List.of();
        Map<Long, Question> questions = new HashMap<>();
        entities.createQuery("SELECT q FROM Question q WHERE q.id IN :ids", Question.class)
                .setParameter("ids", ids).getResultList().forEach(question -> questions.put(question.getId(), question));
        List<ServedItem> items = new ArrayList<>();
        for (long id : ids) {
            factory.create(questions.get(id), items.size() + 1).ifPresent(items::add);
        }
        return List.copyOf(items);
    }

    void setExpiry(long sessionId, Instant expiresAt) {
        entities.getReference(Attempt.class, sessionId).setExpiresAt(expiresAt);
    }

    private JsonNode json(String value) {
        if (value == null) return mapper.nullNode();
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored contest data", exception);
        }
    }

    record ContestAttempt(long id, String status, Instant expiresAt, Instant submittedAt, JsonNode snapshot) {
        Instant deadline() {
            return Instant.parse(snapshot.path("meta").path("contest").path("deadline").asText());
        }
    }

    record ContestExam(long id, String title, String status, int durationMinutes) { }
}
