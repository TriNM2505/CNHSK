package com.cnhsk.learning.content.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CjkUtilsTest {

    @ParameterizedTest(name = "FR009 [{index}] code point {0}")
    @MethodSource("codePoints")
    void FR009_cjkCodePointsUseOnlyTheTwoSpecifiedBlocks(int codePoint, boolean expected) {
        assertThat(CjkUtils.isCjk(codePoint)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "FR009 [{index}] CJK string {0}")
    @MethodSource("strings")
    void FR009_cjkStringsMustBeNonemptyAndContainOnlySupportedCodePoints(String input, boolean expected) {
        assertThat(CjkUtils.isCjk(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "FR009 [{index}] broken text, CJK required={1}")
    @MethodSource("brokenText")
    void FR009_AT012_brokenTextUsesTheColumnContext(String input, boolean requiresCjk, boolean expected) {
        assertThat(CjkUtils.isBroken(input, requiresCjk)).isEqualTo(expected);
    }

    private static Stream<Arguments> codePoints() {
        return Stream.of(
                Arguments.of(0x3400, true), Arguments.of(0x4DBF, true),
                Arguments.of(0x4E00, true), Arguments.of(0x9FFF, true),
                Arguments.of(0x33FF, false), Arguments.of(0x4DC0, false),
                Arguments.of(0x4DFF, false), Arguments.of(0xA000, false),
                Arguments.of(0x2E80, false), Arguments.of(0x2FDF, false),
                Arguments.of(0x3007, false), Arguments.of(0x20000, false),
                Arguments.of(-1, false), Arguments.of(0x110000, false));
    }

    private static Stream<Arguments> strings() {
        return Stream.of(
                Arguments.of(null, false), Arguments.of("", false),
                Arguments.of(" ", false), Arguments.of("你好", true),
                Arguments.of("\u3400\u4DBF", true), Arguments.of("\u4E00\u9FFF", true),
                Arguments.of("\u3400你", true), Arguments.of("你?", false),
                Arguments.of("你 好", false), Arguments.of("A你", false),
                Arguments.of("你😀", false),
                Arguments.of(new String(Character.toChars(0x20000)), false));
    }

    private static Stream<Arguments> brokenText() {
        return Stream.of(
                Arguments.of(null, true, false), Arguments.of("", true, false),
                Arguments.of("??", true, true), Arguments.of("??", false, false),
                Arguments.of("你?", true, true), Arguments.of("Why?", false, false),
                Arguments.of("\uFFFD", false, true), Arguments.of("你\uFFFD", true, true),
                Arguments.of("你好", true, false), Arguments.of("你好", false, false));
    }
}
