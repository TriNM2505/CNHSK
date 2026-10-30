package com.cnhsk.learning.content.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.ImportPreview.PreviewRow;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads existing content in bounded batches; preview never changes content. */
@Component
public class ImportChangePlanner {
    private static final int BATCH_SIZE = 500;
    private static final Set<String> NUMERIC = Set.of("hsk_level", "stroke_count", "order_index", "pass_percent");
    private final LexemeRepository lexemes;
    private final TopicRepository topics;
    private final TopicItemRepository topicItems;
    private final PronStageRepository stages;

    public ImportChangePlanner(LexemeRepository lexemes, TopicRepository topics,
            TopicItemRepository topicItems, PronStageRepository stages) {
        this.lexemes = lexemes;
        this.topics = topics;
        this.topicItems = topicItems;
        this.stages = stages;
    }

    @Transactional(readOnly = true)
    public List<PreviewRow> plan(ImportDataType type, List<ValidatedRow> rows) {
        List<PreviewRow> result = new ArrayList<>();
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            List<ValidatedRow> batch = rows.subList(offset, Math.min(rows.size(), offset + BATCH_SIZE));
            Map<List<String>, Map<String, String>> existing = snapshots(type, batch);
            for (ValidatedRow row : batch) {
                Map<String, String> old = existing.get(key(type, row.values()));
                RowAction action = old == null ? RowAction.CREATE : row.values().entrySet().stream()
                        .filter(cell -> !cell.getValue().isBlank())
                        .filter(cell -> !ImportTemplate.forType(type).naturalKey().contains(cell.getKey()))
                        .anyMatch(cell -> !same(cell.getKey(), cell.getValue(), old.get(cell.getKey())))
                                ? RowAction.UPDATE : RowAction.UNCHANGED;
                result.add(new PreviewRow(row.line(), action, row.values()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean same(String column, String incoming, String old) {
        if (old == null) {
            return false;
        }
        return NUMERIC.contains(column) ? new BigDecimal(incoming).compareTo(new BigDecimal(old)) == 0
                : incoming.equals(old);
    }

    private Map<List<String>, Map<String, String>> snapshots(ImportDataType type, List<ValidatedRow> rows) {
        Map<List<String>, Map<String, String>> result = new HashMap<>();
        switch (type) {
            case LEXEME -> {
                for (LexemeKind kind : List.of(LexemeKind.CHAR, LexemeKind.WORD)) {
                    List<String> texts = rows.stream().map(ValidatedRow::values)
                            .filter(v -> kind.name().equals(v.get("kind"))).map(v -> v.get("text")).distinct().toList();
                    if (!texts.isEmpty()) {
                        lexemes.findByKindAndTextIn(kind, texts).forEach(l -> put(result, type, lexeme(l)));
                    }
                }
            }
            case GRAMMAR, GRAMMAR_EXERCISE -> {
                String column = type == ImportDataType.GRAMMAR ? "text" : "grammar_text";
                for (Lexeme l : lexemes.findByKindAndTextIn(LexemeKind.GRAMMAR, values(rows, column))) {
                    if (type == ImportDataType.GRAMMAR) {
                        put(result, type, lexeme(l));
                    } else if (l.getExtra() != null && l.getExtra().exercises() != null) {
                        for (LexemeExtra.Exercise e : l.getExtra().exercises()) {
                            Map<String, String> v = new LinkedHashMap<>();
                            v.put("grammar_text", l.getText());
                            cell(v, "exercise_no", e.no()); cell(v, "stem", e.stem());
                            if (e.options() != null) {
                                e.options().forEach(o -> cell(v, "option_" + o.key().toLowerCase(java.util.Locale.ROOT), o.text()));
                            }
                            cell(v, "answer", e.answerKey()); cell(v, "explanation_vi", e.explanation());
                            put(result, type, v);
                        }
                    }
                }
            }
            case TOPIC -> topics.findByCodeIn(values(rows, "code")).forEach(t -> {
                Map<String, String> v = new LinkedHashMap<>();
                cell(v, "code", t.getCode()); cell(v, "name", t.getName());
                cell(v, "hsk_level", t.getHskLevel()); cell(v, "order_index", t.getOrderIndex());
                put(result, type, v);
            });
            case TOPIC_ITEM -> topicItemSnapshots(rows, result);
            case PRON_STAGE, PRON_UNIT -> {
                List<Short> indices = values(rows, "stage_index").stream().map(Short::valueOf).toList();
                for (PronStage s : stages.findByStageIndexIn(indices)) {
                    if (type == ImportDataType.PRON_STAGE) {
                        Map<String, String> v = new LinkedHashMap<>();
                        cell(v, "stage_index", s.getStageIndex()); cell(v, "name", s.getName());
                        cell(v, "description", s.getDescription()); cell(v, "pass_percent", s.getPassPercent());
                        if (s.getTargetSounds() != null) cell(v, "target_sounds", String.join("|", s.getTargetSounds()));
                        put(result, type, v);
                    } else if (s.getUnits() != null) {
                        for (PronUnit u : s.getUnits()) {
                            Map<String, String> v = new LinkedHashMap<>();
                            cell(v, "stage_index", s.getStageIndex()); cell(v, "unit_code", u.code());
                            cell(v, "audio_url", u.audioUrl()); cell(v, "prompt_vi", u.prompt());
                            if (u.options() != null) {
                                u.options().forEach(o -> cell(v, "option_" + o.key().toLowerCase(java.util.Locale.ROOT), o.text()));
                            }
                            cell(v, "answer", u.answerKey()); cell(v, "explanation_vi", u.explanation());
                            put(result, type, v);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void topicItemSnapshots(List<ValidatedRow> rows, Map<List<String>, Map<String, String>> result) {
        Map<Long, Topic> topicById = topics.findByCodeIn(values(rows, "topic_code")).stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));
        Map<Long, Lexeme> lexemeById = new HashMap<>();
        for (LexemeKind kind : List.of(LexemeKind.CHAR, LexemeKind.WORD)) {
            List<String> texts = rows.stream().map(ValidatedRow::values).filter(v -> kind.name().equals(v.get("kind")))
                    .map(v -> v.get("text")).distinct().toList();
            if (!texts.isEmpty()) lexemes.findByKindAndTextIn(kind, texts).forEach(l -> lexemeById.put(l.getId(), l));
        }
        if (topicById.isEmpty() || lexemeById.isEmpty()) return;
        for (TopicItem item : topicItems.findByTopicIdInAndLexemeIdIn(topicById.keySet(), lexemeById.keySet())) {
            Lexeme l = lexemeById.get(item.getLexemeId());
            Map<String, String> v = new LinkedHashMap<>();
            cell(v, "topic_code", topicById.get(item.getTopicId()).getCode());
            cell(v, "kind", l.getKind()); cell(v, "text", l.getText()); cell(v, "order_index", item.getOrderIndex());
            put(result, ImportDataType.TOPIC_ITEM, v);
        }
    }

    private static Map<String, String> lexeme(Lexeme l) {
        Map<String, String> v = new LinkedHashMap<>();
        cell(v, "kind", l.getKind()); cell(v, "text", l.getText()); cell(v, "pinyin", l.getPinyin());
        cell(v, "sino_viet", l.getSinoViet()); cell(v, "meaning_vi", l.getMeaningVi());
        cell(v, "title_vi", l.getMeaningVi()); cell(v, "hsk_level", l.getHskLevel());
        cell(v, "audio_url", l.getAudioUrl()); cell(v, "radical", l.getRadical()); cell(v, "stroke_count", l.getStrokeCount());
        LexemeExtra extra = l.getExtra();
        if (extra != null) {
            cell(v, "structure", extra.structure()); cell(v, "explanation_vi", extra.explanation());
            cell(v, "common_mistake_vi", extra.commonMistake());
            if (extra.tags() != null) cell(v, "tags", String.join("|", extra.tags()));
            if (extra.examples() != null) {
                for (int i = 0; i < extra.examples().size(); i++) {
                    LexemeExtra.Example e = extra.examples().get(i);
                    String prefix = "example" + (l.getKind() == LexemeKind.GRAMMAR ? i + 1 : "");
                    cell(v, prefix + "_cn", e.cn()); cell(v, prefix + "_pinyin", e.pinyin()); cell(v, prefix + "_vi", e.vi());
                    if (l.getKind() != LexemeKind.GRAMMAR) break;
                }
            }
        }
        return v;
    }

    private static Collection<String> values(List<ValidatedRow> rows, String column) {
        return rows.stream().map(r -> r.values().get(column)).distinct().toList();
    }

    private static void cell(Map<String, String> v, String column, Object value) {
        if (value != null) v.put(column, value.toString());
    }

    private static List<String> key(ImportDataType type, Map<String, String> values) {
        return ImportTemplate.forType(type).naturalKey().stream().map(values::get).toList();
    }

    private static void put(Map<List<String>, Map<String, String>> result, ImportDataType type, Map<String, String> v) {
        result.put(key(type, v), v);
    }
}
