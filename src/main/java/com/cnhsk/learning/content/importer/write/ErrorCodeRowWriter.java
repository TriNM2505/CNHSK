package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.entity.ErrorCode;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.ErrorCodeRepository;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ErrorCodeRowWriter implements RowWriter {
    private final ErrorCodeRepository codes;
    private final KnowledgePointRepository points;

    public ErrorCodeRowWriter(ErrorCodeRepository codes, KnowledgePointRepository points) {
        this.codes = codes;
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.ERROR_CODE; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        String kidCode = row.values().get("kid");
        KnowledgePoint kid = kidCode == null || kidCode.isBlank() ? null : points.findByCode(kidCode)
                .orElseThrow(() -> new RowWriteRejectedException("REFERENCE_NOT_FOUND", "kid",
                        "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước"));
        short severity = Short.parseShort(row.values().get("severity"));
        ErrorCode existing = codes.findByCodeForUpdate(row.values().get("code")).orElse(null);
        ErrorCode code = existing == null
                ? new ErrorCode(row.values().get("code"), kid, severity, row.values().get("name_vi")) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        if (existing != null) {
            Long before = existing.getKid() == null ? null : existing.getKid().getId();
            Long after = kid == null ? null : kid.getId();
            if (!java.util.Objects.equals(before, after)) changes.set(null, kid, code::setKid);
            changes.set(existing.getSeverity(), severity, code::setSeverity);
        }
        changes.text(row, "name_vi", code.getNameVi(), code::setNameVi);
        changes.text(row, "example", code.getExample(), code::setExample);
        changes.text(row, "fix_hint", code.getFixHint(), code::setFixHint);
        changes.text(row, "description", code.getDescription(), code::setDescription);
        if (existing == null || changes.changed()) {
            codes.saveAndFlush(code);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
