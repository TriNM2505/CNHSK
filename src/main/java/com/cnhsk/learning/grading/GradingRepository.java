package com.cnhsk.learning.grading;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grading-only persistence. Services own authorization, billing and the caller's transaction. */
@Repository
public class GradingRepository {
    private static final String COLUMNS = "id,user_id,status,payload,result,assignee_id,assigned_at,deadline_at,"
            + "score,feedback,payout,created_at,completed_at,error_message";
    private static final String CLAIM_DEADLINE = "(payload->>'claimDeadline')::timestamptz";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public GradingRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSubmit(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "grading:submit:" + userId), rows -> null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long createPending(long userId, JsonNode payload, Instant createdAt, Instant claimDeadline) {
        return jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,created_at,deadline_at) "
                        + "VALUES (:user,'GRADING','PENDING',CAST(:payload AS jsonb),:created,:deadline) RETURNING id",
                new MapSqlParameterSource("user", userId).addValue("payload", json(payload))
                        .addValue("created", Timestamp.from(createdAt)).addValue("deadline", Timestamp.from(claimDeadline)), Long.class);
    }

    @Transactional(readOnly = true)
    public Optional<RequestRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM learning.service_requests WHERE id=:id AND kind='GRADING'",
                Map.of("id", id), (row, index) -> request(row)).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public long countMine(long userId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE kind='GRADING' AND user_id=:user "
                + "AND (:status IS NULL OR status=:status)", mineParameters(userId, status), Long.class);
    }

    @Transactional(readOnly = true)
    public List<RequestRow> findMine(long userId, String status, int page, int size) {
        return jdbc.query("SELECT " + COLUMNS + " FROM learning.service_requests WHERE kind='GRADING' AND user_id=:user "
                        + "AND (:status IS NULL OR status=:status) ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset",
                mineParameters(userId, status).addValue("size", size).addValue("offset", Math.multiplyExact((long) page, size)),
                (row, index) -> request(row));
    }

    private MapSqlParameterSource mineParameters(long userId, String status) {
        return new MapSqlParameterSource("user", userId).addValue("status", status, java.sql.Types.VARCHAR);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveEssay(long userId, String essayHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM learning.service_requests "
                        + "WHERE kind='GRADING' AND user_id=:user AND status IN ('PENDING','ASSIGNED') "
                        + "AND payload->>'essayHash'=:hash)", Map.of("user", userId, "hash", essayHash), Boolean.class));
    }

    @Transactional(readOnly = true)
    public long countActiveClaims(long teacherId) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE kind='GRADING' "
                + "AND status='ASSIGNED' AND assignee_id=:teacher", Map.of("teacher", teacherId), Long.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(long id, long teacherId, Instant now, Instant gradingDeadline) {
        return jdbc.update("UPDATE learning.service_requests SET status='ASSIGNED',assignee_id=:teacher,assigned_at=:now,"
                        + "deadline_at=:deadline,payload=jsonb_set(payload,'{claimDeadline}',to_jsonb(deadline_at),true) "
                        + "WHERE id=:id AND kind='GRADING' AND status='PENDING' AND deadline_at>:now AND user_id<>:teacher",
                parameters(id, now).addValue("teacher", teacherId).addValue("deadline", Timestamp.from(gradingDeadline))) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancel(long id, long ownerId, Instant now) {
        return jdbc.update("UPDATE learning.service_requests SET status='FAILED',error_message='CANCELLED_BY_LEARNER',completed_at=:now "
                        + "WHERE id=:id AND kind='GRADING' AND user_id=:owner AND status='PENDING'",
                parameters(id, now).addValue("owner", ownerId)) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean release(long id, long teacherId, Instant now) {
        String remaining = CLAIM_DEADLINE + ">:now";
        return jdbc.update("UPDATE learning.service_requests SET status=CASE WHEN " + remaining + " THEN 'PENDING' ELSE 'EXPIRED' END,"
                        + "assignee_id=CASE WHEN " + remaining + " THEN NULL ELSE assignee_id END,"
                        + "assigned_at=CASE WHEN " + remaining + " THEN NULL ELSE assigned_at END,"
                        + "deadline_at=CASE WHEN " + remaining + " THEN " + CLAIM_DEADLINE + " ELSE deadline_at END,"
                        + "completed_at=CASE WHEN " + remaining + " THEN NULL ELSE CAST(:now AS timestamptz) END "
                        + "WHERE id=:id AND kind='GRADING' AND status='ASSIGNED' AND assignee_id=:teacher "
                        + "AND payload->>'claimDeadline' IS NOT NULL",
                parameters(id, now).addValue("teacher", teacherId)) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean complete(long id, long teacherId, Instant now, int score, String feedback, JsonNode result, int payout) {
        return jdbc.update("UPDATE learning.service_requests SET status='COMPLETED',score=:score,feedback=:feedback,"
                        + "result=CAST(:result AS jsonb),payout=:payout,completed_at=:now "
                        + "WHERE id=:id AND kind='GRADING' AND status='ASSIGNED' AND assignee_id=:teacher AND deadline_at>=:now",
                parameters(id, now).addValue("teacher", teacherId).addValue("score", score).addValue("feedback", feedback)
                        .addValue("result", json(result)).addValue("payout", payout)) == 1;
    }

    @Transactional(readOnly = true)
    public List<RequestRow> findOverdue(Instant now) {
        return jdbc.query("SELECT " + COLUMNS + " FROM learning.service_requests WHERE kind='GRADING' "
                        + "AND status IN ('PENDING','ASSIGNED') AND deadline_at<:now ORDER BY deadline_at,id LIMIT 100",
                Map.of("now", Timestamp.from(now)), (row, index) -> request(row));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expire(long id, String expectedStatus, Instant now) {
        return jdbc.update("UPDATE learning.service_requests SET status='EXPIRED',completed_at=:now "
                        + "WHERE id=:id AND kind='GRADING' AND status=:expected AND status IN ('PENDING','ASSIGNED') AND deadline_at<:now",
                parameters(id, now).addValue("expected", expectedStatus)) == 1;
    }

    private MapSqlParameterSource parameters(long id, Instant now) {
        return new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(now));
    }

    private String json(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("Grading JSON must be an object.");
        return value.toString();
    }

    private RequestRow request(ResultSet row) throws SQLException {
        return new RequestRow(row.getLong("id"), row.getLong("user_id"), row.getString("status"),
                parse(row.getString("payload")), parse(row.getString("result")), row.getObject("assignee_id", Long.class),
                instant(row, "assigned_at"), instant(row, "deadline_at"), row.getObject("score", Integer.class),
                row.getString("feedback"), row.getObject("payout", Integer.class), instant(row, "created_at"),
                instant(row, "completed_at"), row.getString("error_message"));
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored grading JSON is invalid."); }
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Server-only data; controllers must build the role-specific DTO, never return this row. */
    public record RequestRow(long id, long userId, String status, JsonNode payload, JsonNode result, Long assigneeId,
            Instant assignedAt, Instant deadline, Integer score, String feedback, Integer payout,
            Instant createdAt, Instant completedAt, String errorMessage) {
        @Override
        public String toString() { return "GradingRequest[id=" + id + ", status=" + status + "]"; }
    }
}
