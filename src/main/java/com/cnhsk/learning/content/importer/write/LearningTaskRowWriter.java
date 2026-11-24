package com.cnhsk.learning.content.importer.write;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.LearningTask;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.LearningTaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A task row replaces the whole task, including its targets; optional blank cells clear the optional fields. */
@Component
public class LearningTaskRowWriter implements RowWriter {
    private final LearningTaskRepository tasks;
    private final KnowledgePointRepository points;

    public LearningTaskRowWriter(LearningTaskRepository tasks, KnowledgePointRepository points) {
        this.tasks = tasks;
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.LEARNING_TASK; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Map<String, String> values = row.values();
        LearningTask existing = tasks.findByCodeForUpdate(values.get("code")).orElse(null);
        Map<String, String> before = existing == null ? null : existing.toCsv();
        LearningTask task = existing == null ? new LearningTask(values.get("code")) : existing;
        task.setKind(LearningTask.Kind.valueOf(values.get("kind")));
        task.setHskLevel(Short.parseShort(values.get("hsk_level")));
        task.setNlKid(node(values.get("nl_kid"), "nl_kid"));
        task.setTitleVi(values.get("title_vi"));
        task.setPromptVi(values.get("prompt_vi"));
        task.setPromptZh(blankToNull(values.get("prompt_zh")));
        task.setMinChars(shortOrNull(values.get("min_chars")));
        task.setMaxSeconds(shortOrNull(values.get("max_seconds")));
        task.setActive(!"FALSE".equals(values.get("is_active")));
        List<LearningTask.Target> targets = new ArrayList<>();
        for (String part : values.get("targets").split("\\|")) {
            String[] pieces = part.split(":", 2);
            targets.add(new LearningTask.Target(node(pieces[1], "targets"), pieces[0]));
        }
        if (existing != null && before.equals(withTargets(task, targets))) {
            return RowAction.UNCHANGED;
        }
        task.setTargets(targets);
        tasks.saveAndFlush(task);
        return existing == null ? RowAction.CREATE : RowAction.UPDATE;
    }

    private static Map<String, String> withTargets(LearningTask task, List<LearningTask.Target> targets) {
        List<LearningTask.Target> previous = task.getTargets();
        task.setTargets(targets);
        Map<String, String> csv = task.toCsv();
        task.setTargets(previous);
        return csv;
    }

    private KnowledgePoint node(String kid, String column) {
        return points.findByCode(kid).orElseThrow(() -> new RowWriteRejectedException("REFERENCE_NOT_FOUND", column,
                "Nút " + kid + " chưa tồn tại — nhập mẫu Nút cây năng lực trước"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Short shortOrNull(String value) {
        return value == null || value.isBlank() ? null : Short.valueOf(value);
    }
}
