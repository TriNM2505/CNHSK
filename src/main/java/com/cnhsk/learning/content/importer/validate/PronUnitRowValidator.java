package com.cnhsk.learning.content.importer.validate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.repository.PronStageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PronUnitRowValidator implements RowValidator {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,30}$");

    private final PronStageRepository stages;

    public PronUnitRowValidator(PronStageRepository stages) {
        this.stages = stages;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.PRON_UNIT;
    }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            row.matching("unit_code", CODE, "mã phải theo mẫu ^[A-Za-z0-9_-]{1,30}$");
            row.url("audio_url");
            row.answer();
            canonicalStageIndex(row);
        }
        Set<Short> indexes = rows.stream().filter(row -> row.eligible("stage_index"))
                .map(row -> Short.valueOf(row.value("stage_index")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Short, PronStage> existing = indexes.isEmpty() ? Map.of()
                : stages.findByStageIndexIn(indexes).stream()
                        .collect(Collectors.toMap(PronStage::getStageIndex, stage -> stage));
        for (Context row : rows) {
            if (row.eligible("stage_index") && !existing.containsKey(Short.valueOf(row.value("stage_index")))) {
                row.error("stage_index", "REFERENCE_NOT_FOUND", "Không tìm thấy tầng phát âm '"
                        + RowValidationSupport.limitedValue(row.value("stage_index"))
                        + "' — nhập mẫu Tầng phát âm trước");
            }
        }
        validateCapacity(rows, existing);
        return RowValidationSupport.finish(template, input, rows);
    }

    private static void canonicalStageIndex(Context row) {
        if (row.eligible("stage_index")) {
            try {
                row.value("stage_index", Short.toString(Short.parseShort(row.value("stage_index"))));
            } catch (NumberFormatException exception) {
                row.invalid("stage_index", "chỉ số tầng phải là số nguyên hợp lệ");
            }
        }
    }

    private static void validateCapacity(List<Context> rows, Map<Short, PronStage> stages) {
        Map<Short, Set<String>> existingCodes = new HashMap<>();
        Map<Short, Integer> counts = new HashMap<>();
        stages.forEach((index, stage) -> {
            existingCodes.put(index, stage.getUnits().stream().map(PronUnit::code).collect(Collectors.toSet()));
            counts.put(index, stage.getUnits().size());
        });
        Map<Short, Set<String>> acceptedCodes = new HashMap<>();
        for (Context row : rows) {
            if (!row.valid()) {
                continue;
            }
            Short index = Short.valueOf(row.value("stage_index"));
            String code = row.value("unit_code");
            Set<String> accepted = acceptedCodes.computeIfAbsent(index, ignored -> new HashSet<>());
            if (accepted.contains(code)) {
                // finish() reports the duplicate using the first fully valid physical line.
                continue;
            }
            boolean createsUnit = !existingCodes.get(index).contains(code);
            if (createsUnit && counts.get(index) >= 100) {
                row.invalid("unit_code", "mỗi tầng chỉ có tối đa 100 đơn vị phát âm");
                continue;
            }
            accepted.add(code);
            if (createsUnit) {
                counts.put(index, counts.get(index) + 1);
            }
        }
    }
}
