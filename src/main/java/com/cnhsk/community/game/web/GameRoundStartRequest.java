package com.cnhsk.community.game.web;

import java.util.Map;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;

/** Strict optional request: omitted body is different from an explicit JSON null. */
public record GameRoundStartRequest(Integer hskLevel) {
    public static GameCode code(String value) {
        try {
            return GameCode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.INVALID_GAME_CODE, HttpStatus.BAD_REQUEST,
                    "Mã game không hợp lệ.", Map.of());
        }
    }

    public static GameRoundStartRequest parse(String body, ObjectMapper mapper) {
        if (body == null) return new GameRoundStartRequest(null);
        try {
            var parser = mapper.getFactory().createParser(body);
            try (parser) {
                JsonNode value = mapper.readTree(parser);
                if (value == null || !value.isObject() || parser.nextToken() != null
                        || value.size() > 1 || (value.size() == 1 && !value.has("hskLevel"))) throw invalid();
                if (!value.has("hskLevel")) return new GameRoundStartRequest(null);
                var level = value.get("hskLevel");
                if (!level.isIntegralNumber() || !level.canConvertToInt()
                        || level.intValue() < 1 || level.intValue() > 9) throw invalid();
                return new GameRoundStartRequest(level.intValue());
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
