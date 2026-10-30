package com.cnhsk.learning.content.importer.validate;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class GrammarPronRowValidatorsTest {

    private final LexemeRepository lexemes = mock(LexemeRepository.class);
    private final PronStageRepository stages = mock(PronStageRepository.class);

    @BeforeEach
    void existingParents() {
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.GRAMMAR), anyCollection()))
                .thenReturn(List.of(new Lexeme(LexemeKind.GRAMMAR, "是……的", "Nhấn mạnh thông tin")));
        when(stages.findByStageIndexIn(anyCollection())).thenReturn(List.of(stage(1, 0)));
    }

    @ParameterizedTest(name = "FR009 valid {0}")
    @EnumSource(value = ImportDataType.class, names = {"GRAMMAR", "GRAMMAR_EXERCISE", "PRON_STAGE", "PRON_UNIT"})
    void FR009_allFourTypesAcceptTheirValidFields(ImportDataType type) {
        RowValidationResult result = validate(type, row(type, 7, Map.of()));

        assertThat(result.errors()).isEmpty();
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.validRows()).singleElement().satisfies(valid -> assertThat(valid.line()).isEqualTo(7));
        assertThat(validator(type).dataType()).isEqualTo(type);
    }

    @ParameterizedTest(name = "FR009 [{index}] {0} {1} => {3}")
    @MethodSource("invalidCells")
    void FR009_sections44Through47RejectInvalidCellsWithExistingCodes(
            ImportDataType type, String column, String value, String expectedCode) {
        RowValidationResult result = validate(type, row(type, 3, Map.of(column, value)));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).filteredOn(error -> column.equals(error.column())).singleElement()
                .satisfies(error -> {
                    assertThat(error.line()).isEqualTo(3);
                    assertThat(error.code()).isEqualTo(expectedCode);
                    assertThat(error.message()).isNotBlank();
                });
        assertThat(result.errors()).extracting(RowError::column).doesNotHaveDuplicates();
    }

    private static Stream<Arguments> invalidCells() {
        ImportDataType grammar = ImportDataType.GRAMMAR;
        ImportDataType exercise = ImportDataType.GRAMMAR_EXERCISE;
        ImportDataType stage = ImportDataType.PRON_STAGE;
        ImportDataType unit = ImportDataType.PRON_UNIT;
        return Stream.of(
                Arguments.of(grammar, "text", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "text", "A + B", "NOT_CJK"),
                Arguments.of(grammar, "text", "我?", "BROKEN_TEXT"),
                Arguments.of(grammar, "text", "我".repeat(201), "TOO_LONG"),
                Arguments.of(grammar, "hsk_level", "10", "INVALID_HSK_LEVEL"),
                Arguments.of(grammar, "title_vi", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "title_vi", "a".repeat(301), "TOO_LONG"),
                Arguments.of(grammar, "structure", "a".repeat(301), "TOO_LONG"),
                Arguments.of(grammar, "explanation_vi", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "explanation_vi", "a".repeat(3001), "TOO_LONG"),
                Arguments.of(grammar, "common_mistake_vi", "a".repeat(1001), "TOO_LONG"),
                Arguments.of(grammar, "example1_cn", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "example2_cn", "Latin", "NOT_CJK"),
                Arguments.of(grammar, "example3_cn", "我".repeat(301), "TOO_LONG"),
                Arguments.of(grammar, "example2_cn", "我?", "BROKEN_TEXT"),
                Arguments.of(grammar, "example1_pinyin", "a".repeat(501), "TOO_LONG"),
                Arguments.of(grammar, "example2_pinyin", "a".repeat(501), "TOO_LONG"),
                Arguments.of(grammar, "example3_pinyin", "a".repeat(501), "TOO_LONG"),
                Arguments.of(grammar, "example1_vi", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "example2_vi", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "example3_vi", "", "REQUIRED_MISSING"),
                Arguments.of(grammar, "example3_vi", "a".repeat(501), "TOO_LONG"),
                Arguments.of(exercise, "grammar_text", "", "REQUIRED_MISSING"),
                Arguments.of(exercise, "grammar_text", "不存在", "REFERENCE_NOT_FOUND"),
                Arguments.of(exercise, "exercise_no", "0", "INVALID_VALUE"),
                Arguments.of(exercise, "exercise_no", "21", "INVALID_VALUE"),
                Arguments.of(exercise, "exercise_no", "1.5", "INVALID_VALUE"),
                Arguments.of(exercise, "stem", "", "REQUIRED_MISSING"),
                Arguments.of(exercise, "stem", "a".repeat(501), "TOO_LONG"),
                Arguments.of(exercise, "option_a", "", "REQUIRED_MISSING"),
                Arguments.of(exercise, "option_b", "a".repeat(201), "TOO_LONG"),
                Arguments.of(exercise, "option_c", "a".repeat(201), "TOO_LONG"),
                Arguments.of(exercise, "option_d", "a".repeat(201), "TOO_LONG"),
                Arguments.of(exercise, "answer", "D", "INVALID_ANSWER"),
                Arguments.of(exercise, "answer", "E", "INVALID_ANSWER"),
                Arguments.of(exercise, "answer", "a", "INVALID_ANSWER"),
                Arguments.of(exercise, "answer", "", "REQUIRED_MISSING"),
                Arguments.of(exercise, "explanation_vi", "a".repeat(1001), "TOO_LONG"),
                Arguments.of(stage, "stage_index", "0", "INVALID_VALUE"),
                Arguments.of(stage, "stage_index", "9", "INVALID_VALUE"),
                Arguments.of(stage, "stage_index", "1.5", "INVALID_VALUE"),
                Arguments.of(stage, "name", "", "REQUIRED_MISSING"),
                Arguments.of(stage, "name", "a".repeat(101), "TOO_LONG"),
                Arguments.of(stage, "description", "a".repeat(2001), "TOO_LONG"),
                Arguments.of(stage, "target_sounds", "a".repeat(21), "INVALID_VALUE"),
                Arguments.of(stage, "pass_percent", "-0.01", "INVALID_VALUE"),
                Arguments.of(stage, "pass_percent", "100.01", "INVALID_VALUE"),
                Arguments.of(stage, "pass_percent", "80.255", "INVALID_VALUE"),
                Arguments.of(stage, "pass_percent", "NaN", "INVALID_VALUE"),
                Arguments.of(stage, "pass_percent", "Infinity", "INVALID_VALUE"),
                Arguments.of(unit, "stage_index", "9", "REFERENCE_NOT_FOUND"),
                Arguments.of(unit, "stage_index", "1.5", "INVALID_VALUE"),
                Arguments.of(unit, "stage_index", "40000", "INVALID_VALUE"),
                Arguments.of(unit, "stage_index", "", "REQUIRED_MISSING"),
                Arguments.of(unit, "unit_code", "", "REQUIRED_MISSING"),
                Arguments.of(unit, "unit_code", "a b", "INVALID_VALUE"),
                Arguments.of(unit, "unit_code", "a".repeat(31), "TOO_LONG"),
                Arguments.of(unit, "audio_url", "", "REQUIRED_MISSING"),
                Arguments.of(unit, "audio_url", "ftp://example.com/a", "INVALID_URL"),
                Arguments.of(unit, "prompt_vi", "a".repeat(201), "TOO_LONG"),
                Arguments.of(unit, "option_a", "", "REQUIRED_MISSING"),
                Arguments.of(unit, "option_b", "a".repeat(51), "TOO_LONG"),
                Arguments.of(unit, "option_c", "a".repeat(51), "TOO_LONG"),
                Arguments.of(unit, "option_d", "a".repeat(51), "TOO_LONG"),
                Arguments.of(unit, "answer", "D", "INVALID_ANSWER"),
                Arguments.of(unit, "answer", "Z", "INVALID_ANSWER"),
                Arguments.of(unit, "explanation_vi", "a".repeat(501), "TOO_LONG"));
    }

    @Test
    void FR030_FR032_grammarKeepsLongVietnameseTextAndDerivesMeaningNormFromTitle() {
        RowValidationResult result = validate(ImportDataType.GRAMMAR, row(ImportDataType.GRAMMAR, 2,
                Map.of("text", " 把   + O + V ", "title_vi", "  Đi  học  ", "explanation_vi", "  Dòng  một\n  Dòng hai  ")));

        assertThat(result.errors()).isEmpty();
        RowValidationResult.ValidatedRow valid = result.validRows().get(0);
        assertThat(valid.values()).containsEntry("text", "把 + O + V")
                .containsEntry("title_vi", "Đi  học").containsEntry("explanation_vi", "Dòng  một\n  Dòng hai");
        assertThat(valid.meaningNorm()).isEqualTo("di  hoc");
        assertThat(valid.pinyinNorm()).isNull();
    }

    @Test
    void FR009_grammarOptionalExamplesCanBeAbsentAndPinyinMayContainPunctuation() {
        Map<String, String> values = defaults(ImportDataType.GRAMMAR);
        values.keySet().removeIf(name -> name.startsWith("example2_") || name.startsWith("example3_"));
        values.put("example1_pinyin", "Wǒ shì zuótiān lái de.");

        RowValidationResult result = validate(ImportDataType.GRAMMAR, new CsvReader.Row(2, values));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
    }

    @Test
    void FR009_grammarExerciseOptionDRequiresOptionC() {
        RowValidationResult result = validate(ImportDataType.GRAMMAR_EXERCISE,
                row(ImportDataType.GRAMMAR_EXERCISE, 2, Map.of("option_d", "没有", "answer", "D")));

        assertThat(result.errors()).containsExactly(new RowError(2, "option_c", null, "REQUIRED_MISSING",
                "Thiếu giá trị bắt buộc ở cột option_c"));
    }

    @ParameterizedTest(name = "FR009 AT039 duplicate options for {0}")
    @EnumSource(value = ImportDataType.class, names = {"GRAMMAR_EXERCISE", "PRON_UNIT"})
    void FR009_AT039_normalizedDuplicateOptionsAreInvalidAnswer(ImportDataType type) {
        RowValidationResult result = validate(type, row(type, 2,
                Map.of("option_a", " Nu\u0308\u030c ", "option_b", "Nǚ")));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("answer");
            assertThat(error.code()).isEqualTo("INVALID_ANSWER");
        });
    }

    @Test
    void FR009_pronUnitOnlyRequiresAAndBAndAnswerCanPointToAnExistingD() {
        RowValidationResult result = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 2, Map.of("option_d", "f", "answer", "D")));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
    }

    @ParameterizedTest(name = "FR009 cleaned sounds [{index}]")
    @MethodSource("sounds")
    void FR009_QT0701_targetSoundsDropEmptyPartsBeforeLimits(String input, String expected) {
        RowValidationResult result = validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("target_sounds", input)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("target_sounds", expected);
    }

    private static Stream<Arguments> sounds() {
        return Stream.of(Arguments.of("b||p", "b|p"), Arguments.of("b| |p", "b|p"),
                Arguments.of("|b", "b"), Arguments.of("b|", "b"), Arguments.of("", ""),
                Arguments.of("|", ""), Arguments.of(" | \t | ", ""), Arguments.of(" b |\u00A0p ", "b|p"));
    }

    @Test
    void FR009_QT0701_targetSoundLimitsAreThirtyItemsOfTwentyUnicodeCharactersAfterCleanup() {
        String thirty = IntStream.range(0, 30).mapToObj(index -> "sound" + index).collect(Collectors.joining("||"));
        assertThat(validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("target_sounds", "|" + thirty + "|"))).errors()).isEmpty();
        assertThat(validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("target_sounds", "😀".repeat(20)))).errors()).isEmpty();

        RowValidationResult tooMany = validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("target_sounds", thirty + "|extra")));
        assertThat(tooMany.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
        RowValidationResult tooLong = validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("target_sounds", "😀".repeat(21))));
        assertThat(tooLong.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
    }

    @ParameterizedTest(name = "FR009 pass percent {0}")
    @CsvSource({"0,0.00", "100,100.00", "80,80.00", "80.25,80.25"})
    void FR009_passPercentUsesExactDecimalValuesWithinBounds(String input, String expected) {
        RowValidationResult result = validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("pass_percent", input)));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows().get(0).values()).containsEntry("pass_percent", expected);
    }

    @Test
    void FR009_optionalBlanksStayBlankAndDefaultsAreDeferredToWriting() {
        RowValidationResult stage = validate(ImportDataType.PRON_STAGE,
                row(ImportDataType.PRON_STAGE, 2, Map.of("pass_percent", " ", "target_sounds", "||")));
        assertThat(stage.errors()).isEmpty();
        assertThat(stage.validRows().get(0).values()).containsEntry("pass_percent", "").containsEntry("target_sounds", "");
        RowValidationResult unit = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 2, Map.of("prompt_vi", " ")));
        assertThat(unit.errors()).isEmpty();
        assertThat(unit.validRows().get(0).values()).containsEntry("prompt_vi", "");
    }

    @ParameterizedTest(name = "FR010 numeric key {0}")
    @EnumSource(value = ImportDataType.class, names = {"GRAMMAR_EXERCISE", "PRON_STAGE", "PRON_UNIT"})
    void FR010_numericNaturalKeysUseNumericIdentityAndFirstValidPhysicalLine(ImportDataType type) {
        String key = type == ImportDataType.GRAMMAR_EXERCISE ? "exercise_no" : "stage_index";
        RowValidationResult result = validate(type, row(type, 3, Map.of(key, "01")), row(type, 7, Map.of(key, "1")));

        assertThat(result.validRows()).singleElement().satisfies(valid -> assertThat(valid.line()).isEqualTo(3));
        assertThat(result.errors()).containsExactly(new RowError(7, null, null, "DUPLICATE_IN_FILE", "Trùng khoá với dòng 3"));
    }

    @Test
    void FR009_pronUnitCapacityIncludesExistingUnitsButDoesNotCountUpdatesOrDuplicates() {
        PronStage existing = stage(1, 99);
        List<PronUnit> originalUnits = existing.getUnits();
        when(stages.findByStageIndexIn(anyCollection())).thenReturn(List.of(existing));

        RowValidationResult result = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 3, Map.of("unit_code", "NEW")),
                row(ImportDataType.PRON_UNIT, 7, Map.of("unit_code", "NEW")),
                row(ImportDataType.PRON_UNIT, 9, Map.of("unit_code", "OVER")),
                row(ImportDataType.PRON_UNIT, 11, Map.of("unit_code", "OLD_0", "answer", "B")),
                row(ImportDataType.PRON_UNIT, 13, Map.of("unit_code", "NEW", "answer", "Z")));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(3, 11);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(7, 9, 13);
        assertThat(result.errors()).extracting(RowError::code)
                .containsExactly("DUPLICATE_IN_FILE", "INVALID_VALUE", "INVALID_ANSWER");
        assertThat(existing.getUnits()).isSameAs(originalUnits).hasSize(99);
    }

    @Test
    void FR009_pronUnitAtCapacityAllowsOnlyExistingCodes() {
        when(stages.findByStageIndexIn(anyCollection())).thenReturn(List.of(stage(1, 100)));

        RowValidationResult result = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 2, Map.of("unit_code", "NEW")),
                row(ImportDataType.PRON_UNIT, 3, Map.of("unit_code", "OLD_0")));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(3);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.line()).isEqualTo(2);
            assertThat(error.column()).isEqualTo("unit_code");
            assertThat(error.code()).isEqualTo("INVALID_VALUE");
        });
    }

    @Test
    void FR010_invalidPronUnitDoesNotReserveKeyOrConsumeCapacity() {
        when(stages.findByStageIndexIn(anyCollection())).thenReturn(List.of(stage(1, 99)));

        RowValidationResult result = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 3, Map.of("unit_code", "NEW", "answer", "Z")),
                row(ImportDataType.PRON_UNIT, 7, Map.of("unit_code", "NEW")),
                row(ImportDataType.PRON_UNIT, 9, Map.of("unit_code", "NEW")));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(7);
        assertThat(result.errors()).extracting(RowError::code).containsExactly("INVALID_ANSWER", "DUPLICATE_IN_FILE");
        assertThat(result.errors().get(1).message()).isEqualTo("Trùng khoá với dòng 7");
    }

    @Test
    void FR011_missingOrMalformedParentKeysSkipDatabaseQueries() {
        RowValidationResult exercise = validate(ImportDataType.GRAMMAR_EXERCISE,
                row(ImportDataType.GRAMMAR_EXERCISE, 2, Map.of("grammar_text", "")));
        RowValidationResult unit = validate(ImportDataType.PRON_UNIT,
                row(ImportDataType.PRON_UNIT, 2, Map.of("stage_index", "bad")));

        assertThat(exercise.errors()).extracting(RowError::code).containsOnly("REQUIRED_MISSING");
        assertThat(unit.errors()).extracting(RowError::code).containsOnly("INVALID_VALUE");
        verifyNoInteractions(lexemes, stages);
    }

    @Test
    void FR011_grammarParentsAreLookedUpOncePerFileWithoutWrites() {
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.GRAMMAR), anyCollection())).thenAnswer(invocation -> {
            Collection<String> texts = invocation.getArgument(1);
            return texts.stream().map(text -> new Lexeme(LexemeKind.GRAMMAR, text, "Điểm tự soạn")).toList();
        });
        List<CsvReader.Row> rows = IntStream.range(0, 2000).mapToObj(index -> row(ImportDataType.GRAMMAR_EXERCISE,
                index + 2, Map.of("grammar_text", "点" + index / 20, "exercise_no", Integer.toString(index % 20 + 1)))).toList();

        RowValidationResult result = validator(ImportDataType.GRAMMAR_EXERCISE)
                .validate(new CsvReader.ReadResult(rows.size(), rows, List.of()));

        assertThat(result.validRows()).hasSize(2000);
        assertThat(result.errors()).isEmpty();
        verify(lexemes).findByKindAndTextIn(eq(LexemeKind.GRAMMAR),
                org.mockito.ArgumentMatchers.argThat(texts -> texts.size() == 100));
        verifyNoMoreInteractions(lexemes);
    }

    @Test
    void FR011_pronStageReferencesAreLoadedInOneBatchAndCapacityIsPerStage() {
        when(stages.findByStageIndexIn(anyCollection())).thenReturn(List.of(stage(1, 0), stage(2, 0), stage(3, 0)));
        List<CsvReader.Row> rows = new ArrayList<>();
        for (int index = 0; index < 250; index++) {
            rows.add(row(ImportDataType.PRON_UNIT, index + 2,
                    Map.of("stage_index", Integer.toString(index % 3 + 1), "unit_code", "UNIT_" + index)));
        }

        RowValidationResult result = validator(ImportDataType.PRON_UNIT)
                .validate(new CsvReader.ReadResult(rows.size(), rows, List.of()));

        assertThat(result.validRows()).hasSize(250);
        assertThat(result.errors()).isEmpty();
        verify(stages).findByStageIndexIn(Set.of((short) 1, (short) 2, (short) 3));
        verifyNoMoreInteractions(stages);
    }

    private RowValidator validator(ImportDataType type) {
        return switch (type) {
            case GRAMMAR -> new GrammarRowValidator();
            case GRAMMAR_EXERCISE -> new GrammarExerciseRowValidator(lexemes);
            case PRON_STAGE -> new PronStageRowValidator();
            case PRON_UNIT -> new PronUnitRowValidator(stages);
            default -> throw new IllegalArgumentException("Not part of T-07: " + type);
        };
    }

    private RowValidationResult validate(ImportDataType type, CsvReader.Row... rows) {
        return validator(type).validate(new CsvReader.ReadResult(rows.length, List.of(rows), List.of()));
    }

    private static CsvReader.Row row(ImportDataType type, int line, Map<String, String> replacements) {
        Map<String, String> values = defaults(type);
        values.putAll(replacements);
        return new CsvReader.Row(line, values);
    }

    private static Map<String, String> defaults(ImportDataType type) {
        return new HashMap<>(switch (type) {
            case GRAMMAR -> Map.of("text", "是……的", "hsk_level", "2", "title_vi", "Nhấn mạnh thông tin",
                    "explanation_vi", "Giải thích tự soạn", "example1_cn", "我是昨天来的。", "example1_vi", "Tôi đến hôm qua.",
                    "example2_cn", "他是坐车来的。", "example2_vi", "Anh ấy đến bằng xe.",
                    "example3_cn", "她是在家学的。", "example3_vi", "Cô ấy đã học ở nhà.");
            case GRAMMAR_EXERCISE -> Map.of("grammar_text", "是……的", "exercise_no", "1", "stem", "我___昨天来的。",
                    "option_a", "是", "option_b", "有", "answer", "A");
            case PRON_STAGE -> Map.of("stage_index", "1", "name", "Thanh mẫu");
            case PRON_UNIT -> Map.of("stage_index", "1", "unit_code", "UNIT_1", "audio_url", "https://example.com/a.mp3",
                    "option_a", "b", "option_b", "p", "answer", "A");
            default -> throw new IllegalArgumentException("Not part of T-07: " + type);
        });
    }

    private static PronStage stage(int index, int unitCount) {
        PronStage stage = new PronStage((short) index, "Tầng tự soạn");
        stage.setUnits(IntStream.range(0, unitCount).mapToObj(number -> new PronUnit("OLD_" + number,
                "https://example.com/a.mp3", "Nghe và chọn", List.of(new PronUnit.Option("A", "b"),
                        new PronUnit.Option("B", "p")), "A", "Giải thích tự soạn")).toList());
        return stage;
    }
}
