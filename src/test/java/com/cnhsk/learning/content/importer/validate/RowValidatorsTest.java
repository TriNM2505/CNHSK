package com.cnhsk.learning.content.importer.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RowValidatorsTest {

    private final TopicRepository topics = mock(TopicRepository.class);
    private final LexemeRepository lexemes = mock(LexemeRepository.class);

    @BeforeEach
    void existingReferences() {
        when(topics.findByCodeIn(anyCollection()))
                .thenReturn(List.of(new Topic("DAILY_LIFE", "Sinh hoạt", (short) 1)));
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.WORD), anyCollection()))
                .thenReturn(List.of(new Lexeme(LexemeKind.WORD, "我们", "chúng ta")));
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.CHAR), anyCollection()))
                .thenReturn(List.of(new Lexeme(LexemeKind.CHAR, "我", "tôi")));
    }

    @ParameterizedTest(name = "FR009 valid {0}")
    @EnumSource(value = ImportDataType.class, names = {"LEXEME", "TOPIC", "TOPIC_ITEM"})
    void FR009_validRowsRetainPhysicalLineAndDoNotInventOptionalValues(ImportDataType type) {
        RowValidationResult result = validate(type, row(type, 7, Map.of()));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.validRows()).hasSize(1);
        assertThat(result.validRows().get(0).line()).isEqualTo(7);
        assertThat(result.validRows().get(0).values()).doesNotContainKeys("order_index", "audio_url", "tags");
        assertThat(result.errors()).isEmpty();
        assertThat(validator(type).dataType()).isEqualTo(type);
    }

    @ParameterizedTest(name = "FR009 [{index}] {0} {1} => {3}")
    @MethodSource("invalidCells")
    void FR009_section4CellRulesUseApprovedErrorCodes(ImportDataType type, String column, String value, String code) {
        RowValidationResult result = validate(type, row(type, 3, Map.of(column, value)));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).filteredOn(error -> column.equals(error.column()))
                .singleElement().satisfies(error -> {
                    assertThat(error.line()).isEqualTo(3);
                    assertThat(error.code()).isEqualTo(code);
                    assertThat(error.message()).isNotBlank();
                });
        assertThat(result.errors()).extracting(RowError::column).doesNotHaveDuplicates();
    }

    private static Stream<Arguments> invalidCells() {
        ImportDataType lexeme = ImportDataType.LEXEME;
        ImportDataType topic = ImportDataType.TOPIC;
        ImportDataType item = ImportDataType.TOPIC_ITEM;
        return Stream.of(
                Arguments.of(lexeme, "kind", "", "REQUIRED_MISSING"),
                Arguments.of(lexeme, "kind", "word", "INVALID_VALUE"),
                Arguments.of(lexeme, "kind", "GRAMMAR", "INVALID_VALUE"),
                Arguments.of(lexeme, "text", " \t ", "REQUIRED_MISSING"),
                Arguments.of(lexeme, "text", "我a", "NOT_CJK"),
                Arguments.of(lexeme, "text", "我 们", "NOT_CJK"),
                Arguments.of(lexeme, "text", "\uD840\uDC00", "NOT_CJK"),
                Arguments.of(lexeme, "text", "我".repeat(21), "TOO_LONG"),
                Arguments.of(lexeme, "text", "??", "BROKEN_TEXT"),
                Arguments.of(lexeme, "pinyin", "", "REQUIRED_MISSING"),
                Arguments.of(lexeme, "pinyin", "wo3men5", "INVALID_PINYIN"),
                Arguments.of(lexeme, "pinyin", "nǐ?", "INVALID_PINYIN"),
                Arguments.of(lexeme, "pinyin", "a".repeat(201), "TOO_LONG"),
                Arguments.of(lexeme, "sino_viet", "a".repeat(201), "TOO_LONG"),
                Arguments.of(lexeme, "meaning_vi", "", "REQUIRED_MISSING"),
                Arguments.of(lexeme, "meaning_vi", "a".repeat(1001), "TOO_LONG"),
                Arguments.of(lexeme, "meaning_vi", "Lỗi \uFFFD", "BROKEN_TEXT"),
                Arguments.of(lexeme, "hsk_level", "0", "INVALID_HSK_LEVEL"),
                Arguments.of(lexeme, "hsk_level", "10", "INVALID_HSK_LEVEL"),
                Arguments.of(lexeme, "hsk_level", "1.0", "INVALID_HSK_LEVEL"),
                Arguments.of(lexeme, "hsk_level", "999999999999999", "INVALID_HSK_LEVEL"),
                Arguments.of(lexeme, "audio_url", "ftp://example.com/a", "INVALID_URL"),
                Arguments.of(lexeme, "audio_url", "https://example.com/a b", "INVALID_URL"),
                Arguments.of(lexeme, "audio_url", "https://example.com/a\u00A0b", "INVALID_URL"),
                Arguments.of(lexeme, "audio_url", "https://example.com/" + "a".repeat(500), "TOO_LONG"),
                Arguments.of(lexeme, "radical", "人人", "INVALID_VALUE"),
                Arguments.of(lexeme, "stroke_count", "1", "INVALID_VALUE"),
                Arguments.of(lexeme, "example_cn", "Latin only", "NOT_CJK"),
                Arguments.of(lexeme, "example_cn", "我?", "BROKEN_TEXT"),
                Arguments.of(lexeme, "example_cn", "我".repeat(301), "TOO_LONG"),
                Arguments.of(lexeme, "example_pinyin", "a".repeat(501), "TOO_LONG"),
                Arguments.of(lexeme, "example_vi", "a".repeat(501), "TOO_LONG"),
                Arguments.of(topic, "code", "a", "INVALID_VALUE"),
                Arguments.of(topic, "code", "lower_case", "INVALID_VALUE"),
                Arguments.of(topic, "code", "A B", "INVALID_VALUE"),
                Arguments.of(topic, "code", "A".repeat(51), "TOO_LONG"),
                Arguments.of(topic, "name", " ", "REQUIRED_MISSING"),
                Arguments.of(topic, "name", "a".repeat(201), "TOO_LONG"),
                Arguments.of(topic, "hsk_level", "10", "INVALID_HSK_LEVEL"),
                Arguments.of(topic, "order_index", "-1", "INVALID_VALUE"),
                Arguments.of(topic, "order_index", "100001", "INVALID_VALUE"),
                Arguments.of(topic, "order_index", "1.5", "INVALID_VALUE"),
                Arguments.of(item, "topic_code", "", "REQUIRED_MISSING"),
                Arguments.of(item, "topic_code", "MISSING", "REFERENCE_NOT_FOUND"),
                Arguments.of(item, "kind", "GRAMMAR", "INVALID_VALUE"),
                Arguments.of(item, "text", "", "REQUIRED_MISSING"),
                Arguments.of(item, "text", "不存在", "REFERENCE_NOT_FOUND"),
                Arguments.of(item, "text", "??", "BROKEN_TEXT"),
                Arguments.of(item, "order_index", "100001", "INVALID_VALUE"));
    }

    @Test
    void FR009_AT009_characterWithTwoHanziHasInvalidValue() {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("kind", "CHAR", "text", "你好")));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("text");
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
        });
    }

    @Test
    void FR009_AT011_multipleColumnErrorsKeepTemplateOrderAndPhysicalLine() {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 7, Map.of("meaning_vi", " ", "hsk_level", "10")));

        assertThat(result.errors()).extracting(RowError::column).containsExactly("meaning_vi", "hsk_level");
        assertThat(result.errors()).extracting(RowError::code).containsExactly("REQUIRED_MISSING", "INVALID_HSK_LEVEL");
        assertThat(result.errors()).extracting(RowError::line).containsOnly(7);
    }

    @ParameterizedTest(name = "FR009 stroke_count {0}")
    @ValueSource(strings = {"0", "65", "-1", "1.5", "999999999999"})
    void FR009_characterStrokeCountMustBeAnIntegerFromOneToSixtyFour(String count) {
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 2,
                Map.of("kind", "CHAR", "text", "我", "stroke_count", count)));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("stroke_count");
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
        });
    }

    @ParameterizedTest(name = "FR009 radical {0}")
    @ValueSource(strings = {"我", "\u3400", "\u2E80", "\u2FDF"})
    void FR009_characterRadicalSupportsHanziAndApprovedRadicalBlocks(String radical) {
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 2,
                Map.of("kind", "CHAR", "text", "我", "radical", radical, "stroke_count", "64")));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
    }

    @Test
    void FR009_characterRadicalOutsideApprovedBlocksIsInvalid() {
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 2,
                Map.of("kind", "CHAR", "text", "我", "radical", "😀")));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("radical");
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
        });
    }

    @Test
    void FR009_exampleChineseRequiresItsVietnameseTranslation() {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("example_vi", " \t ")));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("example_vi");
            assertThat(error.code()).isEqualTo("REQUIRED_MISSING");
        });
    }

    @ParameterizedTest(name = "FR009 accepted audio URL {0}")
    @ValueSource(strings = {"http://example.com/a.mp3", "https://example.com/a.mp3"})
    void FR009_httpAndHttpsAudioUrlsAreBothAccepted(String url) {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("audio_url", url)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("audio_url", url);
    }

    @Test
    void FR009_section41UpperLengthsAreAcceptedWithoutExtraPinyinRulesForExamples() {
        String urlPrefix = "https://example.com/";
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 2,
                Map.of("text", "我".repeat(20), "pinyin", "a".repeat(200), "sino_viet", "a".repeat(200),
                        "meaning_vi", "a".repeat(1000), "audio_url", urlPrefix + "a".repeat(500 - urlPrefix.length()),
                        "example_cn", "我".repeat(300), "example_pinyin", ".".repeat(500), "example_vi", "a".repeat(500))));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
    }

    @Test
    void FR030_FR031_FR032_normalizedRowsUseExistingHelpersWithoutMutatingInput() {
        CsvReader.Row input = row(ImportDataType.LEXEME, 3, Map.of("kind", " WORD ", "text", " 女儿 ",
                "pinyin", " Nu\u0308\u030c'e\u0301r ", "meaning_vi", "  Đi  học\n  cùng bạn  "));

        RowValidationResult result = validate(ImportDataType.LEXEME, input);

        assertThat(result.errors()).isEmpty();
        RowValidationResult.ValidatedRow valid = result.validRows().get(0);
        assertThat(valid.values()).containsEntry("kind", "WORD").containsEntry("text", "女儿")
                .containsEntry("pinyin", "Nǚ'ér").containsEntry("meaning_vi", "Đi  học\n  cùng bạn");
        assertThat(valid.pinyinNorm()).isEqualTo("nver");
        assertThat(valid.meaningNorm()).isEqualTo("di  hoc\n  cung ban");
        assertThat(input.values()).containsEntry("kind", " WORD ");
    }

    @ParameterizedTest(name = "FR009 HSK {0}")
    @ValueSource(strings = {"1", "3", "9"})
    void FR009_apiStillAcceptsHskOneThroughNine(String level) {
        for (ImportDataType type : List.of(ImportDataType.LEXEME, ImportDataType.TOPIC)) {
            RowValidationResult result = validate(type, row(type, 2, Map.of("hsk_level", level)));
            assertThat(result.errors()).isEmpty();
            assertThat(result.validRows()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "FR009 order_index {0}")
    @ValueSource(strings = {"", "  ", "0", "100000"})
    void FR009_optionalOrderIndexAcceptsBoundariesAndKeepsBlankForUpsert(String order) {
        for (ImportDataType type : List.of(ImportDataType.TOPIC, ImportDataType.TOPIC_ITEM)) {
            RowValidationResult result = validate(type, row(type, 2, Map.of("order_index", order)));
            assertThat(result.errors()).isEmpty();
            assertThat(result.validRows().get(0).values()).containsEntry("order_index", order.trim());
        }
    }

    @ParameterizedTest(name = "FR009 cleaned tags [{index}]")
    @MethodSource("tags")
    void FR009_QT0602_tagsDropEmptyPartsAndTrimEachLabel(String input, String expected) {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("tags", input)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("tags", expected);
    }

    private static Stream<Arguments> tags() {
        return Stream.of(Arguments.of("a||b", "a|b"), Arguments.of("a| |b", "a|b"),
                Arguments.of("|a", "a"), Arguments.of("a|", "a"), Arguments.of("", ""),
                Arguments.of("|", ""), Arguments.of("|||", ""), Arguments.of(" | \t | ", ""),
                Arguments.of("  a |\u00A0b  ", "a|b"), Arguments.of("a|a", "a|a"));
    }

    @Test
    void FR009_QT0602_tagLimitsApplyAfterRemovingEmptyParts() {
        String tenTags = IntStream.range(0, 10).mapToObj(index -> "nhãn" + index).collect(Collectors.joining("||"));
        RowValidationResult valid = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("tags", "|" + tenTags + "|")));
        assertThat(valid.errors()).isEmpty();
        assertThat(valid.validRows().get(0).values().get("tags").split("\\|")).hasSize(10);

        RowValidationResult invalid = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("tags", tenTags + "|thêm")));
        assertThat(invalid.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("tags");
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
        });
    }

    @Test
    void FR009_QT0602_eachTagHasAtMostThirtyUnicodeCharacters() {
        RowValidationResult valid = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("tags", "😀".repeat(30))));
        assertThat(valid.errors()).isEmpty();

        RowValidationResult invalid = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 2, Map.of("tags", "😀".repeat(31))));
        assertThat(invalid.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
    }

    @Test
    void FR010_QT0601_invalidLine3DoesNotReserveKeyBeforeValidLine7() {
        RowValidationResult result = validate(ImportDataType.LEXEME,
                row(ImportDataType.LEXEME, 3, Map.of("hsk_level", "10")), row(ImportDataType.LEXEME, 7, Map.of()));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(7);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(3);
            assertThat(error.code()).isEqualTo("INVALID_HSK_LEVEL");
        });
    }

    @ParameterizedTest(name = "FR010 first valid key for {0}")
    @EnumSource(value = ImportDataType.class, names = {"LEXEME", "TOPIC", "TOPIC_ITEM"})
    void FR010_QT0601_validLine7DuplicatesValidLine3WithPhysicalFirstLine(ImportDataType type) {
        RowValidationResult result = validate(type, row(type, 7, Map.of()), row(type, 3, Map.of()));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(3);
        assertThat(result.errors()).containsExactly(new RowError(7, null, null, "DUPLICATE_IN_FILE", "Trùng khoá với dòng 3"));
    }

    @Test
    void FR010_invalidLaterRowHasOnlyItsOwnErrorsDespiteAnEarlierValidKey() {
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 3, Map.of()),
                row(ImportDataType.LEXEME, 7, Map.of("hsk_level", "10")));

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(7);
            assertThat(error.code()).isEqualTo("INVALID_HSK_LEVEL");
        });
    }

    @Test
    void FR010_kindIsPartOfTheLexemeNaturalKeyAndStateDoesNotLeakAcrossFiles() {
        CsvReader.Row word = row(ImportDataType.LEXEME, 3, Map.of("text", "我"));
        CsvReader.Row character = row(ImportDataType.LEXEME, 7, Map.of("kind", "CHAR", "text", "我"));
        RowValidator validator = validator(ImportDataType.LEXEME);

        assertThat(validator.validate(input(word, character)).validRows()).hasSize(2);
        assertThat(validator.validate(input(word)).validRows()).hasSize(1);
        assertThat(validator.validate(input(word)).errors()).isEmpty();
    }

    @Test
    void FR009_existingStructuralErrorsAreMergedAndSortedWithNewFieldErrors() {
        RowError structural = new RowError(5, null, null, "INVALID_VALUE", "Dòng thiếu ô");
        CsvReader.ReadResult input = new CsvReader.ReadResult(2,
                List.of(row(ImportDataType.LEXEME, 3, Map.of("hsk_level", "10"))), List.of(structural));

        RowValidationResult result = validator(ImportDataType.LEXEME).validate(input);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).extracting(RowError::line).containsExactly(3, 5);
        assertThat(result.errors()).contains(structural);
    }

    @Test
    void FR009_errorValuesAreLimitedToOneHundredCodePointsWithoutSplittingSurrogates() {
        RowValidationResult result = validate(ImportDataType.TOPIC,
                row(ImportDataType.TOPIC, 2, Map.of("name", "😀".repeat(201))));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("TOO_LONG");
            assertThat(error.value()).isEqualTo("😀".repeat(100));
        });
    }

    @Test
    void FR011_AT014_missingReferencesHaveParentTemplateHintsAndOneErrorPerColumn() {
        RowValidationResult result = validate(ImportDataType.TOPIC_ITEM,
                row(ImportDataType.TOPIC_ITEM, 2, Map.of("topic_code", "MISSING", "text", "不存在")));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).extracting(RowError::column).containsExactly("topic_code", "text");
        assertThat(result.errors()).extracting(RowError::code).containsOnly("REFERENCE_NOT_FOUND");
        assertThat(result.errors().get(0).message()).contains("Chủ đề từ vựng trước");
        assertThat(result.errors().get(1).message()).contains("Chữ và từ vựng trước");
    }

    @Test
    void FR011_missingRequiredReferencePartsDoNotTriggerDatabaseQueries() {
        RowValidationResult result = validate(ImportDataType.TOPIC_ITEM,
                row(ImportDataType.TOPIC_ITEM, 2, Map.of("topic_code", "", "kind", "", "text", "")));

        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors()).extracting(RowError::code).containsOnly("REQUIRED_MISSING");
        verifyNoInteractions(topics, lexemes);
    }

    @Test
    void FR011_twoThousandRowsUseOneBatchPerReferenceKindAndNeverWrite() {
        when(topics.findByCodeIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            return codes.stream().map(code -> new Topic(code, "Chủ đề tự soạn", (short) 1)).toList();
        });
        List<CsvReader.Row> rows = new ArrayList<>();
        for (int index = 0; index < 2000; index++) {
            rows.add(row(ImportDataType.TOPIC_ITEM, index + 2, Map.of("topic_code", "TOPIC_" + index,
                    "kind", index % 2 == 0 ? "CHAR" : "WORD", "text", index % 2 == 0 ? "我" : "我们")));
        }

        RowValidationResult result = validator(ImportDataType.TOPIC_ITEM)
                .validate(new CsvReader.ReadResult(rows.size(), rows, List.of()));

        assertThat(result.validRows()).hasSize(2000);
        assertThat(result.errors()).isEmpty();
        verify(topics).findByCodeIn(org.mockito.ArgumentMatchers.argThat(codes -> codes.size() == 2000));
        verify(lexemes).findByKindAndTextIn(LexemeKind.CHAR, java.util.Set.of("我"));
        verify(lexemes).findByKindAndTextIn(LexemeKind.WORD, java.util.Set.of("我们"));
        verifyNoMoreInteractions(topics, lexemes);
    }

    @Test
    void FR009_validationResultsAreImmutable() {
        RowValidationResult result = validate(ImportDataType.LEXEME, row(ImportDataType.LEXEME, 2, Map.of()));

        assertThatThrownBy(() -> result.validRows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.validRows().get(0).values().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private RowValidator validator(ImportDataType type) {
        return switch (type) {
            case LEXEME -> new LexemeRowValidator();
            case TOPIC -> new TopicRowValidator();
            case TOPIC_ITEM -> new TopicItemRowValidator(topics, lexemes);
            default -> throw new IllegalArgumentException("Not part of T-06: " + type);
        };
    }

    private RowValidationResult validate(ImportDataType type, CsvReader.Row... rows) {
        return validator(type).validate(input(rows));
    }

    private static CsvReader.ReadResult input(CsvReader.Row... rows) {
        return new CsvReader.ReadResult(rows.length, List.of(rows), List.of());
    }

    private static CsvReader.Row row(ImportDataType type, int line, Map<String, String> replacements) {
        Map<String, String> values = new HashMap<>(switch (type) {
            case LEXEME -> Map.of("kind", "WORD", "text", "我们", "pinyin", "wǒmen", "meaning_vi", "chúng ta",
                    "hsk_level", "1", "example_cn", "我们学习。", "example_vi", "Chúng ta học.");
            case TOPIC -> Map.of("code", "DAILY_LIFE", "name", "Sinh hoạt", "hsk_level", "1");
            case TOPIC_ITEM -> Map.of("topic_code", "DAILY_LIFE", "kind", "WORD", "text", "我们");
            default -> throw new IllegalArgumentException("Not part of T-06: " + type);
        });
        values.putAll(replacements);
        return new CsvReader.Row(line, values);
    }
}
