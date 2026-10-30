package com.cnhsk.learning.content.importer.write;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RowWritersTest {
    private LexemeRepository lexemes;
    private TopicRepository topics;
    private TopicItemRepository items;
    private PronStageRepository stages;
    private Map<ImportDataType, RowWriter> writers;
    private Lexeme grammar;
    private PronStage stage;

    @BeforeEach
    void setup() {
        lexemes = mock(LexemeRepository.class);
        topics = mock(TopicRepository.class);
        items = mock(TopicItemRepository.class);
        stages = mock(PronStageRepository.class);
        writers = Map.of(ImportDataType.LEXEME, new LexemeRowWriter(lexemes),
                ImportDataType.TOPIC, new TopicRowWriter(topics),
                ImportDataType.TOPIC_ITEM, new TopicItemRowWriter(topics, lexemes, items),
                ImportDataType.GRAMMAR, new GrammarRowWriter(lexemes),
                ImportDataType.GRAMMAR_EXERCISE, new GrammarExerciseRowWriter(lexemes),
                ImportDataType.PRON_STAGE, new PronStageRowWriter(stages),
                ImportDataType.PRON_UNIT, new PronUnitRowWriter(stages));
        grammar = new Lexeme(LexemeKind.GRAMMAR, "是……的", "Tên ngữ pháp");
        stage = new PronStage((short) 1, "Tên tầng");
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR020_everyTemplateCreatesUpdatesAndReimportsWithoutAnotherSave(ImportDataType type) {
        parents(type);
        Map<String, String> values = example(type);
        RowWriter writer = writers.get(type);
        assertThat(writer.dataType()).isEqualTo(type);
        assertThat(writer.write(row(values))).isEqualTo(RowAction.CREATE);
        Object stored = lastSaved(type);
        bindExisting(type, stored);
        clearSaves();
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UNCHANGED);
        assertNoSaves();
        values.put(changedColumn(type), changedValue(type));
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UPDATE);
        assertThat(lastSaved(type)).isSameAs(stored);
        clearSaves();
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UNCHANGED);
        assertNoSaves();
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR020_blankOptionalCellsKeepPreviouslyStoredValues(ImportDataType type) {
        optionalCells(type, false);
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR020_omittedOptionalColumnsKeepPreviouslyStoredValues(ImportDataType type) {
        optionalCells(type, true);
    }

    private void optionalCells(ImportDataType type, boolean omit) {
        parents(type);
        Map<String, String> values = example(type);
        assertThat(writers.get(type).write(row(values))).isEqualTo(RowAction.CREATE);
        bindExisting(type, lastSaved(type));
        ImportTemplate.forType(type).columns().stream().filter(c -> !c.required()).forEach(c -> {
            if (omit) values.remove(c.name());
            else values.put(c.name(), "");
        });
        clearSaves();
        assertThat(writers.get(type).write(row(values))).isEqualTo(RowAction.UNCHANGED);
        assertNoSaves();
        values.put(changedColumn(type), changedValue(type));
        assertThat(writers.get(type).write(row(values))).isEqualTo(RowAction.UPDATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sino_viet", "audio_url", "radical", "stroke_count", "example_cn", "example_pinyin", "example_vi", "tags"})
    void FR020_eachOptionalLexemeFieldCanChangeIndependently(String column) {
        Map<String, String> values = example(ImportDataType.LEXEME);
        values.put("kind", "CHAR");
        values.put("text", "我");
        RowWriter writer = writers.get(ImportDataType.LEXEME);
        writer.write(row(values));
        Lexeme stored = (Lexeme) lastSaved(ImportDataType.LEXEME);
        bindExisting(ImportDataType.LEXEME, stored);
        values.put(column, switch (column) {
            case "radical" -> "手";
            case "stroke_count" -> "7";
            case "audio_url" -> "https://example.com/changed.mp3";
            default -> "Đã sửa";
        });
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UPDATE);
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UNCHANGED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"structure", "explanation_vi", "common_mistake_vi", "example1_pinyin", "example2_cn", "example3_vi"})
    void FR020_eachNestedGrammarFieldCanChangeWithoutLosingExercises(String column) {
        RowWriter writer = writers.get(ImportDataType.GRAMMAR);
        Map<String, String> values = example(ImportDataType.GRAMMAR);
        writer.write(row(values));
        Lexeme stored = (Lexeme) lastSaved(ImportDataType.GRAMMAR);
        LexemeExtra old = stored.getExtra();
        List<LexemeExtra.Exercise> exercises = List.of(new LexemeExtra.Exercise(4, "Đề", List.of(), "A", "Giải thích"));
        stored.setExtra(new LexemeExtra(old.examples(), List.of("nhãn cũ"), old.structure(), old.explanation(), old.commonMistake(), exercises));
        bindExisting(ImportDataType.GRAMMAR, stored);
        values.put(column, "Nội dung mới");
        assertThat(writer.write(row(values))).isEqualTo(RowAction.UPDATE);
        assertThat(stored.getExtra().exercises()).isEqualTo(exercises);
        assertThat(stored.getExtra().tags()).containsExactly("nhãn cũ");
    }

    @Test
    void FR020_AT24_wordUpdatePreservesExtraFieldsAndExamplesAfterIndexZero() {
        Map<String, String> values = example(ImportDataType.LEXEME);
        RowWriter writer = writers.get(ImportDataType.LEXEME);
        writer.write(row(values));
        Lexeme stored = (Lexeme) lastSaved(ImportDataType.LEXEME);
        LexemeExtra old = stored.getExtra();
        LexemeExtra.Example tail = new LexemeExtra.Example("你好", "nǐ hǎo", "xin chào");
        stored.setExtra(new LexemeExtra(List.of(old.examples().get(0), tail), old.tags(), "cấu trúc cũ", "giải thích cũ", "lỗi cũ", List.of()));
        bindExisting(ImportDataType.LEXEME, stored);
        values.put("meaning_vi", "nghĩa mới");
        values.put("audio_url", "");
        writer.write(row(values));
        assertThat(stored.getAudioUrl()).isEqualTo(example(ImportDataType.LEXEME).get("audio_url"));
        assertThat(stored.getExtra().examples()).hasSize(2).last().isEqualTo(tail);
        assertThat(stored.getExtra().structure()).isEqualTo("cấu trúc cũ");
        assertThat(stored.getExtra().explanation()).isEqualTo("giải thích cũ");
        assertThat(stored.getExtra().commonMistake()).isEqualTo("lỗi cũ");
        assertThat(stored.getExtra().exercises()).isEmpty();
    }

    @Test
    void FR020_grammarExamplePositionsDoNotShiftWhenExampleTwoIsMissing() {
        Map<String, String> values = example(ImportDataType.GRAMMAR);
        values.keySet().removeIf(k -> k.startsWith("example2"));
        writers.get(ImportDataType.GRAMMAR).write(row(values));
        Lexeme stored = (Lexeme) lastSaved(ImportDataType.GRAMMAR);
        assertThat(stored.getExtra().examples()).hasSize(3);
        assertThat(stored.getExtra().examples().get(1)).isEqualTo(new LexemeExtra.Example(null, null, null));
        assertThat(stored.getExtra().examples().get(2).cn()).isEqualTo(values.get("example3_cn"));
    }

    @Test
    void FR020_numericRepresentationsAndDecimalScaleDoNotCauseUpdates() {
        Map<String, String> topic = example(ImportDataType.TOPIC);
        writers.get(ImportDataType.TOPIC).write(row(topic));
        bindExisting(ImportDataType.TOPIC, lastSaved(ImportDataType.TOPIC));
        topic.put("hsk_level", "01");
        topic.put("order_index", "000");
        assertThat(writers.get(ImportDataType.TOPIC).write(row(topic))).isEqualTo(RowAction.UNCHANGED);
        Map<String, String> sounds = example(ImportDataType.PRON_STAGE);
        writers.get(ImportDataType.PRON_STAGE).write(row(sounds));
        bindExisting(ImportDataType.PRON_STAGE, lastSaved(ImportDataType.PRON_STAGE));
        sounds.put("pass_percent", "80.00");
        assertThat(writers.get(ImportDataType.PRON_STAGE).write(row(sounds))).isEqualTo(RowAction.UNCHANGED);
        sounds.put("pass_percent", "0");
        assertThat(writers.get(ImportDataType.PRON_STAGE).write(row(sounds))).isEqualTo(RowAction.UPDATE);
    }

    @Test
    void FR020_AT38_exercisesSortByNumberAndPreserveUnrelatedGrammarFields() {
        parents(ImportDataType.GRAMMAR_EXERCISE);
        List<LexemeExtra.Example> examples = List.of(new LexemeExtra.Example("我是学生", null, "Tôi là học sinh"));
        grammar.setExtra(new LexemeExtra(examples, List.of("giữ"), "cấu trúc", "giải thích", "lỗi", null));
        Map<String, String> values = example(ImportDataType.GRAMMAR_EXERCISE);
        for (String no : List.of("3", "1", "2")) {
            values.put("exercise_no", no);
            assertThat(writers.get(ImportDataType.GRAMMAR_EXERCISE).write(row(values))).isEqualTo(RowAction.CREATE);
        }
        assertThat(grammar.getExtra().exercises()).extracting(LexemeExtra.Exercise::no).containsExactly(1, 2, 3);
        values.put("exercise_no", "1");
        values.put("stem", "Đề mới");
        values.put("option_c", ""); values.remove("option_d"); values.remove("explanation_vi");
        assertThat(writers.get(ImportDataType.GRAMMAR_EXERCISE).write(row(values))).isEqualTo(RowAction.UPDATE);
        LexemeExtra.Exercise updated = grammar.getExtra().exercises().get(0);
        assertThat(updated.options()).extracting(LexemeExtra.Option::key).containsExactly("A", "B", "C", "D");
        assertThat(updated.explanation()).isEqualTo(example(ImportDataType.GRAMMAR_EXERCISE).get("explanation_vi"));
        assertThat(grammar.getExtra().examples()).isEqualTo(examples);
        assertThat(grammar.getExtra().structure()).isEqualTo("cấu trúc");
        assertThat(grammar.getExtra().explanation()).isEqualTo("giải thích");
        assertThat(grammar.getExtra().commonMistake()).isEqualTo("lỗi");
        assertThat(grammar.getExtra().tags()).containsExactly("giữ");
    }

    @Test
    void FR020_AT40_unitsAppendAndUpdatesKeepPositionAndStageMetadata() {
        parents(ImportDataType.PRON_UNIT);
        stage.setDescription("mô tả cũ");
        stage.setTargetSounds(List.of("b", "p"));
        stage.setPassPercent(new BigDecimal("85.50"));
        Map<String, String> values = example(ImportDataType.PRON_UNIT);
        for (String code : List.of("Z", "A", "M")) {
            values.put("unit_code", code);
            assertThat(writers.get(ImportDataType.PRON_UNIT).write(row(values))).isEqualTo(RowAction.CREATE);
        }
        values.put("unit_code", "A"); values.put("answer", "B");
        values.put("prompt_vi", ""); values.put("option_c", ""); values.put("option_d", ""); values.put("explanation_vi", "");
        assertThat(writers.get(ImportDataType.PRON_UNIT).write(row(values))).isEqualTo(RowAction.UPDATE);
        assertThat(stage.getUnits()).extracting(PronUnit::code).containsExactly("Z", "A", "M");
        assertThat(stage.getUnits().get(1).answerKey()).isEqualTo("B");
        assertThat(stage.getUnits().get(1).options()).hasSize(4);
        assertThat(stage.getUnits().get(1).prompt()).isEqualTo(example(ImportDataType.PRON_UNIT).get("prompt_vi"));
        assertThat(stage.getUnits().get(1).explanation()).isEqualTo(example(ImportDataType.PRON_UNIT).get("explanation_vi"));
        assertThat(stage.getName()).isEqualTo("Tên tầng");
        assertThat(stage.getDescription()).isEqualTo("mô tả cũ");
        assertThat(stage.getTargetSounds()).containsExactly("b", "p");
        assertThat(stage.getPassPercent()).isEqualByComparingTo("85.50");
    }

    @Test
    void FR020_unitCapacityIsRecheckedAndUpdatingAFullStageStillWorks() {
        parents(ImportDataType.PRON_UNIT);
        stage.setUnits(java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new PronUnit("U" + i, "https://example.com/old.mp3", "Cũ", List.of(), "A", null)).toList());
        List<PronUnit> old = stage.getUnits();
        Map<String, String> values = example(ImportDataType.PRON_UNIT);
        assertThatThrownBy(() -> writers.get(ImportDataType.PRON_UNIT).write(row(values))).isInstanceOf(IllegalStateException.class);
        assertThat(stage.getUnits()).isEqualTo(old);
        verify(stages, never()).saveAndFlush(any());
        values.put("unit_code", "U50");
        assertThat(writers.get(ImportDataType.PRON_UNIT).write(row(values))).isEqualTo(RowAction.UPDATE);
        assertThat(stage.getUnits()).hasSize(100);
    }

    @Test
    void FR020_newRowsApplyDefaultsAndLeaveMissingOptionalValuesUnset() {
        Map<String, String> values = example(ImportDataType.LEXEME);
        keepRequired(ImportDataType.LEXEME, values);
        writers.get(ImportDataType.LEXEME).write(row(values));
        Lexeme word = (Lexeme) lastSaved(ImportDataType.LEXEME);
        assertThat(word.getAudioUrl()).isNull();
        assertThat(word.getSinoViet()).isNull();
        assertThat(word.getExtra()).isEqualTo(LexemeExtra.empty());
        values = example(ImportDataType.PRON_STAGE);
        keepRequired(ImportDataType.PRON_STAGE, values);
        writers.get(ImportDataType.PRON_STAGE).write(row(values));
        PronStage created = (PronStage) lastSaved(ImportDataType.PRON_STAGE);
        assertThat(created.getPassPercent()).isEqualByComparingTo("80");
        assertThat(created.getTargetSounds()).isEmpty();
        assertThat(created.getUnits()).isEmpty();
        parents(ImportDataType.PRON_UNIT);
        values = example(ImportDataType.PRON_UNIT);
        keepRequired(ImportDataType.PRON_UNIT, values);
        writers.get(ImportDataType.PRON_UNIT).write(row(values));
        assertThat(stage.getUnits().get(0).prompt()).isEqualTo("Nghe và chọn đáp án đúng");
        assertThat(stage.getUnits().get(0).options()).hasSize(2);
        assertThat(stage.getUnits().get(0).explanation()).isNull();
    }

    @Test
    void FR020_missingReferencesAndStorageErrorsPropagateToCallerWithoutInventingParents() {
        for (ImportDataType type : List.of(ImportDataType.TOPIC_ITEM, ImportDataType.GRAMMAR_EXERCISE, ImportDataType.PRON_UNIT)) {
            assertThatThrownBy(() -> writers.get(type).write(row(example(type)))).isInstanceOf(IllegalStateException.class);
        }
        Topic topic = new Topic("DAILY_LIFE", "Chủ đề", (short) 1);
        when(topics.findByCode(anyString())).thenReturn(Optional.of(topic));
        assertThatThrownBy(() -> writers.get(ImportDataType.TOPIC_ITEM).write(row(example(ImportDataType.TOPIC_ITEM))))
                .isInstanceOf(IllegalStateException.class);
        assertNoSaves();
        doThrow(new DataIntegrityViolationException("simulated constraint failure")).when(lexemes).saveAndFlush(any());
        assertThatThrownBy(() -> writers.get(ImportDataType.LEXEME).write(row(example(ImportDataType.LEXEME))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"GRAMMAR_EXERCISE", "PRON_UNIT"})
    void FR020_mergedOptionsCannotDuplicateAnOptionalValueKeptFromTheDatabase(ImportDataType type) {
        parents(type);
        Map<String, String> values = example(type);
        writers.get(type).write(row(values));
        Object previous = type == ImportDataType.GRAMMAR_EXERCISE ? grammar.getExtra() : stage.getUnits();
        clearSaves();
        values.put("option_a", "  " + values.get("option_c") + "  ");
        values.put("option_c", "");
        values.put("option_d", "");
        values.put("answer", "A");

        assertThatThrownBy(() -> writers.get(type).write(row(values)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Đáp án phải là A–D, trỏ tới phương án có nội dung; các phương án không được trùng");
        assertThat(type == ImportDataType.GRAMMAR_EXERCISE ? grammar.getExtra() : stage.getUnits()).isEqualTo(previous);
        assertNoSaves();
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"GRAMMAR_EXERCISE", "PRON_UNIT"})
    void FR020_writerRejectsAnAnswerPointingToAnEmptyMergedOption(ImportDataType type) {
        parents(type);
        Map<String, String> values = example(type);
        values.put("option_c", "");
        values.put("option_d", "");
        values.put("answer", "C");
        assertThatThrownBy(() -> writers.get(type).write(row(values)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Đáp án phải là A–D, trỏ tới phương án có nội dung; các phương án không được trùng");
        assertNoSaves();
        assertThat(grammar.getExtra().exercises()).isNull();
        assertThat(stage.getUnits()).isEmpty();
    }

    private void parents(ImportDataType type) {
        if (type == ImportDataType.TOPIC_ITEM) {
            Topic topic = new Topic("DAILY_LIFE", "Chủ đề", (short) 1);
            Lexeme word = new Lexeme(LexemeKind.WORD, "我们", "chúng ta");
            ReflectionTestUtils.setField(topic, "id", 11L);
            ReflectionTestUtils.setField(word, "id", 22L);
            when(topics.findByCode("DAILY_LIFE")).thenReturn(Optional.of(topic));
            when(lexemes.findByKindAndText(LexemeKind.WORD, "我们")).thenReturn(Optional.of(word));
        } else if (type == ImportDataType.GRAMMAR_EXERCISE) {
            when(lexemes.findByKindAndText(LexemeKind.GRAMMAR, "是……的")).thenReturn(Optional.of(grammar));
        } else if (type == ImportDataType.PRON_UNIT) {
            when(stages.findByStageIndex((short) 1)).thenReturn(Optional.of(stage));
        }
    }

    private void bindExisting(ImportDataType type, Object stored) {
        switch (type) {
            case LEXEME, GRAMMAR -> {
                Lexeme lexeme = (Lexeme) stored;
                when(lexemes.findByKindAndText(lexeme.getKind(), lexeme.getText())).thenReturn(Optional.of(lexeme));
            }
            case TOPIC -> when(topics.findByCode(anyString())).thenReturn(Optional.of((Topic) stored));
            case TOPIC_ITEM -> when(items.findByTopicIdAndLexemeId(anyLong(), anyLong())).thenReturn(Optional.of((TopicItem) stored));
            case PRON_STAGE -> when(stages.findByStageIndex(anyShort())).thenReturn(Optional.of((PronStage) stored));
            case GRAMMAR_EXERCISE, PRON_UNIT -> { /* The existing parent holds the newly written JSON record. */ }
        }
    }

    private Object lastSaved(ImportDataType type) {
        Object repository = switch (type) {
            case LEXEME, GRAMMAR, GRAMMAR_EXERCISE -> lexemes;
            case TOPIC -> topics;
            case TOPIC_ITEM -> items;
            case PRON_STAGE, PRON_UNIT -> stages;
        };
        return mockingDetails(repository).getInvocations().stream().filter(i -> i.getMethod().getName().equals("saveAndFlush"))
                .reduce((first, last) -> last).orElseThrow().getArgument(0);
    }

    private void clearSaves() {
        clearInvocations(lexemes, topics, items, stages);
    }

    private void assertNoSaves() {
        verify(lexemes, never()).saveAndFlush(any());
        verify(topics, never()).saveAndFlush(any());
        verify(items, never()).saveAndFlush(any());
        verify(stages, never()).saveAndFlush(any());
    }

    static Map<String, String> example(ImportDataType type) {
        Map<String, String> values = new LinkedHashMap<>();
        ImportTemplate.forType(type).columns().forEach(c -> values.put(c.name(), c.example()));
        return values;
    }

    private static void keepRequired(ImportDataType type, Map<String, String> values) {
        ImportTemplate.forType(type).columns().stream().filter(c -> !c.required()).forEach(c -> values.remove(c.name()));
    }

    static ValidatedRow row(Map<String, String> values) {
        return new ValidatedRow(2, values, null, null);
    }

    static String changedColumn(ImportDataType type) {
        return switch (type) {
            case LEXEME -> "meaning_vi";
            case TOPIC -> "name";
            case TOPIC_ITEM -> "order_index";
            case GRAMMAR -> "title_vi";
            case GRAMMAR_EXERCISE -> "stem";
            case PRON_STAGE -> "name";
            case PRON_UNIT -> "answer";
        };
    }

    static String changedValue(ImportDataType type) {
        return switch (type) {
            case TOPIC_ITEM -> "7";
            case PRON_UNIT -> "B";
            default -> "Nội dung đã sửa";
        };
    }
}
