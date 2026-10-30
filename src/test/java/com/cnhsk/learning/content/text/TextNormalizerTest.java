package com.cnhsk.learning.content.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextNormalizerTest {

    @ParameterizedTest(name = "FR030 [{index}] column={0}")
    @MethodSource("cells")
    void FR030_cellsUseNfcAndColumnSpecificWhitespace(String column, String input, String expected) {
        String actual = TextNormalizer.normalizeCell(column, input);

        assertThat(actual).isEqualTo(expected);
        assertThat(TextNormalizer.normalizeCell(column, actual)).isEqualTo(expected);
        if (actual != null) {
            assertThat(Normalizer.isNormalized(actual, Normalizer.Form.NFC)).isTrue();
        }
    }

    @ParameterizedTest(name = "FR032 [{index}] {0}")
    @MethodSource("meanings")
    void FR032_AT035_meaningsRemoveVietnameseDiacriticsAndKeepPunctuation(String input, String expected) {
        assertThat(TextNormalizer.normalizeMeaning(input)).isEqualTo(expected);
    }

    @Test
    void FR032_meaningCaseFoldingDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(TextNormalizer.normalizeMeaning("I ĐI HỌC")).isEqualTo("i di hoc");
            assertThat(TextNormalizer.normalizeCell("name", " I ")).isEqualTo("I");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static Stream<Arguments> cells() {
        return Stream.of(
                Arguments.of("text", null, null),
                Arguments.of("text", "", ""),
                Arguments.of("text", "\t\u00A0\u3000\n", ""),
                Arguments.of("text", " \t你 \r\n 好\u00A0 ", "你 好"),
                Arguments.of("pinyin", "\u00A0Nǐ\t \n hǎo\u3000", "Nǐ hǎo"),
                Arguments.of("name", "\u2003  Tên\u2009chủ   đề  ", "Tên chủ đề"),
                Arguments.of("code", "  AB\u00A0 CD  ", "AB CD"),
                Arguments.of("meaning_vi", "  Đi  học\n  cùng bạn  ", "Đi  học\n  cùng bạn"),
                Arguments.of("title_vi", "\u00A0Tên   ngắn\u00A0", "Tên   ngắn"),
                Arguments.of("explanation", " \nDòng  một\r\n  Dòng hai\n ", "Dòng  một\r\n  Dòng hai"),
                Arguments.of("explanation_vi", "  a\t  b  ", "a\t  b"),
                Arguments.of("description", "  a\n\n  b  ", "a b"),
                Arguments.of("meaning_vi", Normalizer.normalize("  Đi học  ", Normalizer.Form.NFD), "Đi học"),
                Arguments.of(null, "  a  b  ", "a b"),
                Arguments.of("text", "  你😀\t好  ", "你😀 好"));
    }

    private static Stream<Arguments> meanings() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of("\t\u00A0 ", ""),
                Arguments.of("Đi học", "di hoc"),
                Arguments.of(" ĐẶNG Thị HỒNG ", "dang thi hong"),
                Arguments.of("Chữ;  nghĩa, Đọc sách", "chu;  nghia, doc sach"),
                Arguments.of("Nữ; lữ khách", "nu; lu khach"),
                Arguments.of(" \tĐẾN  \n  TRƯỜNG\t ", "den  \n  truong"),
                Arguments.of("我; 中文", "我; 中文"),
                Arguments.of(Normalizer.normalize("Đi học", Normalizer.Form.NFD), "di hoc"));
    }
}
