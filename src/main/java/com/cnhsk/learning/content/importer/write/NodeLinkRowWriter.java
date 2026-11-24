package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.NodeLinkRowValidator;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.NodeLinkRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NodeLinkRowWriter implements RowWriter {
    private final NodeLinkRepository targets;
    private final KnowledgePointRepository points;

    public NodeLinkRowWriter(NodeLinkRepository targets, KnowledgePointRepository points) {
        this.targets = targets;
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.NODE_LINK; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        String type = row.values().get("target_type");
        String key = row.values().get("target_key");
        var target = (switch (type) {
            case "TOPIC" -> targets.lockTopic(key);
            case "PRON_UNIT" -> targets.lockPronUnit(key);
            default -> targets.lockLexeme(LexemeKind.valueOf(type), key);
        }).orElseThrow(() -> new RowWriteRejectedException("REFERENCE_NOT_FOUND", "target_key",
                "Không tìm thấy nội dung — nhập mẫu nội dung tương ứng trước"));
        var point = points.findByCode(row.values().get("kid"))
                .orElseThrow(() -> new RowWriteRejectedException("REFERENCE_NOT_FOUND", "kid",
                        "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước"));
        String domain = NodeLinkRowValidator.domainOf(type);
        if (!point.getDomain().name().equals(domain)) {
            throw new RowWriteRejectedException("DOMAIN_MISMATCH", "kid",
                    "Nội dung loại " + type + " chỉ gắn được vào nút miền " + domain);
        }
        if (type.equals("PRON_UNIT")) {
            // Pronunciation units live in a JSON array, so the link is the node code stored on the unit.
            if (point.getCode().equals(target.kid())) return RowAction.UNCHANGED;
            targets.updatePronUnit(target.id(), key.substring(key.indexOf(':') + 1), point.getCode());
            return RowAction.UPDATE;
        }
        if (point.getId().equals(target.pointId())) return RowAction.UNCHANGED;
        if (type.equals("TOPIC")) targets.updateTopic(target.id(), point.getId());
        else targets.updateLexeme(target.id(), point.getId());
        return RowAction.UPDATE;
    }
}
