package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeNodeRowWriter implements RowWriter {
    private final KnowledgePointRepository points;

    public KnowledgeNodeRowWriter(KnowledgePointRepository points) {
        this.points = points;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.KNOWLEDGE_NODE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        String kid = row.values().get("kid");
        KnowledgePoint existing = points.findByCodeForUpdate(kid).orElse(null);
        KnowledgePoint point = existing == null
                ? new KnowledgePoint(kid, row.values().get("name_vi"), KnowledgeDomain.valueOf(kid.substring(0, 2)),
                        row.values().get("name_zh"), Short.parseShort(kid.substring(2, 3)))
                : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.text(row, "name_vi", point.getTitle(), point::setTitle);
        changes.text(row, "name_zh", point.getNameZh(), point::setNameZh);
        changes.text(row, "can_do", point.getCanDo(), point::setCanDo);
        if (RowWriteSupport.present(row, "skill_type")) {
            changes.set(point.getSkillType(), KnowledgeSkillType.valueOf(row.values().get("skill_type")), point::setSkillType);
        }
        if (existing == null || changes.changed()) {
            points.saveAndFlush(point);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
