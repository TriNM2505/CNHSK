package com.cnhsk.learning.content.importer.validate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.springframework.stereotype.Component;

@Component
public class PronStageRowValidator implements RowValidator {

    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    @Override
    public ImportDataType dataType() {
        return ImportDataType.PRON_STAGE;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            row.integer("stage_index", 1, 8, false);
            row.canonicalInteger("stage_index");
            validateTargetSounds(row);
            validatePassPercent(row);
        }
        return RowValidationSupport.finish(template, input, rows);
    }

    private static void validateTargetSounds(Context row) {
        if (!row.eligible("target_sounds")) {
            return;
        }
        List<String> sounds = Arrays.stream(row.value("target_sounds").split("\\|", -1))
                .map(sound -> TextNormalizer.normalizeCell("target_sounds", sound))
                .filter(sound -> !sound.isEmpty()).toList();
        row.value("target_sounds", String.join("|", sounds));
        if (sounds.size() > 30 || sounds.stream().anyMatch(sound -> RowValidationSupport.length(sound) > 20)) {
            row.invalid("target_sounds", "tối đa 30 mục, mỗi mục tối đa 20 ký tự");
        }
    }

    private static void validatePassPercent(Context row) {
        if (!row.eligible("pass_percent")) {
            return;
        }
        try {
            BigDecimal percent = new BigDecimal(row.value("pass_percent"));
            if (percent.signum() >= 0 && percent.compareTo(MAX_PERCENT) <= 0 && percent.scale() <= 2) {
                row.value("pass_percent", percent.setScale(2).toPlainString());
                return;
            }
        } catch (NumberFormatException exception) {
            // Keep malformed numbers in the same field error as range and precision failures.
        }
        row.invalid("pass_percent", "phải từ 0 đến 100 và tối đa 2 chữ số thập phân");
    }
}
