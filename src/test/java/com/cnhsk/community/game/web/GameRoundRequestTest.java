package com.cnhsk.community.game.web;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GameRoundRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void AT11_exactContractFieldsAndBoundsAreAccepted() {
        assertThat(GameRoundRequest.parse("{\"no\":1,\"answer\":\"A\",\"durationMs\":0}", mapper))
                .isEqualTo(new GameRoundRequest(1, "A", 0));
        assertThat(GameRoundRequest.parse("{\"no\":2147483647,\"answer\":\"" + "字".repeat(100)
                + "\",\"durationMs\":600000}", mapper).durationMs()).isEqualTo(600000);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"null", "[]", "{", "{}", "{\"no\":1,\"answer\":\"A\"}",
            "{\"no\":0,\"answer\":\"A\",\"durationMs\":1}", "{\"no\":1.5,\"answer\":\"A\",\"durationMs\":1}",
            "{\"no\":2147483648,\"answer\":\"A\",\"durationMs\":1}",
            "{\"no\":1,\"answer\":null,\"durationMs\":1}", "{\"no\":1,\"answer\":1,\"durationMs\":1}",
            "{\"no\":1,\"answer\":\"A\",\"durationMs\":-1}", "{\"no\":1,\"answer\":\"A\",\"durationMs\":600001}",
            "{\"no\":1,\"answer\":\"A\",\"durationMs\":1.5}", "{\"no\":1,\"answer\":\"A\",\"durationMs\":\"1\"}",
            "{\"no\":1,\"answer\":\"A\",\"durationMs\":1,\"score\":999999}",
            "{\"no\":1,\"answer\":\"A\",\"durationMs\":1,\"correct\":true}"})
    void AT11_wrongTypesMissingAndAdditionalFieldsAre422(String body) {
        assertThatThrownBy(() -> GameRoundRequest.parse(body, mapper)).isInstanceOfSatisfying(ApiException.class,
                error -> { assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(error.getStatus().value()).isEqualTo(422); });
    }

    @Test void AT11_longAnswerIsRejectedAndFinishAbandonOnlyAllowAbsentOrEmptyObject() {
        assertThatThrownBy(() -> GameRoundRequest.parse("{\"no\":1,\"answer\":\"" + "A".repeat(101)
                + "\",\"durationMs\":1}", mapper)).isInstanceOf(ApiException.class);
        GameRoundRequest.empty(null, mapper); GameRoundRequest.empty("{}", mapper);
        for (String body : java.util.List.of("null", "[]", "{", "{\"score\":999999}", "{\"answers\":[]}", "{\"points\":1}")) {
            assertThatThrownBy(() -> GameRoundRequest.empty(body, mapper)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "1.5"})
    void Q_T06_04_badIdsAre404(String value) {
        assertThatThrownBy(() -> GameRoundRequest.id(value)).isInstanceOfSatisfying(ApiException.class,
                error -> { assertThat(error.getStatus().value()).isEqualTo(404);
                    assertThat(error.getCode()).isEqualTo(ErrorCode.ROUND_NOT_FOUND); });
    }

    @Test void Q_T06_04_int64IdIsParsedWithoutPrecisionLoss() {
        assertThat(GameRoundRequest.id("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }
}
