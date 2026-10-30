package com.cnhsk.learning.content.importer.write;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.ImportChangePlanner;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationResult;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.validate.RowValidator;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic content is written only to the isolated Testcontainers database. */
@Transactional
class RowWritersIntegrationTest extends AbstractIntegrationTest {
    @Autowired private List<RowWriter> writerBeans;
    @Autowired private List<RowValidator> validatorBeans;
    @Autowired private CsvReader reader;
    @Autowired private ImportChangePlanner planner;
    @Autowired private LexemeRepository lexemes;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository items;
    @Autowired private PronStageRepository stages;
    @Autowired private EntityManager entityManager;
    private Map<ImportDataType, RowWriter> writers;
    private Map<ImportDataType, RowValidator> validators;

    @BeforeEach
    void setup() {
        writers = writerBeans.stream().collect(Collectors.toMap(RowWriter::dataType, Function.identity()));
        validators = validatorBeans.stream().collect(Collectors.toMap(RowValidator::dataType, Function.identity()));
    }

    @Test
    void FR020_allSevenWritersAreAvailableWithoutRequiringATransactionForMetadata() {
        assertThat(writers).containsOnlyKeys(ImportDataType.values());
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR020_eachTemplatePersistsThenReimportsAndUpdatesTheSameNaturalKey(ImportDataType type) {
        parents(type);
        Map<String, String> values = example(type);
        ValidatedRow row = validate(type, values);
        assertThat(writers.get(type).write(row)).isEqualTo(RowAction.CREATE);
        reload();
        long count = recordCount(type);
        assertThat(planner.plan(type, List.of(row)).get(0).action()).isEqualTo(RowAction.UNCHANGED);
        assertThat(writers.get(type).write(row)).isEqualTo(RowAction.UNCHANGED);
        values.put(RowWritersTest.changedColumn(type), RowWritersTest.changedValue(type));
        ValidatedRow changed = validate(type, values);
        assertThat(writers.get(type).write(changed)).isEqualTo(RowAction.UPDATE);
        reload();
        assertThat(recordCount(type)).isEqualTo(count);
        assertThat(planner.plan(type, List.of(changed)).get(0).action()).isEqualTo(RowAction.UNCHANGED);
        assertThat(writers.get(type).write(changed)).isEqualTo(RowAction.UNCHANGED);
        reload();
        assertThat(recordCount(type)).isEqualTo(count);
    }

    @Test
    void FR020_AT24_audioAndOtherOptionalValuesSurviveBlankAndOmittedColumns() {
        Map<String, String> values = example(ImportDataType.LEXEME);
        writers.get(ImportDataType.LEXEME).write(validate(ImportDataType.LEXEME, values));
        Lexeme original = lexemes.findByKindAndText(LexemeKind.WORD, "我们").orElseThrow();
        Long id = original.getId();
        Instant createdAt = original.getCreatedAt();
        original.setFrequency(23);
        LexemeExtra extra = original.getExtra();
        values.put("audio_url", "");
        values.remove("sino_viet");
        values.put("tags", "||| ");
        values.put("example_cn", "");
        values.put("example_vi", "");
        values.remove("example_pinyin");
        values.put("pinyin", " Nǐ hǎo ");
        values.put("meaning_vi", " Đi học ");
        values.put("hsk_level", "3");
        assertThat(writers.get(ImportDataType.LEXEME).write(validate(ImportDataType.LEXEME, values))).isEqualTo(RowAction.UPDATE);
        reload();
        Lexeme stored = lexemes.findById(id).orElseThrow();
        assertThat(stored.getAudioUrl()).isEqualTo(example(ImportDataType.LEXEME).get("audio_url"));
        assertThat(stored.getSinoViet()).isEqualTo(example(ImportDataType.LEXEME).get("sino_viet"));
        assertThat(stored.getExtra()).isEqualTo(extra);
        assertThat(stored.getPinyin()).isEqualTo("Nǐ hǎo");
        assertThat(stored.getPinyinNorm()).isEqualTo("nihao");
        assertThat(stored.getMeaningVi()).isEqualTo("Đi học");
        assertThat(stored.getMeaningNorm()).isEqualTo("di hoc");
        assertThat(stored.getHskLevel()).isEqualTo((short) 3);
        assertThat(stored.getFrequency()).isEqualTo(23);
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void FR020_AT37_topicThenTopicItemKeepIdsPrerequisitesAndNullKnowledgePoint() {
        parents(ImportDataType.TOPIC_ITEM);
        Topic topic = topics.findByCode("DAILY_LIFE").orElseThrow();
        Long topicId = topic.getId();
        topic.setPrereqIds(new Long[]{51L, 52L});
        Long lexemeId = lexemes.findByKindAndText(LexemeKind.WORD, "我们").orElseThrow().getId();
        Map<String, String> item = example(ImportDataType.TOPIC_ITEM);
        item.remove("order_index");
        assertThat(writers.get(ImportDataType.TOPIC_ITEM).write(validate(ImportDataType.TOPIC_ITEM, item))).isEqualTo(RowAction.CREATE);
        reload();
        TopicItem first = items.findByTopicIdAndLexemeId(topicId, lexemeId).orElseThrow();
        Long itemId = first.getId();
        assertThat(first.getKnowledgePointId()).isNull();
        assertThat(first.getOrderIndex()).isZero();
        item.put("order_index", "27");
        assertThat(writers.get(ImportDataType.TOPIC_ITEM).write(validate(ImportDataType.TOPIC_ITEM, item))).isEqualTo(RowAction.UPDATE);
        Map<String, String> topicValues = example(ImportDataType.TOPIC);
        topicValues.put("name", "Tên chủ đề đã sửa");
        topicValues.put("order_index", "");
        assertThat(writers.get(ImportDataType.TOPIC).write(validate(ImportDataType.TOPIC, topicValues))).isEqualTo(RowAction.UPDATE);
        reload();
        TopicItem updated = items.findById(itemId).orElseThrow();
        assertThat(updated.getTopicId()).isEqualTo(topicId);
        assertThat(updated.getLexemeId()).isEqualTo(lexemeId);
        assertThat(updated.getKnowledgePointId()).isNull();
        assertThat(updated.getOrderIndex()).isEqualTo(27);
        assertThat(topics.findById(topicId).orElseThrow().getPrereqIds()).containsExactly(51L, 52L);
    }

    @Test
    void FR020_AT38_threeExercisesStaySortedAndSurviveGrammarReimport() {
        parents(ImportDataType.GRAMMAR_EXERCISE);
        Lexeme grammar = lexemes.findByKindAndText(LexemeKind.GRAMMAR, "是……的").orElseThrow();
        Long id = grammar.getId();
        LexemeExtra before = grammar.getExtra();
        Map<String, String> exercise = example(ImportDataType.GRAMMAR_EXERCISE);
        for (String no : List.of("3", "1", "2")) {
            exercise.put("exercise_no", no);
            assertThat(writers.get(ImportDataType.GRAMMAR_EXERCISE).write(validate(ImportDataType.GRAMMAR_EXERCISE, exercise)))
                    .isEqualTo(RowAction.CREATE);
            reload();
        }
        LexemeExtra afterExercises = lexemes.findById(id).orElseThrow().getExtra();
        assertThat(afterExercises.exercises()).extracting(LexemeExtra.Exercise::no).containsExactly(1, 2, 3);
        assertThat(afterExercises.examples()).isEqualTo(before.examples());
        assertThat(afterExercises.structure()).isEqualTo(before.structure());
        Map<String, String> grammarValues = example(ImportDataType.GRAMMAR);
        grammarValues.put("title_vi", "Tên ngữ pháp mới");
        grammarValues.put("explanation_vi", "Giải thích mới");
        grammarValues.put("structure", "");
        assertThat(writers.get(ImportDataType.GRAMMAR).write(validate(ImportDataType.GRAMMAR, grammarValues))).isEqualTo(RowAction.UPDATE);
        reload();
        Lexeme stored = lexemes.findById(id).orElseThrow();
        assertThat(stored.getExtra().exercises()).isEqualTo(afterExercises.exercises());
        assertThat(stored.getExtra().structure()).isEqualTo(before.structure());
        assertThat(stored.getMeaningNorm()).isEqualTo("ten ngu phap moi");
        exercise.put("exercise_no", "1");
        exercise.put("stem", "Đề bài đã sửa");
        exercise.put("answer", "B");
        exercise.put("option_c", ""); exercise.put("option_d", ""); exercise.put("explanation_vi", "");
        assertThat(writers.get(ImportDataType.GRAMMAR_EXERCISE).write(validate(ImportDataType.GRAMMAR_EXERCISE, exercise)))
                .isEqualTo(RowAction.UPDATE);
        reload();
        List<LexemeExtra.Exercise> updated = lexemes.findById(id).orElseThrow().getExtra().exercises();
        assertThat(updated).hasSize(3);
        assertThat(updated.get(0).answerKey()).isEqualTo("B");
        assertThat(updated.get(0).options()).isEqualTo(afterExercises.exercises().get(0).options());
        assertThat(updated.get(0).explanation()).isEqualTo(afterExercises.exercises().get(0).explanation());
    }

    @Test
    void FR020_AT40_eightStagesTenUnitsAndUnitUpdatePreserveCountsOrderAndNestedValues() {
        for (int index = 1; index <= 8; index++) {
            Map<String, String> values = example(ImportDataType.PRON_STAGE);
            values.put("stage_index", String.valueOf(index));
            assertThat(writers.get(ImportDataType.PRON_STAGE).write(validate(ImportDataType.PRON_STAGE, values))).isEqualTo(RowAction.CREATE);
        }
        Map<String, String> unit = example(ImportDataType.PRON_UNIT);
        for (int index = 9; index >= 0; index--) {
            unit.put("unit_code", "BP_" + index);
            assertThat(writers.get(ImportDataType.PRON_UNIT).write(validate(ImportDataType.PRON_UNIT, unit))).isEqualTo(RowAction.CREATE);
        }
        reload();
        List<PronUnit> original = stages.findByStageIndex((short) 1).orElseThrow().getUnits();
        assertThat(original).hasSize(10);
        unit.put("unit_code", "BP_4");
        unit.put("answer", "B");
        unit.put("prompt_vi", ""); unit.put("explanation_vi", "");
        assertThat(writers.get(ImportDataType.PRON_UNIT).write(validate(ImportDataType.PRON_UNIT, unit))).isEqualTo(RowAction.UPDATE);
        Map<String, String> stage = example(ImportDataType.PRON_STAGE);
        stage.put("pass_percent", "85.50");
        stage.put("target_sounds", " | ");
        assertThat(writers.get(ImportDataType.PRON_STAGE).write(validate(ImportDataType.PRON_STAGE, stage))).isEqualTo(RowAction.UPDATE);
        reload();
        PronStage stored = stages.findByStageIndex((short) 1).orElseThrow();
        assertThat(stages.count()).isEqualTo(8);
        assertThat(stored.getUnits()).hasSize(10);
        assertThat(stored.getUnits()).extracting(PronUnit::code).containsExactlyElementsOf(original.stream().map(PronUnit::code).toList());
        assertThat(stored.getUnits().get(5).answerKey()).isEqualTo("B");
        assertThat(stored.getUnits().get(5).prompt()).isEqualTo(original.get(5).prompt());
        assertThat(stored.getUnits().get(5).explanation()).isEqualTo(original.get(5).explanation());
        assertThat(stored.getTargetSounds()).containsExactly("b", "p");
        assertThat(stored.getPassPercent()).isEqualByComparingTo("85.50");
        Instant updatedAt = stored.getUpdatedAt();
        assertThat(writers.get(ImportDataType.PRON_STAGE).write(validate(ImportDataType.PRON_STAGE, stage))).isEqualTo(RowAction.UNCHANGED);
        reload();
        assertThat(stages.findById(stored.getId()).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"TOPIC", "TOPIC_ITEM", "PRON_STAGE", "PRON_UNIT"})
    void FR020_newRowsUseDeclaredDefaultsForMissingOptionalColumns(ImportDataType type) {
        parents(type);
        Map<String, String> values = example(type);
        ImportTemplate.forType(type).columns().stream().filter(c -> !c.required()).forEach(c -> values.remove(c.name()));
        assertThat(writers.get(type).write(validate(type, values))).isEqualTo(RowAction.CREATE);
        reload();
        switch (type) {
            case TOPIC -> assertThat(topics.findByCode("DAILY_LIFE").orElseThrow().getOrderIndex()).isZero();
            case TOPIC_ITEM -> assertThat(items.findAll()).singleElement().satisfies(item -> assertThat(item.getOrderIndex()).isZero());
            case PRON_STAGE -> {
                PronStage stage = stages.findByStageIndex((short) 1).orElseThrow();
                assertThat(stage.getPassPercent()).isEqualByComparingTo("80.00");
                assertThat(stage.getTargetSounds()).isEmpty();
                assertThat(stage.getUnits()).isEmpty();
            }
            case PRON_UNIT -> {
                PronUnit unit = stages.findByStageIndex((short) 1).orElseThrow().getUnits().get(0);
                assertThat(unit.prompt()).isEqualTo("Nghe và chọn đáp án đúng");
                assertThat(unit.options()).hasSize(2);
                assertThat(unit.explanation()).isNull();
            }
            default -> throw new IllegalStateException("Unexpected default-value test case");
        }
    }

    @Test
    void FR020_sparseGrammarExamplesRetainTheirDeclaredJsonArrayIndexes() {
        Map<String, String> values = example(ImportDataType.GRAMMAR);
        values.keySet().removeIf(key -> key.startsWith("example2"));
        writers.get(ImportDataType.GRAMMAR).write(validate(ImportDataType.GRAMMAR, values));
        reload();
        List<LexemeExtra.Example> examples = lexemes.findByKindAndText(LexemeKind.GRAMMAR, values.get("text")).orElseThrow().getExtra().examples();
        assertThat(examples).hasSize(3);
        assertThat(examples.get(1)).isEqualTo(new LexemeExtra.Example(null, null, null));
        assertThat(examples.get(2).cn()).isEqualTo(values.get("example3_cn"));
    }

    @Test
    void FR020_actionsReadCurrentDatabaseValuesInsteadOfReusingPreviewActions() {
        ValidatedRow row = validate(ImportDataType.TOPIC, example(ImportDataType.TOPIC));
        assertThat(planner.plan(ImportDataType.TOPIC, List.of(row)).get(0).action()).isEqualTo(RowAction.CREATE);
        writers.get(ImportDataType.TOPIC).write(row);
        reload();
        assertThat(writers.get(ImportDataType.TOPIC).write(row)).isEqualTo(RowAction.UNCHANGED);
        Topic topic = topics.findByCode("DAILY_LIFE").orElseThrow();
        Long id = topic.getId();
        topic.setName("Nội dung thay đổi sau preview");
        reload();
        assertThat(writers.get(ImportDataType.TOPIC).write(row)).isEqualTo(RowAction.UPDATE);
        reload();
        assertThat(topics.findByCode("DAILY_LIFE").orElseThrow().getId()).isEqualTo(id);
    }

    @Test
    void FR020_writerDoesNotCommitIndependentlyOfTheCallerTransaction() {
        ValidatedRow row = validate(ImportDataType.TOPIC, example(ImportDataType.TOPIC));
        writers.get(ImportDataType.TOPIC).write(row);
        reload();
        assertThat(topics.findByCode("DAILY_LIFE")).isPresent();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        assertThat(topics.findByCode("DAILY_LIFE")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR020_writingWithoutCallerTransactionIsRejectedBeforeAnyMutation(ImportDataType type) {
        ValidatedRow row = RowWritersTest.row(example(type));
        assertThatThrownBy(() -> writers.get(type).write(row)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private void parents(ImportDataType type) {
        switch (type) {
            case TOPIC_ITEM -> {
                writers.get(ImportDataType.TOPIC).write(validate(ImportDataType.TOPIC, example(ImportDataType.TOPIC)));
                writers.get(ImportDataType.LEXEME).write(validate(ImportDataType.LEXEME, example(ImportDataType.LEXEME)));
            }
            case GRAMMAR_EXERCISE -> writers.get(ImportDataType.GRAMMAR).write(validate(ImportDataType.GRAMMAR, example(ImportDataType.GRAMMAR)));
            case PRON_UNIT -> writers.get(ImportDataType.PRON_STAGE).write(validate(ImportDataType.PRON_STAGE, example(ImportDataType.PRON_STAGE)));
            default -> { /* Independent templates do not need existing content. */ }
        }
    }

    private ValidatedRow validate(ImportDataType type, Map<String, String> values) {
        String csv = String.join(",", values.keySet()) + "\n" + values.values().stream()
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"").collect(Collectors.joining(",")) + "\n";
        RowValidationResult result = validators.get(type).validate(reader.read(type, "synthetic.csv", csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        return result.validRows().get(0);
    }

    private long recordCount(ImportDataType type) {
        return switch (type) {
            case LEXEME, GRAMMAR, GRAMMAR_EXERCISE -> lexemes.count();
            case TOPIC -> topics.count();
            case TOPIC_ITEM -> items.count();
            case PRON_STAGE, PRON_UNIT -> stages.count();
        };
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Map<String, String> example(ImportDataType type) {
        return new LinkedHashMap<>(RowWritersTest.example(type));
    }
}
