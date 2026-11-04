package com.cnhsk.learning.content.importer.validate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.QuestionImportSupport;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.knowledge.QuestionRules;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import org.springframework.stereotype.Component;

@Component
public class QuestionRowValidator implements RowValidator {
    private static final int BATCH_SIZE = 500;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,50}$");
    private final KnowledgePointRepository points;
    private final QuestionRepository questions;

    public QuestionRowValidator(KnowledgePointRepository points, QuestionRepository questions) {
        this.points = points;
        this.questions = questions;
    }

    @Override public ImportDataType dataType() { return ImportDataType.QUESTION; }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        var canonical = new CsvReader.ReadResult(input.totalRows(), input.rows().stream()
                .sorted(Comparator.comparingInt(CsvReader.Row::line))
                .map(row -> new CsvReader.Row(row.line(), QuestionImportSupport.cells(row.values()))).toList(), input.errors());
        List<Context> rows = RowValidationSupport.rows(template, canonical);
        List<Map<String, String>> values = new ArrayList<>();
        Set<String> referencedKids = new HashSet<>();
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            row.matching("code", CODE, "phải khớp ^[A-Z0-9_-]{3,50}$");
            row.integer("hsk_level", 1, 9, true);
            row.integer("difficulty", 1, 3, false);
            row.canonicalInteger("hsk_level"); row.canonicalInteger("difficulty");
            row.url("audio_url"); row.url("image_url");
            Map<String, String> cells = new LinkedHashMap<>();
            template.columns().forEach(column -> { if (row.value(column.name()) != null) cells.put(column.name(), row.value(column.name())); });
            values.add(cells);
            referencedKids.addAll(QuestionImportSupport.references(cells));
        }
        Set<String> existingKids = referencedKids.isEmpty() ? Set.of() : points.findByCodeIn(referencedKids).stream()
                .map(point -> point.getCode()).collect(java.util.stream.Collectors.toSet());
        Map<String, Question> existing = new HashMap<>();
        Set<Long> used = new HashSet<>();
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            List<String> codes = values.subList(offset, Math.min(values.size(), offset + BATCH_SIZE)).stream()
                    .map(row -> row.get("code")).filter(code -> code != null && CODE.matcher(code).matches()).distinct().toList();
            if (codes.isEmpty()) continue;
            List<Question> batch = questions.findByCodeIn(codes);
            batch.forEach(question -> existing.put(question.getCode(), question));
            if (!batch.isEmpty()) used.addAll(questions.usedQuestionIds(batch.stream().map(Question::getId).toList()));
        }
        for (int index = 0; index < rows.size(); index++) {
            Context row = rows.get(index);
            if (!row.valid()) continue;
            Question previous = existing.get(row.value("code"));
            var parsed = QuestionImportSupport.parse(values.get(index), previous);
            var checked = QuestionRules.validate(parsed.value(), existingKids);
            parsed.errors().forEach(error -> row.error(QuestionImportSupport.column(error), error.code(), error.message()));
            checked.errors().forEach(error -> row.error(QuestionImportSupport.column(error), error.code(), error.message()));
            if (row.valid() && previous != null && used.contains(previous.getId())
                    && !QuestionRules.gradingSignature(QuestionImportSupport.stored(previous, "", List.of()))
                            .equals(QuestionRules.gradingSignature(checked.value()))) {
                row.error("correct", "GRADING_CHANGE_NOT_ALLOWED", "Câu đã có người làm — không đổi được cách chấm; tạo câu mới");
            }
        }
        return RowValidationSupport.finish(template, canonical, rows);
    }
}
