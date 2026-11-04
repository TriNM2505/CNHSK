package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeNodeRowValidator implements RowValidator {
    private static final Pattern KID = Pattern.compile("^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$");

    @Override
    public ImportDataType dataType() {
        return ImportDataType.KNOWLEDGE_NODE;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("name_zh"));
            row.lengths();
            if (row.eligible("kid")) {
                row.value("kid", row.value("kid").toUpperCase(Locale.ROOT));
                if (!KID.matcher(row.value("kid")).matches()) {
                    row.error("kid", "INVALID_KID", "Mã nút phải dạng miền + cấp + số, ví dụ NP3-004");
                }
            }
            row.containsCjk("name_zh");
            if (row.eligible("skill_type")) {
                try {
                    KnowledgeSkillType.valueOf(row.value("skill_type"));
                } catch (IllegalArgumentException exception) {
                    row.invalid("skill_type", "chỉ nhận LISTENING, READING, WRITING, VOCAB, GRAMMAR hoặc CHARACTER");
                }
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
