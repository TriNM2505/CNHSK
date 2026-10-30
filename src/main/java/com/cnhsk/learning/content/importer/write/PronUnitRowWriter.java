package com.cnhsk.learning.content.importer.write;

import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.PronStageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PronUnitRowWriter implements RowWriter {
    private final PronStageRepository stages;

    public PronUnitRowWriter(PronStageRepository stages) {
        this.stages = stages;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.PRON_UNIT;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        PronStage stage = stages.findByStageIndex(Short.valueOf(row.values().get("stage_index")))
                .orElseThrow(() -> new IllegalStateException("Missing pronunciation stage import reference"));
        List<PronUnit> units = new ArrayList<>(stage.getUnits());
        String code = row.values().get("unit_code");
        PronUnit existing = units.stream().filter(u -> code.equals(u.code())).findFirst().orElse(null);
        PronUnit next = new PronUnit(code, row.values().get("audio_url"),
                RowWriteSupport.value(row, "prompt_vi", existing == null ? "Nghe và chọn đáp án đúng" : existing.prompt()),
                RowWriteSupport.options(row, existing == null ? null : existing.options(), PronUnit.Option::key, PronUnit.Option::new),
                row.values().get("answer"),
                RowWriteSupport.value(row, "explanation_vi", existing == null ? null : existing.explanation()));
        RowWriteSupport.validateAnswer(next.options(), next.answerKey(), PronUnit.Option::key, PronUnit.Option::text);
        if (next.equals(existing)) {
            return RowAction.UNCHANGED;
        }
        if (existing == null) {
            if (units.size() >= 100) {
                throw new IllegalStateException("Pronunciation stage import capacity exceeded");
            }
            units.add(next);
        } else {
            units.set(units.indexOf(existing), next);
        }
        stage.setUnits(units);
        stages.saveAndFlush(stage);
        return existing == null ? RowAction.CREATE : RowAction.UPDATE;
    }
}
