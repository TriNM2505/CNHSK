package com.cnhsk.community.game.web;

import java.util.Map;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;

/**
 * Strict request: a practice round is played and ranked at one HSK level that has game content, while a contest
 * round names the contest instead and mixes HSK 1–3.
 */
public record GameRoundStartRequest(Integer hskLevel, Long contestId) {
    /** Game content (lexemes with strokes, radicals and Sino-Vietnamese readings) exists for HSK 1–3 only. */
    public static final int MAX_LEVEL = 3;

    public static GameCode code(String value) {
        try {
            return GameCode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.INVALID_GAME_CODE, HttpStatus.BAD_REQUEST,
                    "Mã game không hợp lệ.", Map.of());
        }
    }

    public static GameRoundStartRequest parse(String body, ObjectMapper mapper) {
        if (body == null) throw invalid();
        try {
            var parser = mapper.getFactory().createParser(body);
            try (parser) {
                JsonNode value = mapper.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null || value.size() != 1) throw invalid();
                if (value.has("contestId")) {
                    var contest = value.get("contestId");
                    if (!contest.isIntegralNumber() || !contest.canConvertToLong() || contest.longValue() < 1) throw invalid();
                    return new GameRoundStartRequest(null, contest.longValue());
                }
                if (!value.has("hskLevel")) throw invalid();
                var level = value.get("hskLevel");
                if (!level.isIntegralNumber() || !level.canConvertToInt()
                        || level.intValue() < 1 || level.intValue() > MAX_LEVEL) throw invalid();
                return new GameRoundStartRequest(level.intValue(), null);
            }
        } catch (java.io.IOException exception) {
            throw invalid();
        }
    }

    static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu tạo ván không hợp lệ.", Map.of());
    }
}
