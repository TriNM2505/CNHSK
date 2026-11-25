package com.cnhsk.learning.api;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Live counts of the learning work staff still have to do, for the notification bell. */
@Component
public class StaffQueueApi {
    /** A claimed grading request is flagged this long before its deadline. */
    public static final Duration DUE_SOON = Duration.ofHours(2);

    private final NamedParameterJdbcTemplate jdbc;

    StaffQueueApi(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GRADING_OPEN: essays any teacher may claim (never the teacher's own); GRADING_DUE_SOON: essays this teacher
     * claimed that are due within two hours; AI_REVIEW: AI questions waiting for a teacher.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> teacherQueues(long teacherId, Instant now) {
        var parameters = new MapSqlParameterSource().addValue("teacher", teacherId)
                .addValue("now", now.atOffset(ZoneOffset.UTC))
                .addValue("soon", now.plus(DUE_SOON).atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM learning.service_requests
                        WHERE kind = 'GRADING' AND status = 'PENDING' AND user_id <> :teacher AND deadline_at > :now)
                           AS open,
                       (SELECT COUNT(*) FROM learning.service_requests
                        WHERE kind = 'GRADING' AND status = 'ASSIGNED' AND assignee_id = :teacher
                          AND deadline_at > :now AND deadline_at <= :soon) AS due,
                       (SELECT COUNT(*) FROM learning.questions
                        WHERE source = 'AI' AND status = 'PENDING_REVIEW' AND reviewed_by IS NULL) AS ai
                """, parameters, (row, number) -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("GRADING_DUE_SOON", row.getLong("due"));
            counts.put("GRADING_OPEN", row.getLong("open"));
            counts.put("AI_REVIEW", row.getLong("ai"));
            return counts;
        });
    }

    /** AI_PUBLISH: AI questions a teacher already checked, waiting for a content editor other than that teacher. */
    @Transactional(readOnly = true)
    public Map<String, Long> contentEditorQueues(long editorId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM learning.questions
                WHERE source = 'AI' AND status = 'PENDING_REVIEW' AND reviewed_by IS NOT NULL AND reviewed_by <> :me
                """, new MapSqlParameterSource("me", editorId), Long.class);
        return Map.of("AI_PUBLISH", count == null ? 0L : count);
    }
}
