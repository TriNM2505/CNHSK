package com.cnhsk.learning.content.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImportChangePlannerTest {
    private LexemeRepository lexemes;
    private TopicRepository topics;
    private TopicItemRepository items;
    private PronStageRepository stages;
    private ImportChangePlanner planner;

    @BeforeEach
    void setup() {
        lexemes = mock(LexemeRepository.class); topics = mock(TopicRepository.class);
        items = mock(TopicItemRepository.class); stages = mock(PronStageRepository.class);
        planner = new ImportChangePlanner(lexemes, topics, items, stages);
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR013_absentNaturalKeyIsCreateForEveryTemplate(ImportDataType type) {
        assertThat(action(type, example(type))).isEqualTo(RowAction.CREATE);
        verify(lexemes, never()).save(any()); verify(topics, never()).save(any());
        verify(items, never()).save(any()); verify(stages, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR013_existingKeyWithAllEqualValuesIsUnchanged(ImportDataType type) {
        fixtures();
        assertThat(action(type, example(type))).isEqualTo(RowAction.UNCHANGED);
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR013_anyChangedValueMakesUpdateWithoutMutatingStoredJson(ImportDataType type) {
        fixtures();
        Map<String, String> v = example(type);
        String column = switch (type) {
            case LEXEME -> "meaning_vi";
            case TOPIC, PRON_STAGE -> "name";
            case TOPIC_ITEM -> "order_index";
            case GRAMMAR -> "example2_vi";
            case GRAMMAR_EXERCISE -> "option_c";
            case PRON_UNIT -> "prompt_vi";
        };
        v.put(column, type == ImportDataType.TOPIC_ITEM ? "9" : "Đã đổi");
        assertThat(action(type, v)).isEqualTo(RowAction.UPDATE);
        assertThat(action(type, example(type))).isEqualTo(RowAction.UNCHANGED);
        verify(lexemes, never()).save(any()); verify(stages, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR013_FR020_optionalBlankKeepsOldValuesIncludingNestedJson(ImportDataType type) {
        fixtures();
        Map<String, String> v = example(type);
        ImportTemplate.forType(type).columns().stream().filter(c -> !c.required()).forEach(c -> v.put(c.name(), ""));
        assertThat(action(type, v)).isEqualTo(RowAction.UNCHANGED);
    }

    @Test
    void FR013_numericRepresentationIsComparedAsNumbersAndKeysStaySeparated() {
        fixtures();
        Map<String, String> lexeme = example(ImportDataType.LEXEME);
        lexeme.put("hsk_level", "01");
        assertThat(action(ImportDataType.LEXEME, lexeme)).isEqualTo(RowAction.UNCHANGED);
        lexeme.put("kind", "CHAR");
        assertThat(action(ImportDataType.LEXEME, lexeme)).isEqualTo(RowAction.CREATE);
        Map<String, String> stage = example(ImportDataType.PRON_STAGE);
        stage.put("pass_percent", "80.0");
        assertThat(action(ImportDataType.PRON_STAGE, stage)).isEqualTo(RowAction.UNCHANGED);
    }

    @Test
    void FR013_referenceLookupsUseBatchesOfAtMost500AndKeepRowOrder() {
        List<ValidatedRow> rows = IntStream.range(0, 1201).mapToObj(i -> new ValidatedRow(i + 2,
                Map.of("code", "T08_" + i, "name", "Chủ đề", "hsk_level", "1"), null, null)).toList();
        when(topics.findByCodeIn(any())).thenAnswer(invocation -> {
            assertThat((java.util.Collection<?>) invocation.getArgument(0)).hasSizeLessThanOrEqualTo(500);
            return List.of();
        });
        assertThat(planner.plan(ImportDataType.TOPIC, rows)).extracting(ImportPreview.PreviewRow::line)
                .containsExactlyElementsOf(IntStream.range(2, 1203).boxed().toList());
        verify(topics, times(3)).findByCodeIn(any());
    }

    @Test
    void FR013_newExerciseAndUnitInsideExistingParentAreCreate() {
        fixtures();
        Map<String, String> exercise = example(ImportDataType.GRAMMAR_EXERCISE);
        exercise.put("exercise_no", "2");
        assertThat(action(ImportDataType.GRAMMAR_EXERCISE, exercise)).isEqualTo(RowAction.CREATE);
        Map<String, String> unit = example(ImportDataType.PRON_UNIT);
        unit.put("unit_code", "NEW");
        assertThat(action(ImportDataType.PRON_UNIT, unit)).isEqualTo(RowAction.CREATE);
    }

    private RowAction action(ImportDataType type, Map<String, String> v) {
        return planner.plan(type, List.of(new ValidatedRow(7, v, null, null))).get(0).action();
    }

    private static Map<String, String> example(ImportDataType type) {
        Map<String, String> values = new LinkedHashMap<>();
        ImportTemplate.forType(type).columns().forEach(c -> values.put(c.name(), c.example()));
        return values;
    }

    private void fixtures() {
        Map<String, String> v = example(ImportDataType.LEXEME);
        Lexeme word = new Lexeme(LexemeKind.WORD, v.get("text"), v.get("meaning_vi"));
        ReflectionTestUtils.setField(word, "id", 11L);
        word.setPinyin(v.get("pinyin")); word.setHskLevel((short) 1); word.setSinoViet(v.get("sino_viet"));
        word.setAudioUrl(v.get("audio_url"));
        word.setExtra(new LexemeExtra(List.of(new LexemeExtra.Example(v.get("example_cn"), v.get("example_pinyin"), v.get("example_vi"))),
                List.of(v.get("tags").split("\\|")), null, null, null, null));
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.WORD), any())).thenReturn(List.of(word));
        Map<String, String> g = example(ImportDataType.GRAMMAR);
        Map<String, String> e = example(ImportDataType.GRAMMAR_EXERCISE);
        Lexeme grammar = new Lexeme(LexemeKind.GRAMMAR, g.get("text"), g.get("title_vi"));
        grammar.setHskLevel((short) 2);
        List<LexemeExtra.Example> examples = new ArrayList<>();
        for (int i = 1; i <= 3; i++) examples.add(new LexemeExtra.Example(g.get("example" + i + "_cn"),
                g.get("example" + i + "_pinyin"), g.get("example" + i + "_vi")));
        List<LexemeExtra.Option> options = List.of("A", "B", "C", "D").stream()
                .map(k -> new LexemeExtra.Option(k, e.get("option_" + k.toLowerCase(java.util.Locale.ROOT)))).toList();
        grammar.setExtra(new LexemeExtra(examples, null, g.get("structure"), g.get("explanation_vi"), g.get("common_mistake_vi"),
                List.of(new LexemeExtra.Exercise(1, e.get("stem"), options, e.get("answer"), e.get("explanation_vi")))));
        when(lexemes.findByKindAndTextIn(eq(LexemeKind.GRAMMAR), any())).thenReturn(List.of(grammar));
        Map<String, String> t = example(ImportDataType.TOPIC);
        Topic topic = new Topic(t.get("code"), t.get("name"), (short) 1);
        ReflectionTestUtils.setField(topic, "id", 22L);
        when(topics.findByCodeIn(any())).thenReturn(List.of(topic));
        when(items.findByTopicIdInAndLexemeIdIn(any(), any())).thenReturn(List.of(new TopicItem(22L, 11L)));
        Map<String, String> s = example(ImportDataType.PRON_STAGE);
        Map<String, String> u = example(ImportDataType.PRON_UNIT);
        PronStage stage = new PronStage((short) 1, s.get("name"));
        stage.setDescription(s.get("description")); stage.setPassPercent(new BigDecimal("80.00"));
        stage.setTargetSounds(List.of("b", "p"));
        stage.setUnits(List.of(new PronUnit(u.get("unit_code"), u.get("audio_url"), u.get("prompt_vi"),
                List.of("A", "B", "C", "D").stream().map(k -> new PronUnit.Option(k,
                        u.get("option_" + k.toLowerCase(java.util.Locale.ROOT)))).toList(), u.get("answer"), u.get("explanation_vi"))));
        when(stages.findByStageIndexIn(any())).thenReturn(List.of(stage));
    }
}
