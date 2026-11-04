package com.cnhsk.learning.content.importer.validate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.ExamImportSupport;
import com.cnhsk.learning.content.importer.ExamImportSupport.Position;
import com.cnhsk.learning.content.importer.ExamImportSupport.Slot;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import org.springframework.stereotype.Component;

@Component
public class ExamQuestionRowValidator implements RowValidator {
    private final ExamImportSupport exams;
    private final QuestionRepository questions;

    public ExamQuestionRowValidator(ExamImportSupport exams, QuestionRepository questions) {
        this.exams = exams;
        this.questions = questions;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.EXAM_QUESTION;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        var template = ImportTemplate.forType(dataType());
        var rows = RowValidationSupport.rows(template, input);
        for (var row : rows) {
            row.requiredAndBroken(Set.of());
            row.integer("section_index", Short.MIN_VALUE, Short.MAX_VALUE, false);
            row.integer("order_index", 1, 999, false);
            row.integer("points", 1, 10, false);
            if (!row.present("points")) row.value("points", "1");
            for (String field : List.of("section_index", "order_index", "points")) row.canonicalInteger(field);
        }

        var examCodes = rows.stream().filter(RowValidationSupport.Context::valid)
                .map(row -> row.value("exam_code")).distinct().toList();
        var questionCodes = rows.stream().filter(RowValidationSupport.Context::valid)
                .map(row -> row.value("question_code")).distinct().toList();
        var byExam = exams.exams(examCodes, false).stream()
                .collect(Collectors.toMap(ExamImportSupport.Exam::code, Function.identity()));
        var byQuestion = questionCodes.isEmpty() ? Map.<String, Question>of()
                : questions.findByCodeIn(questionCodes).stream()
                        .collect(Collectors.toMap(Question::getCode, Function.identity()));
        var stored = exams.slots(examCodes);
        Map<String, Set<Position>> reserved = new HashMap<>();
        Map<String, Set<String>> accepted = new HashMap<>();
        Map<String, Integer> added = new HashMap<>();

        for (var row : rows) {
            if (!row.valid()) continue;
            String exam = row.value("exam_code");
            String question = row.value("question_code");
            var slot = new Slot(Integer.parseInt(row.value("section_index")), Integer.parseInt(row.value("order_index")),
                    Integer.parseInt(row.value("points")));
            var conflict = exams.questionConflict(byExam.get(exam), byQuestion.get(question), slot);
            if (conflict.isPresent()) {
                var error = conflict.get();
                row.error(error.field(), error.code(), error.message());
                continue;
            }
            Set<String> pairs = accepted.computeIfAbsent(exam, ignored -> new HashSet<>());
            if (pairs.contains(question)) {
                row.error(null, "DUPLICATE_IN_FILE", "Câu hỏi đã xuất hiện trong đề ở dòng phía trên.");
                continue;
            }
            var links = stored.getOrDefault(exam, Map.of());
            Set<Position> positions = reserved.computeIfAbsent(exam, ignored -> new HashSet<>());
            if (ExamImportSupport.occupied(links, question, slot) || positions.contains(slot.position())) {
                var error = ExamImportSupport.duplicateOrder();
                row.error(error.field(), error.code(), error.message());
                continue;
            }
            if (!links.containsKey(question)) {
                if (links.size() + added.getOrDefault(exam, 0) >= 200) {
                    var error = ExamImportSupport.tooManyQuestions();
                    row.error(error.field(), error.code(), error.message());
                    continue;
                }
                added.merge(exam, 1, Integer::sum);
            }
            pairs.add(question);
            positions.add(slot.position());
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
