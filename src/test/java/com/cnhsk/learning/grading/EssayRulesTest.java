package com.cnhsk.learning.grading;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EssayRulesTest {
    @ParameterizedTest
    @ValueSource(ints = {49, 501})
    void FR002_AT01_hanziOutsideTheRangeReturns422WithCountOnly(int count) {
        String essay = "字".repeat(count);
        assertThatThrownBy(() -> EssayRules.validate(essay, config())).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.GRADING_INVALID_ESSAY);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(error.getExtra()).containsExactly(Map.entry("details", Map.of("hanziCount", count)));
            assertThat(error.getMessage()).doesNotContain(essay);
            assertThat(error.getCause()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 500})
    void FR002_hanziEndpointsAreAccepted(int count) {
        var essay = EssayRules.validate("字".repeat(count), config());
        assertThat(essay.hanziCount()).isEqualTo(count);
        assertThat(essay.essayHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void FR002_countsOnlyTheBaseCjkUnifiedIdeographsBlock() {
        assertThat(EssayRules.countHanzi("\u4E00\u9FFF\u3400\uF900\uD840\uDC00😀，。abc123")).isEqualTo(2);
        assertThat(EssayRules.countHanzi(null)).isZero();
    }

    @Test
    void FR002_AT01_totalLengthUsesCodePointsAndUnicodeTrimWithoutChangingInternalLines() {
        String body = "字".repeat(50) + "😀".repeat(2950);
        assertThat(EssayRules.validate("\u00A0\u3000\t" + body + "\r\n\u202F", config()).essay()).isEqualTo(body);
        assertThatThrownBy(() -> EssayRules.validate(body + "😀", config())).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getExtra()).containsEntry("details", Map.of("hanziCount", 50)));
        String lines = "字".repeat(25) + "\r\n\r\n  " + "字".repeat(25);
        assertThat(EssayRules.validate(" \n" + lines + "\t ", config()).essay()).isEqualTo(lines);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\r\n\u00A0\u3000", "only Latin and punctuation!"})
    void FR002_missingOrNonHanziEssaysAreRejectedWithoutPrivateInput(String value) {
        assertThatThrownBy(() -> EssayRules.validate(value, config())).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getExtra()).containsEntry("details", Map.of("hanziCount", 0)));
    }

    @Test
    void FR003_hashUsesUtf8Sha256AndOnlyCollapsesWhitespace() {
        assertThat(EssayRules.essayHash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(EssayRules.essayHash("\u3000字\r\n\t\u00A0A 字 \n"))
                .isEqualTo(EssayRules.essayHash("字 A 字"));
        assertThat(EssayRules.essayHash("字A")).isNotEqualTo(EssayRules.essayHash("字a"));
        assertThat(EssayRules.essayHash("字，字")).isNotEqualTo(EssayRules.essayHash("字,字"));
        assertThat(EssayRules.essayHash("字字")).isNotEqualTo(EssayRules.essayHash("字 字"));
        assertThat(EssayRules.essayHash("字é")).isNotEqualTo(EssayRules.essayHash("字e\u0301"));
        assertThat(EssayRules.essayHash(null)).isEqualTo(EssayRules.essayHash(""));
    }

    @Test
    void FR002_rulesReadTheConfigurationAndDiagnosticsDoNotPrintTheEssay() {
        GradingConfig config = config(); config.setMinHanzi(2); config.setMaxHanzi(3); config.setMaxChars(5);
        var essay = EssayRules.validate("  字字😀  ", config);
        assertThat(essay.hanziCount()).isEqualTo(2);
        assertThat(essay.toString()).isEqualTo("Essay[hanziCount=2]").doesNotContain("字", essay.essayHash());
        assertThatIllegalArgumentException().isThrownBy(() -> EssayRules.validate("private", null))
                .withMessage("Grading configuration is required.");
    }

    private GradingConfig config() {
        GradingConfig config = new GradingConfig();
        config.setMinHanzi(50); config.setMaxHanzi(500); config.setMaxChars(3000);
        return config;
    }
}
