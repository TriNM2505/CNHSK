package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.PronStageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PronStageRowWriter implements RowWriter {
    private final PronStageRepository stages;

    public PronStageRowWriter(PronStageRepository stages) {
        this.stages = stages;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.PRON_STAGE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Short index = Short.valueOf(row.values().get("stage_index"));
        PronStage existing = stages.findByStageIndex(index).orElse(null);
        PronStage stage = existing == null ? new PronStage(index, row.values().get("name")) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.text(row, "name", stage.getName(), stage::setName);
        changes.text(row, "description", stage.getDescription(), stage::setDescription);
        changes.set(stage.getTargetSounds(), RowWriteSupport.tokens(row, "target_sounds", stage.getTargetSounds()), stage::setTargetSounds);
        changes.decimal(row, "pass_percent", stage.getPassPercent(), stage::setPassPercent);
        if (existing == null || changes.changed()) {
            stages.saveAndFlush(stage);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
