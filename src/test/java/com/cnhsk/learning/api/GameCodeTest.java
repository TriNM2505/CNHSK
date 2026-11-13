package com.cnhsk.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GameCodeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void FR015_catalogContainsExactlySixApprovedCodesInOrder() {
        assertThat(GameCode.values()).extracting(Enum::name).containsExactly(
                "CHAR_RAIN", "PINYIN_MATCH", "RADICAL_MATCH", "CHAR_CATCH", "BRUSH_STROKE", "FLYING_DART");
    }

    @ParameterizedTest
    @CsvSource({
            "CHAR_RAIN, Mưa Chữ",
            "PINYIN_MATCH, Ghép Pinyin",
            "RADICAL_MATCH, Ghép Bộ Thủ",
            "CHAR_CATCH, Bắt Chữ",
            "BRUSH_STROKE, Thần Bút",
            "FLYING_DART, Phi Đao"
    })
    void FR015_approvedCodeResolvesToItsVietnameseDisplayName(String code, String displayName) {
        assertThat(GameCode.valueOf(code).displayName()).isEqualTo(displayName);
    }

    @ParameterizedTest
    @EnumSource(GameCode.class)
    void FR015_jsonUsesTechnicalCodeAndRoundTrips(GameCode game) throws Exception {
        String json = mapper.writeValueAsString(game);

        assertThat(json).isEqualTo("\"" + game.name() + "\"");
        assertThat(mapper.readValue(json, GameCode.class)).isSameAs(game);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "char_rain", "PINYIN_DART", "FLYING_DART "})
    void FR015_unapprovedOrNonExactCodesAreRejected(String code) {
        assertThatThrownBy(() -> GameCode.valueOf(code)).isInstanceOf(IllegalArgumentException.class);
    }
}
