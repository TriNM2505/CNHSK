package com.cnhsk.learning.practice.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PinyinGraderTest {

    @ParameterizedTest
    @ValueSource(strings = {"wo3men", "wǒmen", "wo3 men5", "WO3MEN", " wo3-men5 ", "wo3'men5", "wǒmen"})
    void FR011_AT18_acceptsNumberedMarkedNeutralAndNfcEquivalentPinyin(String answer) {
        assertThat(PinyinGrader.matches(answer, List.of("wo3men5"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nü3", "nv3", "nǚ", "nu:3", "NÜ3", "NU:3", "nǚ"})
    void FR011_AT18_acceptsAllSupportedUmlautForms(String answer) {
        assertThat(PinyinGrader.matches(answer, List.of("nv3"))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"women,wo3men5", "hao,hao3", "hao2,hao3", "nu3,nv3", "nü,nv3", "!!!,hao3",
            "wǒ123,wo3", "huāmǐn,x", "ma1,ma5"})
    void FR011_AT18_rejectsMissingWrongOrInvalidTones(String answer, String expected) {
        assertThat(PinyinGrader.matches(answer, List.of(expected))).isFalse();
    }

    @Test
    void FR011_normalizesEveryAcceptedAlternativeAndRejectsAbsentInput() {
        assertThat(PinyinGrader.matches("ní", List.of("ni3", "ni2"))).isTrue();
        assertThat(PinyinGrader.matches("ni3", List.of("nǐ"))).isTrue();
        assertThat(PinyinGrader.matches("men", List.of("men5"))).isTrue();
        assertThat(PinyinGrader.matches("", List.of(""))).isFalse();
        assertThat(PinyinGrader.matches("  ", List.of("men5"))).isFalse();
        assertThat(PinyinGrader.matches(null, List.of("ni3"))).isFalse();
        assertThat(PinyinGrader.matches("ni3", null)).isFalse();
        assertThat(PinyinGrader.matches("ni3", List.of())).isFalse();
    }
}
