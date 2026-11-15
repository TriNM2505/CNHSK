package com.cnhsk.community.game.web;

import java.util.Map;
import java.util.Set;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/** A local strict parser; the application's shared ObjectMapper settings are unchanged. */
public record GameRoundRequest(int no, String answer, int durationMs) {
    private static final Set<String> FIELDS = Set.of("no", "answer", "durationMs");

    public static GameRoundRequest parse(String body, ObjectMapper mapper) {
        JsonNode root = object(body, mapper);
        if (root.size() != 3 || root.properties().stream().anyMatch(field -> !FIELDS.contains(field.getKey()))
                || !root.path("no").isIntegralNumber() || !root.path("no").canConvertToInt() || root.path("no").intValue() < 1
                || !root.path("answer").isTextual() || root.path("answer").textValue().length() > 100
                || !root.path("durationMs").isIntegralNumber() || !root.path("durationMs").canConvertToInt()
                || root.path("durationMs").intValue() < 0 || root.path("durationMs").intValue() > 600_000) {
            throw invalid();
        }
        return new GameRoundRequest(root.path("no").intValue(), root.path("answer").textValue(), root.path("durationMs").intValue());
    }

    public static void empty(String body, ObjectMapper mapper) {
        if (body != null && !body.isBlank() && !object(body, mapper).isEmpty()) throw invalid();
    }

    public static long id(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (NumberFormatException exception) { /* Use the same response as a missing round. */ }
        throw new ApiException(ErrorCode.ROUND_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy ván chơi.", Map.of());
    }

    private static JsonNode object(String body, ObjectMapper mapper) {
        try {
            JsonNode root = body == null ? null : mapper.readTree(body);
            if (root != null && root.isObject()) return root;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { /* No parser/input details leave this boundary. */ }
        throw invalid();
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu ván chơi không hợp lệ.", Map.of());
    }
}
