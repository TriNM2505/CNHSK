package com.cnhsk.learning.content.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PinyinUtilsTest {

    @ParameterizedTest(name = "FR031 [{index}] {0} -> {1}")
    @MethodSource("normalizedPinyin")
    void FR031_AT034_searchKeysRemoveTonesAndSeparatorsWithoutMergingUAndV(String input, String expected) {
        assertThat(PinyinUtils.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "FR033 [{index}] {0} -> {1}")
    @MethodSource("numberedPinyin")
    void FR033_AT036_toneMarksBecomeNumberedSyllables(String input, String expected) {
        assertThat(PinyinUtils.toNumbered(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "FR009 [{index}] character validity of {0}")
    @MethodSource("characterValidity")
    void FR009_pinyinCharacterValidationFollowsTheImportAlphabet(String input, boolean expected) {
        assertThat(PinyinUtils.hasValidCharacters(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "FR033 [{index}] reject unsupported input")
    @ValueSource(strings = {
            "ni3", "nǐhao3", "lu:", "你好", "nǐ?", "nǐ😀", "hàǒ", "lǚè", "abc", "xīān", "'", "--", "\u0301"
    })
    void FR033_invalidCharactersOrSyllablesAreRejectedWithoutGuessing(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> PinyinUtils.toNumbered(input));
    }

    @Test
    void FR033_nullAndWhitespaceRemainMissingValues() {
        assertThat(PinyinUtils.toNumbered(null)).isNull();
        assertThat(PinyinUtils.toNumbered("")).isEmpty();
        assertThat(PinyinUtils.toNumbered(" \t\u00A0\u3000 ")).isEmpty();
    }

    @Test
    void FR031_FR033_pinyinCaseFoldingDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(PinyinUtils.normalize("NI LǙ")).isEqualTo("nilv");
            assertThat(PinyinUtils.hasValidCharacters("NI")).isTrue();
            assertThat(PinyinUtils.toNumbered("NI")).isEqualTo("ni5");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void FR009_characterValidationDoesNotAddUnspecifiedSyllableRulesToImport() {
        assertThat(PinyinUtils.hasValidCharacters("abc")).isTrue();
        assertThat(PinyinUtils.normalize("Nǐ?")).isEqualTo("ni?");
    }

    private static Stream<Arguments> normalizedPinyin() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of(" \t\u00A0\u3000 ", ""),
                Arguments.of("nǚ'ér", "nver"),
                Arguments.of("Nǐ hǎo", "nihao"),
                Arguments.of("lǜ", "lv"),
                Arguments.of("lǖ lǘ lǚ lǜ", "lvlvlvlv"),
                Arguments.of("lü", "lv"),
                Arguments.of("u v ü", "uvv"),
                Arguments.of("LU LǙ", "lulv"),
                Arguments.of("xi'an", "xian"),
                Arguments.of("Xi-ān", "xian"),
                Arguments.of("Zhōngguó", "zhongguo"),
                Arguments.of(" Nǐ\u00A0hǎo\t ", "nihao"),
                Arguments.of(Normalizer.normalize("nǚ'ér", Normalizer.Form.NFD), "nver"));
    }

    private static Stream<Arguments> numberedPinyin() {
        return Stream.of(
                Arguments.of("nǐ hǎo", "ni3 hao3"),
                Arguments.of("NǏ HǍO", "ni3 hao3"),
                Arguments.of("nǐhǎo", "ni3 hao3"),
                Arguments.of("nǚ'ér", "nv3 er2"),
                Arguments.of("nǚ-ér", "nv3 er2"),
                Arguments.of("yī", "yi1"),
                Arguments.of("de", "de5"),
                Arguments.of("Zhōngguó", "zhong1 guo2"),
                Arguments.of("wǒmen", "wo3 men5"),
                Arguments.of("péngyou", "peng2 you5"),
                Arguments.of("shénme", "shen2 me5"),
                Arguments.of("tiānqì", "tian1 qi4"),
                Arguments.of("Shànghǎi", "shang4 hai3"),
                Arguments.of("mā", "ma1"),
                Arguments.of("má", "ma2"),
                Arguments.of("mǎ", "ma3"),
                Arguments.of("mà", "ma4"),
                Arguments.of("ma", "ma5"),
                Arguments.of("lǖ", "lv1"),
                Arguments.of("lǘ", "lv2"),
                Arguments.of("lǚ", "lv3"),
                Arguments.of("lǜ", "lv4"),
                Arguments.of("ā", "a1"),
                Arguments.of("á", "a2"),
                Arguments.of("ǎ", "a3"),
                Arguments.of("à", "a4"),
                Arguments.of("hē", "he1"),
                Arguments.of("hé", "he2"),
                Arguments.of("hě", "he3"),
                Arguments.of("hè", "he4"),
                Arguments.of("shuǐ", "shui3"),
                Arguments.of("liú", "liu2"),
                Arguments.of("duì", "dui4"),
                Arguments.of("lüè", "lve4"),
                Arguments.of("nüè", "nve4"),
                Arguments.of("xiān", "xian1"),
                Arguments.of("xī'ān", "xi1 an1"),
                Arguments.of("nǎr", "nar3"),
                Arguments.of("huār", "huar1"),
                Arguments.of("zhèr", "zher4"),
                Arguments.of("\u00A0Nǐ\t hǎo\u3000", "ni3 hao3"),
                Arguments.of(Normalizer.normalize("nǚ'ér", Normalizer.Form.NFD), "nv3 er2"),
                Arguments.of("Chīfàn", "chi1 fan4"),
                Arguments.of("gōngzuò", "gong1 zuo4"),
                Arguments.of("pǔtōnghuà", "pu3 tong1 hua4"));
    }

    private static Stream<Arguments> characterValidity() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of(" \t\u00A0 ", false),
                Arguments.of("Nǐ hǎo", true),
                Arguments.of("ü ǖ ǘ ǚ ǜ", true),
                Arguments.of("Xi'an Nǚ-ér", true),
                Arguments.of(Normalizer.normalize("nǚ'ér", Normalizer.Form.NFD), true),
                Arguments.of("nv", true),
                Arguments.of("ni3", false),
                Arguments.of("u:", false),
                Arguments.of("你好", false),
                Arguments.of("nǐ?", false),
                Arguments.of("nǐ\uFFFD", false),
                Arguments.of("nǐ😀", false),
                Arguments.of("nǐ’ér", false),
                Arguments.of("nǐ!", false),
                Arguments.of("nǐ_hao", false),
                Arguments.of("ế", false));
    }
}
