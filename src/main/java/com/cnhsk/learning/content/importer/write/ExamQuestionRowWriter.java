package com.cnhsk.learning.content.importer.write;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.ExamImportSupport;
import com.cnhsk.learning.content.importer.ExamImportSupport.Conflict;
import com.cnhsk.learning.content.importer.ExamImportSupport.Slot;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamQuestionRowWriter implements RowWriter {
    private final NamedParameterJdbcTemplate jdbc;
    private final ExamImportSupport exams;
    private final QuestionRepository questions;

    public ExamQuestionRowWriter(NamedParameterJdbcTemplate jdbc, ExamImportSupport exams, QuestionRepository questions) {
        this.jdbc = jdbc;
        this.exams = exams;
        this.questions = questions;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.EXAM_QUESTION;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        var values = row.values();
        String code = values.get("exam_code");
        String questionCode = values.get("question_code");
        Slot slot = Slot.from(values);
        exams.lock(code);
        var exam = exams.exams(List.of(code), true).stream().findFirst().orElse(null);
        var question = exam == null ? null : questions.findByCodeForUpdate(questionCode).orElse(null);
        exams.questionConflict(exam, question, slot).ifPresent(error -> { throw rejected(error); });
        var links = exams.slots(List.of(code)).getOrDefault(code, Map.of());
        if (ExamImportSupport.occupied(links, questionCode, slot)) throw rejected(ExamImportSupport.duplicateOrder());
        Slot old = links.get(questionCode);
        if (old == null && links.size() >= 200) throw rejected(ExamImportSupport.tooManyQuestions());
        if (slot.equals(old)) return RowAction.UNCHANGED;

        jdbc.update("""
                INSERT INTO learning.exam_questions (exam_id, question_id, section_index, order_index, points)
                SELECT id, :question, :section, :order, :points FROM learning.exams WHERE code = :exam
                ON CONFLICT (exam_id, question_id) DO UPDATE SET section_index = excluded.section_index,
                    order_index = excluded.order_index, points = excluded.points
                """, Map.of("exam", code, "question", question.getId(), "section", slot.section(),
                "order", slot.order(), "points", slot.points()));
        return old == null ? RowAction.CREATE : RowAction.UPDATE;
    }

    private static RowWriteRejectedException rejected(Conflict error) {
        return new RowWriteRejectedException(error.code(), error.field(), error.message());
    }
}
