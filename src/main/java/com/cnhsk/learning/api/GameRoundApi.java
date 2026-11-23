package com.cnhsk.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.practice.PracticeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Game lifecycle boundary. Existing engine rules remain authoritative. */
@Component
public class GameRoundApi {
    private final PracticeService practice;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    GameRoundApi(@Lazy PracticeService practice, NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.practice = practice;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockStart(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "game:start:" + userId), result -> null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void abandonActive(long userId, GameCode code, Instant now) {
        // Do not lock these rows in the outer transaction: abandon uses REQUIRES_NEW.
        var rounds = jdbc.query("SELECT id, served_at, served_items FROM learning.attempts "
                        + "WHERE user_id = :user AND kind = 'GAME' AND ref_type = 'GAME_ROUND' "
                        + "AND game_code = :code AND status = 'IN_PROGRESS' ORDER BY id",
                Map.of("user", userId, "code", code.name()), (row, index) -> {
                    try {
                        JsonNode duration = mapper.readTree(row.getString("served_items"))
                                .path("meta").path("timeLimitSeconds");
                        if (!duration.isIntegralNumber() || !duration.canConvertToInt() || duration.intValue() <= 0) {
                            throw new IllegalStateException("Invalid stored game duration.");
                        }
                        return new ActiveRound(row.getLong("id"), row.getTimestamp("served_at").toInstant()
                                .plusSeconds(3L + duration.intValue()));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalStateException("Invalid stored game snapshot.");
                    }
                });
        for (var round : rounds) {
            if (now.isBefore(round.deadline())) practice.abandon(userId, round.id());
        }
    }

    /** Rankings are kept per HSK level, so each round stores the level it was played at. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordHskLevel(long roundId, int hskLevel) {
        int updated = jdbc.update("UPDATE learning.attempts SET hsk_level = :level "
                        + "WHERE id = :id AND kind = 'GAME' AND ref_type = 'GAME_ROUND'",
                Map.of("level", hskLevel, "id", roundId));
        if (updated != 1) throw new IllegalStateException("Game round to record the HSK level on is missing.");
    }

    /** A contest round counts only toward its contest, so it carries the contest instead of a level. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordContest(long roundId, long contestId) {
        int updated = jdbc.update("UPDATE learning.attempts SET ref_id = :contest "
                        + "WHERE id = :id AND kind = 'GAME' AND ref_type = 'GAME_ROUND'",
                Map.of("contest", contestId, "id", roundId));
        if (updated != 1) throw new IllegalStateException("Game round to record the contest on is missing.");
    }

    /** Ownership, status and expiry checks are performed by the existing engine. */
    public void abandon(long userId, long roundId) {
        practice.abandon(userId, roundId);
    }

    /** Server-only snapshot; community projects it into explicitly public DTOs after checking ownership. */
    @Transactional(readOnly = true)
    public Optional<GameRoundHooks.Round> find(long roundId) {
        return read(roundId, null);
    }

    @Transactional(readOnly = true)
    public Optional<GameRoundHooks.Round> findOwned(long userId, long roundId) {
        return read(roundId, userId);
    }

    private Optional<GameRoundHooks.Round> read(long roundId, Long owner) {
        return jdbc.query("SELECT id, user_id, game_code, status, served_at, expires_at, served_items "
                        + "FROM learning.attempts WHERE id = :id AND kind = 'GAME' AND ref_type = 'GAME_ROUND'",
                Map.of("id", roundId), (row, index) -> {
                    if (owner != null && row.getLong("user_id") != owner) {
                        throw new com.cnhsk.shared.exception.ApiException(com.cnhsk.shared.exception.ErrorCode.ATTEMPT_NOT_OWNED,
                                org.springframework.http.HttpStatus.FORBIDDEN, "Không có quyền truy cập lượt luyện này.", Map.of());
                    }
                    try {
                        JsonNode snapshot = mapper.readTree(row.getString("served_items"));
                        if (snapshot == null || !snapshot.isObject() || !snapshot.path("items").isArray()
                                || snapshot.path("items").isEmpty() || !snapshot.path("meta").path("uiConfig").isObject()) {
                            throw invalidSnapshot();
                        }
                        JsonNode meta = snapshot.path("meta");
                        int duration = positive(meta.path("timeLimitSeconds"));
                        int points = positive(meta.path("pointsPerCorrect"));
                        var served = mapper.treeToValue(snapshot, PracticeService.ServedSnapshot.class);
                        List<PracticeApi.ServedItem> items = served.items().stream().map(item ->
                                mapper.convertValue(item, PracticeApi.ServedItem.class)).toList();
                        int gradable = Math.toIntExact(served.items().stream().filter(item -> item.type().gradable()).count());
                        return new GameRoundHooks.Round(row.getLong("id"), row.getLong("user_id"),
                                GameCode.valueOf(row.getString("game_code")), row.getString("status"),
                                row.getTimestamp("served_at").toInstant(), row.getTimestamp("expires_at").toInstant(),
                                duration, points, gradable,
                                mapper.convertValue(meta.path("uiConfig"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }), items);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
                        throw invalidSnapshot();
                    }
                }).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public GameRoundHooks.AnswerTotals totals(long roundId) {
        return jdbc.queryForObject("SELECT COUNT(*) FILTER (WHERE is_correct = true) AS correct, COUNT(*) AS answered "
                        + "FROM learning.attempt_items WHERE attempt_id = :id", Map.of("id", roundId),
                (row, index) -> new GameRoundHooks.AnswerTotals(row.getLong("correct"), row.getLong("answered")));
    }

    @Transactional(readOnly = true)
    public long bestCorrectBefore(long userId, GameCode gameCode, long roundId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(score), 0) FROM learning.attempts "
                        + "WHERE user_id = :user AND kind = 'GAME' AND game_code = :code "
                        + "AND status = 'SUBMITTED' AND suspicious = false AND id <> :id",
                Map.of("user", userId, "code", gameCode.name(), "id", roundId), Long.class);
    }

    private static int positive(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) throw invalidSnapshot();
        return value.intValue();
    }

    private static IllegalStateException invalidSnapshot() {
        return new IllegalStateException("Invalid stored game snapshot.");
    }

    private record ActiveRound(long id, Instant deadline) { }
}
