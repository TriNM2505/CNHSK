package com.cnhsk.learning.aigen.embedding;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sentence vectors of stored questions, kept per model so a model change re-embeds everything. */
@Repository
public class QuestionEmbeddingStore {
    private final JdbcTemplate jdbc;

    public QuestionEmbeddingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Vectors of every question labelled with the node, in any review state, with their options JSON. */
    public List<StoredVector> forKnowledgePoint(long knowledgePointId, String model) {
        return jdbc.query("""
                SELECT q.stem, q.options::text, e.vector
                FROM learning.question_embeddings e
                JOIN learning.questions q ON q.id = e.question_id
                WHERE e.model = ? AND EXISTS (
                    SELECT 1 FROM learning.question_knowledge_points label
                    WHERE label.question_id = q.id AND label.knowledge_point_id = ?)
                ORDER BY q.id
                """, (rows, index) -> new StoredVector(rows.getString(1), rows.getString(2), floats(rows.getArray(3))),
                model, knowledgePointId);
    }

    /** Labelled questions still lacking a vector of this model, the given nodes' questions first. */
    public List<MissingVector> missing(String model, Collection<Long> priorityPoints, int limit) {
        return jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT q.id, q.stem
                    FROM learning.questions q
                    WHERE EXISTS (SELECT 1 FROM learning.question_knowledge_points label WHERE label.question_id = q.id)
                      AND NOT EXISTS (SELECT 1 FROM learning.question_embeddings e
                                      WHERE e.question_id = q.id AND e.model = ?)
                    ORDER BY EXISTS (SELECT 1 FROM learning.question_knowledge_points label
                                     WHERE label.question_id = q.id AND label.knowledge_point_id = ANY (?)) DESC, q.id
                    LIMIT ?
                    """);
            statement.setString(1, model);
            statement.setArray(2, connection.createArrayOf("bigint", priorityPoints.toArray(Long[]::new)));
            statement.setInt(3, limit);
            return statement;
        }, (rows, index) -> new MissingVector(rows.getLong(1), rows.getString(2)));
    }

    /** Writes or replaces the vectors; joins the caller's transaction when there is one. */
    public void save(Map<Long, float[]> vectors, String model) {
        if (vectors.isEmpty()) return;
        List<Map.Entry<Long, float[]>> rows = new ArrayList<>(vectors.entrySet());
        jdbc.batchUpdate("""
                INSERT INTO learning.question_embeddings(question_id, model, vector)
                VALUES (?, ?, ?)
                ON CONFLICT (question_id) DO UPDATE SET model = EXCLUDED.model, vector = EXCLUDED.vector,
                    created_at = now()
                """, rows, rows.size(), (statement, row) -> {
                    statement.setLong(1, row.getKey());
                    statement.setString(2, model);
                    statement.setArray(3, statement.getConnection().createArrayOf("real", boxed(row.getValue())));
                });
    }

    private static Float[] boxed(float[] vector) {
        Float[] result = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) result[i] = vector[i];
        return result;
    }

    private static float[] floats(Array array) throws SQLException {
        Object[] values = (Object[]) array.getArray();
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) result[i] = ((Number) values[i]).floatValue();
        return result;
    }

    public record StoredVector(String stem, String optionsJson, float[] vector) { }
    public record MissingVector(long questionId, String stem) { }
}
