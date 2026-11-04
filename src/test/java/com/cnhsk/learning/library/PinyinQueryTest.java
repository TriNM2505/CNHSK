package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PinyinQueryTest {

    @ParameterizedTest
    @ValueSource(strings = {"ni hao", "nǐ hǎo", "ni3hao3", "NI3-HAO3", "ni3 hao3", "ni3'hao3"})
    void FR004_AT04_numberedMarkedAndPlainQueriesShareTheImportedIndex(String query) {
        assertThat(PinyinQuery.parse(query).normalized()).isEqualTo("nihao");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "nǐ hǎo|nǐhǎo", "ni3hao3|nǐhǎo", "ma1|mā", "ma2|má", "ma3|mǎ", "ma4|mà", "ma5|ma",
            "lv4|lǜ", "lü4|lǜ", "lǜ|lǜ", "nv3 er2|nǚér", "shui3|shuǐ", "liu2|liú", "ou3|ǒu",
            "zhong1guo2|zhōngguó", "huar1|huār", "de5|de"
    })
    void FR004_explicitTonesHaveACompactPreferenceWithoutChangingTheSharedUtility(String query, String preferred) {
        assertThat(PinyinQuery.parse(query).preferredPinyin()).isEqualTo(preferred);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ni hao", "xian", "xi'an", "nǐ3", "ni33hao3", "ni3x", "ma0", "ma6", "xx1", "m1", "nihao3"})
    void FR004_unmarkedAmbiguousOrInvalidNumberedInputDoesNotInventATonePreference(String query) {
        assertThat(PinyinQuery.parse(query).preferredPinyin()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"xian", "xi'an", "xi-an", "xi an"})
    void FR004_AT04_syllableSeparatorsDoNotExcludeEitherXianSpelling(String query) {
        assertThat(PinyinQuery.parse(query).normalized()).isEqualTo("xian");
    }
}
