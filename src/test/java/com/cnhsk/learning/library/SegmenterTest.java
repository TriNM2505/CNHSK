package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.library.Segmenter.Lexeme;
import com.cnhsk.learning.library.Segmenter.Result;
import com.cnhsk.learning.library.Segmenter.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SegmenterTest {
    @Test
    void AT20_longestMatchUsesMyAndFamilyAsWholeWords() {
        Result result = Segmenter.split("我爱我的家人", dictionary());
        assertThat(result.segments()).containsExactly(new Segment("我", "wǒ", 1L), new Segment("爱", "ài", 2L),
                new Segment("我的", "wǒ de", 3L), new Segment("家人", "jiā rén", 5L));
        assertThat(result.pinyin()).isEqualTo("wǒ ài wǒ de jiā rén");
        preserved("我爱我的家人", result);
    }

    @Test
    void AT20_missingMyFallsBackToKnownCharactersWithoutGuessing() {
        var dictionary = dictionary();
        dictionary.remove("我的");
        Result result = Segmenter.split("我爱我的家人", dictionary);
        assertThat(result.segments()).extracting(Segment::text).containsExactly("我", "爱", "我", "的", "家人");
        assertThat(result.pinyin()).isEqualTo("wǒ ài wǒ de jiā rén");
        preserved("我爱我的家人", result);
    }

    @Test
    void AT20_nonCjkRunsKeepNumbersLatinPunctuationWhitespaceAndNewlines() {
        String text = "  HSK 3，。2026\n我爱 A' OR 1=1 --\t家人!🙂 ";
        Result result = Segmenter.split(text, dictionary());
        assertThat(result.segments()).extracting(Segment::text).containsExactly("  HSK 3，。2026\n", "我", "爱", " A' OR 1=1 --\t", "家人", "!🙂 ");
        assertThat(result.segments().get(0)).isEqualTo(new Segment("  HSK 3，。2026\n", null, null));
        assertThat(result.segments().get(3).lexemeId()).isNull();
        assertThat(result.pinyin()).isEqualTo("wǒ ài jiā rén");
        preserved(text, result);
    }

    @Test
    void unknownCjkIsOneCodePointPerSegmentIncludingExtensionB() {
        String text = "龘龖𠀀𠀁";
        Result result = Segmenter.split(text, Map.of());
        assertThat(result.segments()).extracting(Segment::text).containsExactly("龘", "龖", "𠀀", "𠀁");
        for (Segment segment : result.segments()) {
            assertThat(segment.text().codePointCount(0, segment.text().length())).isEqualTo(1);
            assertThat(segment.lexemeId()).isNull();
            assertThat(segment.pinyin()).isNull();
        }
        assertThat(result.pinyin()).isNull();
        preserved(text, result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x4E00, 0x3400, 0x20000, 0x2A700, 0x2B740, 0x2B820, 0x2CEB0, 0x30000, 0xF900, 0x2F800})
    void approvedAssignedCjkBlocksCanMatchDictionary(int point) {
        String text = new String(Character.toChars(point));
        assertThat(Segmenter.split(text, Map.of(text, new Lexeme(1, "pinyin"))).segments())
                .containsExactly(new Segment(text, "pinyin", 1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"，。", "2026", "HSK 3", "🙂", "々〇", "⼀⼁", "かな한글", "\n\t  "})
    void nonCjkLookupEntriesAreIgnoredAndTextIsGrouped(String text) {
        Result result = Segmenter.split(text, Map.of(text, new Lexeme(1, "not used")));
        assertThat(result.segments()).containsExactly(new Segment(text, null, null));
        assertThat(result.pinyin()).isNull();
    }

    @Test
    void maximumWordLengthIsEightCodePointsNotUtf16Units() {
        String eight = "𠀀𠀁𠀂𠀃𠀄𠀅𠀆𠀇";
        String nine = eight + "𠀈";
        Result result = Segmenter.split(nine, Map.of(eight, new Lexeme(8, "eight"), nine, new Lexeme(9, "nine")));
        assertThat(result.segments()).containsExactly(new Segment(eight, "eight", 8L), new Segment("𠀈", null, null));
        assertThat(result.pinyin()).isEqualTo("eight");
        assertThat(Segmenter.candidates(nine)).contains(eight).doesNotContain(nine);
        preserved(nine, result);
    }

    @Test
    void grammarSymbolsAreNotRemovedOrMatchedAcrossRuns() {
        var dictionary = Map.of("是…的", new Lexeme(1, "pattern"), "是的", new Lexeme(2, "shì de"),
                "是", new Lexeme(3, "shì"), "的", new Lexeme(4, "de"));
        assertThat(Segmenter.split("是…的", dictionary).segments()).containsExactly(
                new Segment("是", "shì", 3L), new Segment("…", null, null), new Segment("的", "de", 4L));
        assertThat(Segmenter.split("是的", dictionary).segments()).containsExactly(new Segment("是的", "shì de", 2L));
        assertThat(Segmenter.candidates("是…的")).containsExactly("是", "的");
    }

    @Test
    void nullAndBlankPinyinAreSkippedAndValidPinyinKeepsTones() {
        var dictionary = Map.of("我", new Lexeme(1, null), "爱", new Lexeme(2, "\t\u00a0"), "你", new Lexeme(3, "  nǐ \n"));
        Result result = Segmenter.split("我爱你", dictionary);
        assertThat(result.segments()).containsExactly(new Segment("我", null, 1L), new Segment("爱", null, 2L), new Segment("你", "nǐ", 3L));
        assertThat(result.pinyin()).isEqualTo("nǐ");
        assertThat(Segmenter.split("我爱", dictionary).pinyin()).isNull();
    }

    @Test
    void candidatesAreDistinctContiguousCjkSubstringsOnly() {
        assertThat(Segmenter.candidates("人人 A 人")).containsExactly("人", "人人");
        String distinct = new String(new int[] {0x4E00, 0x4E01, 0x4E02, 0x4E03, 0x4E04, 0x4E05, 0x4E06, 0x4E07, 0x4E08}, 0, 9);
        assertThat(Segmenter.candidates(distinct)).hasSize(44);
        assertThat(Segmenter.candidates(distinct)).allSatisfy(value -> assertThat(value.codePointCount(0, value.length())).isBetween(1, 8));
    }

    @Test
    void emptyAndNonCjkNeedNoRepositoryAndResultIsImmutable() {
        SegmenterQueryRepository repository = mock(SegmenterQueryRepository.class);
        Segmenter segmenter = new Segmenter(repository);
        assertThat(segmenter.segment("")).isEqualTo(new Result(List.of(), null));
        assertThat(segmenter.segment("  HSK 3，。\n").segments()).containsExactly(new Segment("  HSK 3，。\n", null, null));
        verifyNoInteractions(repository);
        assertThatThrownBy(() -> segmenter.segment("").segments().add(new Segment("x", null, null))).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> segmenter.segment(null));
        assertThatIllegalArgumentException().isThrownBy(() -> Segmenter.split(null, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> Segmenter.split("我", null));
        verifyNoInteractions(repository);
    }

    @Test
    void orchestrationQueriesOnceAndUsesReturnedDictionary() {
        SegmenterQueryRepository repository = mock(SegmenterQueryRepository.class);
        Segmenter segmenter = new Segmenter(repository);
        when(repository.lookup(Set.of("我", "我我"))).thenReturn(Map.of("我", new Lexeme(1, null)));
        assertThat(segmenter.segment("我我").segments()).hasSize(2);
        verify(repository).lookup(Set.of("我", "我我"));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void repositoryFailureHasNoInputInMessageCauseOrStackTrace() {
        SegmenterQueryRepository repository = mock(SegmenterQueryRepository.class);
        String privateText = "我爱我的家人";
        when(repository.lookup(anySet())).thenThrow(new IllegalStateException("SQL parameters " + privateText));
        assertThatThrownBy(() -> new Segmenter(repository).segment(privateText)).isInstanceOfSatisfying(Segmenter.SegmentationException.class, error -> {
            assertThat(error.getMessage()).doesNotContain(privateText);
            assertThat(error.getCause()).isNull();
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).doesNotContain(privateText, "SQL parameters");
        });
    }

    private Map<String, Lexeme> dictionary() {
        return new HashMap<>(Map.of("我", new Lexeme(1, "wǒ"), "爱", new Lexeme(2, "ài"), "我的", new Lexeme(3, "wǒ de"),
                "的", new Lexeme(4, "de"), "家人", new Lexeme(5, "jiā rén"), "家", new Lexeme(6, "jiā"), "人", new Lexeme(7, "rén")));
    }
    private void preserved(String input, Result result) { assertThat(result.segments().stream().map(Segment::text).collect(java.util.stream.Collectors.joining())).isEqualTo(input); }
}
