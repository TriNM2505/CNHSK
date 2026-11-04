package com.cnhsk.learning.content.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.cnhsk.learning.content.repository.NodeLinkRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgeTreeQueryRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.content.importer.validate.QuestionRowValidatorTest;
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
    private KnowledgePointRepository points;
    private QuestionRepository questions;
    private KnowledgeTreeQueryRepository questionLabels;
    private NodeLinkRepository nodeLinks;

    @BeforeEach
    void setup() {
        lexemes = mock(LexemeRepository.class); topics = mock(TopicRepository.class);
        items = mock(TopicItemRepository.class); stages = mock(PronStageRepository.class);
        points = mock(KnowledgePointRepository.class);
        questions = mock(QuestionRepository.class); questionLabels = mock(KnowledgeTreeQueryRepository.class);
        nodeLinks = mock(NodeLinkRepository.class);
        planner = new ImportChangePlanner(lexemes, topics, items, stages, points, questions, questionLabels, nodeLinks);
    }

    @Test
    void FR014_nodeLinkPreviewDistinguishesNullSameAndDifferentLinksWithoutCreatingTargets() {
        when(nodeLinks.topics(any())).thenReturn(List.of(new NodeLinkRepository.Target(1, "T09_TOPIC", null, null)));
        var row = new ValidatedRow(2, Map.of("target_type", "TOPIC", "target_key", "T09_TOPIC", "kid", "TV1-801"), null, null);
        assertThat(planner.plan(ImportDataType.NODE_LINK, List.of(row)).get(0).action()).isEqualTo(RowAction.UPDATE);
        when(nodeLinks.topics(any())).thenReturn(List.of(new NodeLinkRepository.Target(1, "T09_TOPIC", 21L, "TV1-801")));
        assertThat(planner.plan(ImportDataType.NODE_LINK, List.of(row)).get(0).action()).isEqualTo(RowAction.UNCHANGED);
        when(nodeLinks.topics(any())).thenReturn(List.of(new NodeLinkRepository.Target(1, "T09_TOPIC", 22L, "TV1-802")));
        assertThat(planner.plan(ImportDataType.NODE_LINK, List.of(row)).get(0).action()).isEqualTo(RowAction.UPDATE);
        verify(nodeLinks, never()).updateTopic(anyLong(), anyLong()); verify(nodeLinks, never()).lockTopic(any());
    }

    @Test
    void FR014_nodeLinkSnapshotsKeepKindsDistinctAndReadAtMostFiveHundredKeysPerBatch() {
        when(nodeLinks.lexemes(any(), any())).thenAnswer(call -> {
            LexemeKind kind = call.getArgument(0);
            java.util.Collection<String> keys = call.getArgument(1);
            assertThat(keys).hasSizeLessThanOrEqualTo(500);
            return keys.stream().map(key -> new NodeLinkRepository.Target(1, key, 21L,
                    kind == LexemeKind.CHAR ? "CH1-002" : "TV1-801")).toList();
        });
        var rows = IntStream.range(0, 1201).mapToObj(index -> new ValidatedRow(index + 2,
                Map.of("target_type", index % 2 == 0 ? "CHAR" : "WORD", "target_key", "木" + index,
                        "kid", index % 2 == 0 ? "CH1-002" : "TV1-801"), null, null)).toList();
        assertThat(planner.plan(ImportDataType.NODE_LINK, rows)).hasSize(1201)
                .allSatisfy(row -> assertThat(row.action()).isEqualTo(RowAction.UNCHANGED));
        verify(nodeLinks, times(3)).lexemes(eq(LexemeKind.CHAR), any());
        verify(nodeLinks, times(3)).lexemes(eq(LexemeKind.WORD), any());
        verifyNoInteractions(points);
        verify(nodeLinks, never()).updateLexeme(anyLong(), anyLong());
    }

    @Test
    void FR012_questionPreviewComparesFullReplacementLabelsAndOptionalScalarPreservation() {
        var question = QuestionRowValidatorTest.existing("Q_IMPORT", 70);
        question.setDifficulty((short) 3); question.setAudioUrl("https://example.test/kept.mp3");
        when(questions.findByCodeIn(any())).thenReturn(List.of(question));
        when(questionLabels.questionLabels(any())).thenReturn(List.of(
                new KnowledgeTreeQueryRepository.QuestionLabelRow(question, "TV1-001", KnowledgeLabelRole.PRIMARY),
                new KnowledgeTreeQueryRepository.QuestionLabelRow(question, "TV1-002", KnowledgeLabelRole.SECONDARY),
                new KnowledgeTreeQueryRepository.QuestionLabelRow(question, "NP2-003", KnowledgeLabelRole.SECONDARY)));
        var cells = QuestionRowValidatorTest.values(com.cnhsk.learning.knowledge.entity.QuestionType.MULTIPLE_CHOICE);
        cells.put("kid_secondary", "NP2-003|TV1-002"); cells.put("difficulty", ""); cells.put("audio_url", "");
        assertThat(action(ImportDataType.QUESTION, cells)).isEqualTo(RowAction.UNCHANGED);
        cells.put("kid_secondary", ""); assertThat(action(ImportDataType.QUESTION, cells)).isEqualTo(RowAction.UPDATE);
        cells.put("explanation", "Nội dung mới"); assertThat(action(ImportDataType.QUESTION, cells)).isEqualTo(RowAction.UPDATE);
        verify(questions, never()).saveAndFlush(any());
    }

    @Test
    void FR012_questionPreviewUsesAtMostTwoQueriesPerFiveHundredRows() {
        Map<Long, com.cnhsk.learning.knowledge.entity.Question> loaded = new LinkedHashMap<>();
        when(questions.findByCodeIn(any())).thenAnswer(call -> {
            java.util.Collection<String> codes = call.getArgument(0); assertThat(codes).hasSizeLessThanOrEqualTo(500);
            return codes.stream().map(code -> {
                var question = QuestionRowValidatorTest.existing(code, Math.abs((long) code.hashCode()) + 1);
                loaded.put(question.getId(), question); return question;
            }).toList();
        });
        when(questionLabels.questionLabels(any())).thenAnswer(call -> {
            Set<Long> ids = call.getArgument(0);
            return ids.stream().map(id -> new KnowledgeTreeQueryRepository.QuestionLabelRow(loaded.get(id), "TV1-001", KnowledgeLabelRole.PRIMARY)).toList();
        });
        var rows = IntStream.range(0, 1201).mapToObj(index -> {
            var cells = QuestionRowValidatorTest.values(com.cnhsk.learning.knowledge.entity.QuestionType.MULTIPLE_CHOICE);
            cells.put("code", "Q_BATCH_" + index); return new ValidatedRow(index + 2, cells, null, null);
        }).toList();
        assertThat(planner.plan(ImportDataType.QUESTION, rows)).hasSize(1201)
                .allSatisfy(row -> assertThat(row.action()).isEqualTo(RowAction.UNCHANGED));
        verify(questions, times(3)).findByCodeIn(any()); verify(questionLabels, times(3)).questionLabels(any());
        verify(questions, never()).saveAndFlush(any()); verify(questions, never()).findByCode(any());
    }

    @Test
    void FR007_edgeSnapshotsDistinguishBothArraysAndMissingPairsWithoutWriting() {
        KnowledgePoint source = new KnowledgePoint("TV1-901", "Nguồn", KnowledgeDomain.TV, "示例", (short) 1);
        KnowledgePoint target = new KnowledgePoint("TV1-902", "Đích", KnowledgeDomain.TV, "示例", (short) 1);
        ReflectionTestUtils.setField(source, "id", 901L);
        ReflectionTestUtils.setField(target, "id", 902L);
        when(points.findByCodeIn(any())).thenReturn(List.of(source, target));
        for (String type : List.of("PREREQ", "SUPPORT")) {
            ValidatedRow row = new ValidatedRow(2, Map.of("from_kid", source.getCode(), "to_kid", target.getCode(),
                    "edge_type", type), null, null);
            assertThat(planner.plan(ImportDataType.KNOWLEDGE_EDGE, List.of(row)).get(0).action()).isEqualTo(RowAction.CREATE);
            if (type.equals("PREREQ")) target.setPrereqIds(new Long[]{901L});
            else target.setSupportIds(new Long[]{901L});
            assertThat(planner.plan(ImportDataType.KNOWLEDGE_EDGE, List.of(row)).get(0).action()).isEqualTo(RowAction.UNCHANGED);
            target.setPrereqIds(new Long[0]); target.setSupportIds(new Long[0]);
        }
        verify(points, never()).saveAndFlush(any());
    }

    @Test
    void FR007_edgeSnapshotsReadBothEndpointsOncePerFiveHundredRows() {
        List<ValidatedRow> rows = IntStream.range(0, 1201).mapToObj(i -> new ValidatedRow(i + 2,
                Map.of("from_kid", "TV1-901", "to_kid", "TV2-" + i, "edge_type", "PREREQ"), null, null)).toList();
        when(points.findByCodeIn(any())).thenAnswer(invocation -> {
            java.util.Collection<String> kids = invocation.getArgument(0);
            assertThat(kids).contains("TV1-901").hasSizeLessThanOrEqualTo(501);
            return List.of();
        });
        assertThat(planner.plan(ImportDataType.KNOWLEDGE_EDGE, rows)).hasSize(1201)
                .allSatisfy(row -> assertThat(row.action()).isEqualTo(RowAction.CREATE));
        verify(points, times(3)).findByCodeIn(any());
        verify(points, never()).findAll();
        verify(points, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"KNOWLEDGE_EDGE", "QUESTION", "NODE_LINK"}, mode = EnumSource.Mode.EXCLUDE)
    void FR013_absentNaturalKeyIsCreateForEveryTemplate(ImportDataType type) {
        assertThat(action(type, example(type))).isEqualTo(RowAction.CREATE);
        verify(lexemes, never()).save(any()); verify(topics, never()).save(any());
        verify(items, never()).save(any()); verify(stages, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"KNOWLEDGE_EDGE", "QUESTION", "NODE_LINK"}, mode = EnumSource.Mode.EXCLUDE)
    void FR013_existingKeyWithAllEqualValuesIsUnchanged(ImportDataType type) {
        fixtures();
        assertThat(action(type, example(type))).isEqualTo(RowAction.UNCHANGED);
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"KNOWLEDGE_EDGE", "QUESTION", "NODE_LINK"}, mode = EnumSource.Mode.EXCLUDE)
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
            case KNOWLEDGE_NODE -> "name_vi";
            default -> throw new IllegalArgumentException("No import planner fixture: " + type);
        };
        v.put(column, type == ImportDataType.TOPIC_ITEM ? "9" : "Đã đổi");
        assertThat(action(type, v)).isEqualTo(RowAction.UPDATE);
        assertThat(action(type, example(type))).isEqualTo(RowAction.UNCHANGED);
        verify(lexemes, never()).save(any()); verify(stages, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ImportDataType.class, names = {"KNOWLEDGE_EDGE", "QUESTION", "NODE_LINK"}, mode = EnumSource.Mode.EXCLUDE)
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

    @Test
    void FR004_nodeLookupsAreBatchedAndPreviewNeverSaves() {
        List<ValidatedRow> rows = IntStream.range(0, 1001).mapToObj(i -> new ValidatedRow(i + 2,
                Map.of("kid", "NP" + (i / 500 + 1) + "-" + String.format(java.util.Locale.ROOT, "%03d", i % 500),
                        "name_vi", "Nút tự soạn", "name_zh", "测试节点"), null, null)).toList();
        when(points.findByCodeIn(any())).thenAnswer(invocation -> {
            assertThat((java.util.Collection<?>) invocation.getArgument(0)).hasSizeLessThanOrEqualTo(500);
            return List.of();
        });
        assertThat(planner.plan(ImportDataType.KNOWLEDGE_NODE, rows)).extracting(ImportPreview.PreviewRow::line)
                .containsExactlyElementsOf(IntStream.range(2, 1003).boxed().toList());
        verify(points, times(3)).findByCodeIn(any());
        verify(points, never()).save(any());
        verify(points, never()).saveAndFlush(any());
    }

    @Test
    void FR004_emptyNodePreviewDoesNotQuery() {
        assertThat(planner.plan(ImportDataType.KNOWLEDGE_NODE, List.of())).isEmpty();
        verifyNoInteractions(points);
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
        Map<String, String> n = example(ImportDataType.KNOWLEDGE_NODE);
        KnowledgePoint node = new KnowledgePoint(n.get("kid"), n.get("name_vi"), KnowledgeDomain.TV, n.get("name_zh"), (short) 1);
        node.setCanDo(n.get("can_do"));
        node.setSkillType(KnowledgeSkillType.valueOf(n.get("skill_type")));
        when(points.findByCodeIn(any())).thenReturn(List.of(node));
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
