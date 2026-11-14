package com.cnhsk.learning.api;

import java.time.Instant;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Game lifecycle boundary. Existing engine rules remain authoritative. */
@Component
public class GameRoundApi {
    private final PracticeService practice;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    GameRoundApi(PracticeService practice, NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
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

    /** Ownership, status and expiry checks are performed by the existing engine. */
    public void abandon(long userId, long roundId) {
        practice.abandon(userId, roundId);
    }

    private record ActiveRound(long id, Instant deadline) { }
}
