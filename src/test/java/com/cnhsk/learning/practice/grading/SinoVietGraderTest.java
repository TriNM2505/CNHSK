package com.cnhsk.learning.practice.grading;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SinoVietGraderTest {
    @ParameterizedTest @ValueSource(strings = {"nhân", "Nhân", " NHÂN ", "nhan", "nhân "})
    void caseSpacingAndToneMarksDoNotMatter(String answer) {
        assertThat(SinoVietGrader.matches(answer, List.of("nhân"))).isTrue();
    }

    @Test void theBarredDIsTypedAsAPlainD() {
        assertThat(SinoVietGrader.matches("dai", List.of("đại"))).isTrue();
        assertThat(SinoVietGrader.matches("đại", List.of("đại"))).isTrue();
    }

    @Test void anyReadingOfAMultiReadingCharacterIsAccepted() {
        assertThat(SinoVietGrader.matches("nhơn", List.of("nhân", "nhơn"))).isTrue();
        assertThat(SinoVietGrader.matches("nhon", List.of("nhân", "nhơn"))).isTrue();
    }

    @Test void decomposedInputMatchesComposedReadings() {
        assertThat(SinoVietGrader.matches("nhân", List.of("nhân"))).isTrue();
    }

    @Test void aDifferentWordIsWrong() {
        assertThat(SinoVietGrader.matches("người", List.of("nhân"))).isFalse();
        assertThat(SinoVietGrader.matches("nha", List.of("nhân"))).isFalse();
    }

    @Test void blankOrMissingInputIsWrong() {
        assertThat(SinoVietGrader.matches("", List.of("nhân"))).isFalse();
        assertThat(SinoVietGrader.matches("   ", List.of("nhân"))).isFalse();
        assertThat(SinoVietGrader.matches(null, List.of("nhân"))).isFalse();
        assertThat(SinoVietGrader.matches("nhân", List.of())).isFalse();
        assertThat(SinoVietGrader.matches("nhân", null)).isFalse();
    }
}
