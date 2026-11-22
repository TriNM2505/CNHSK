package com.cnhsk.community.game.web;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GameRoundStartRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void theThreePlayableLevelsAreAccepted(int level) {
        assertThat(GameRoundStartRequest.parse("{\"hskLevel\":" + level + "}", mapper).hskLevel()).isEqualTo(level);
    }

    @Test void aRoundWithoutALevelIsRejected() {
        for (String body : new String[]{null, "{}"}) {
            assertThatThrownBy(() -> GameRoundStartRequest.parse(body, mapper)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"{\"hskLevel\":4}", "{\"hskLevel\":9}"})
    void levelsWithoutGameContentAreRejected(String body) {
        assertThatThrownBy(() -> GameRoundStartRequest.parse(body, mapper)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @ParameterizedTest @ValueSource(strings = {"null", "[]", "1", "true", "\"text\"", " ", "{", "{} {}",
            "{\"hskLevel\":null}", "{\"hskLevel\":0}", "{\"hskLevel\":10}", "{\"hskLevel\":1.0}",
            "{\"hskLevel\":\"1\"}", "{\"hskLevel\":true}", "{\"hskLevel\":99999999999999999999}",
            "{\"score\":999}", "{\"points\":1}", "{\"hskLevel\":1,\"userId\":7}"})
    void FR006_invalidTypesNullAndUnknownFieldsAre422(String body) {
        assertThatThrownBy(() -> GameRoundStartRequest.parse(body, mapper)).isInstanceOfSatisfying(ApiException.class,
                error -> { assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(error.getStatus().value()).isEqualTo(422); });
    }

    @Test void AT04_onlyTheSixExactCodesAreAccepted() {
        for (var code : GameCode.values()) assertThat(GameRoundStartRequest.code(code.name())).isEqualTo(code);
        for (var code : new String[]{"SNAKE", "char_rain", " CHAR_RAIN", "", null}) {
            assertThatThrownBy(() -> GameRoundStartRequest.code(code)).isInstanceOfSatisfying(ApiException.class,
                    error -> { assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_GAME_CODE);
                        assertThat(error.getStatus().value()).isEqualTo(400); });
        }
    }
}
