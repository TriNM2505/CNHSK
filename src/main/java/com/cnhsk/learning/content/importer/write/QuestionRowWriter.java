package com.cnhsk.learning.content.importer.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.QuestionImportSupport;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.QuestionRules;
import com.cnhsk.learning.knowledge.QuestionRules.Draft;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QuestionRowWriter implements RowWriter {
    private final QuestionRepository questions;
    private final QuestionKnowledgePointRepository labels;
    private final KnowledgePointRepository points;
    private final EntityManager entities;

    public QuestionRowWriter(QuestionRepository questions, QuestionKnowledgePointRepository labels,
            KnowledgePointRepository points, EntityManager entities) {
        this.questions = questions;
        this.labels = labels;
        this.points = points;
        this.entities = entities;
    }

    @Override public ImportDataType dataType() { return ImportDataType.QUESTION; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Question previous = questions.findByCodeForUpdate(row.values().get("code")).orElse(null);
        Map<String, Long> ids = points.findByCodeIn(QuestionImportSupport.references(row.values())).stream()
                .collect(Collectors.toMap(point -> point.getCode(), point -> point.getId()));
        var parsed = QuestionImportSupport.parse(row.values(), previous);
        var checked = QuestionRules.validate(parsed.value(), ids.keySet());
        var errors = new ArrayList<>(parsed.errors()); errors.addAll(checked.errors());
        if (!errors.isEmpty()) {
            var error = errors.get(0);
            throw new RowWriteRejectedException(error.code(), QuestionImportSupport.column(error), error.message());
        }
        Draft value = checked.value();
        if (previous != null && !QuestionRules.gradingSignature(QuestionImportSupport.stored(previous, "", List.of()))
                .equals(QuestionRules.gradingSignature(value))
                && !questions.usedQuestionIds(List.of(previous.getId())).isEmpty()) {
            throw new RowWriteRejectedException("GRADING_CHANGE_NOT_ALLOWED", "correct",
                    "Câu đã có người làm — không đổi được cách chấm; tạo câu mới");
        }
        List<QuestionKnowledgePoint> oldLabels = previous == null ? List.of() : labels.findByQuestionIdIn(List.of(previous.getId()));
        Map<Long, KnowledgeLabelRole> desired = new java.util.HashMap<>();
        desired.put(ids.get(value.primaryKid()), KnowledgeLabelRole.PRIMARY);
        value.secondaryKids().forEach(kid -> desired.put(ids.get(kid), KnowledgeLabelRole.SECONDARY));
        Map<Long, KnowledgeLabelRole> old = oldLabels.stream().collect(Collectors.toMap(
                QuestionKnowledgePoint::getKnowledgePointId, QuestionKnowledgePoint::getRole));
        if (previous != null && old.equals(desired) && QuestionImportSupport.sameQuestion(previous, value)) return RowAction.UNCHANGED;
        Question question = previous == null ? new Question(row.values().get("code"), value.qtype(), (short) value.hskLevel(), value.stem()) : previous;
        if (previous == null) {
            question.setSource(QuestionSource.IMPORT); question.setStatus(QuestionStatus.APPROVED); question.setReusable(true);
        }
        apply(question, value);
        // IDENTITY inserts a new question immediately; existing questions are already managed under the row lock.
        if (previous == null) questions.save(question);
        if (!old.equals(desired)) {
            // Free uq_qkp_primary before inserting a new primary, including a previous secondary with the same composite ID.
            if (!oldLabels.isEmpty()) { labels.deleteAll(oldLabels); labels.flush(); }
            // These labels are new: persist avoids merge's existence SELECT for each assigned composite ID.
            desired.forEach((pointId, role) -> entities.persist(new QuestionKnowledgePoint(question.getId(), pointId, role)));
        }
        return previous == null ? RowAction.CREATE : RowAction.UPDATE;
    }

    private static void apply(Question question, Draft value) {
        question.setQtype(value.qtype()); question.setHskLevel((short) value.hskLevel()); question.setDifficulty((short) value.difficulty());
        question.setStem(value.stem()); question.setExplanation(value.explanation());
        question.setCorrectKey(value.correctKey()); question.setAudioUrl(value.audioUrl()); question.setImageUrl(value.imageUrl());
        question.setOptions(value.options().stream().map(option -> new QuestionOption(option.key(), option.text(), option.isCorrect(),
                option.errorKid(), option.errorNote())).toList());
    }
}
