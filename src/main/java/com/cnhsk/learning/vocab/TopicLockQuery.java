package com.cnhsk.learning.vocab;

import java.util.List;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TopicLockQuery {
    static final String COMPLETED_SQL = """
            bool_or(ref_type = 'TOPIC_TEST' AND status = 'SUBMITTED' AND max_score > 0
                    AND score * 100 >= 90 * max_score)
            """;
    static final String LOCK_JOIN = """
            LEFT JOIN LATERAL (
                SELECT coalesce(jsonb_agg(jsonb_build_object('id', prerequisite.id, 'name', prerequisite.name)
                    ORDER BY prerequisite.id), '[]'::jsonb) AS blocked_by
                FROM learning.topics prerequisite
                LEFT JOIN activity prerequisite_activity ON prerequisite_activity.ref_id = prerequisite.id
                WHERE prerequisite.id = ANY(t.prereq_ids) AND NOT coalesce(prerequisite_activity.completed, false)
            ) topic_lock ON true
            """;
    private static final String BLOCKED = """
            WITH activity AS (
                SELECT ref_id,
            """ + COMPLETED_SQL + """
                AS completed FROM learning.attempts WHERE user_id = :userId
                AND ref_type IN ('TOPIC_STUDY', 'TOPIC_PRACTICE', 'TOPIC_TEST') GROUP BY ref_id
            )
            SELECT CAST(topic_lock.blocked_by AS text) FROM learning.topics t
            """ + LOCK_JOIN + " WHERE t.id = :topicId";

    private final EntityManager entities;
    private final ObjectMapper mapper;

    public TopicLockQuery(EntityManager entities, ObjectMapper mapper) { this.entities = entities; this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOpen(long userId, long topicId) {
        // Lock first; the next statement gets a fresh snapshot after any prerequisite writer commits.
        var ids = entities.createNativeQuery("SELECT id FROM learning.topics WHERE id = :topicId FOR SHARE")
                .setParameter("topicId", topicId).getResultList();
        if (ids.isEmpty()) throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy chủ đề hoặc chủ đề chưa có từ.", Map.of());
        var blocked = parse(entities.createNativeQuery(BLOCKED).setParameter("userId", userId)
                .setParameter("topicId", topicId).getSingleResult().toString());
        if (!blocked.isEmpty()) throw new ApiException(ErrorCode.TOPIC_LOCKED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Chủ đề đang khoá; cần hoàn thành các chủ đề tiên quyết.", Map.of("details", Map.of("lockedBy", blocked)));
    }

    List<LockedTopicRef> parse(String json) {
        try { return List.copyOf(mapper.readValue(json, new TypeReference<List<LockedTopicRef>>() { })); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid topic lock projection", exception); }
    }

    @Schema(name = "LockedTopicRef")
    public record LockedTopicRef(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) { }
}
