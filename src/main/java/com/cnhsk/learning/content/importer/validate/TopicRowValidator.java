package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import org.springframework.stereotype.Component;

@Component
public class TopicRowValidator implements RowValidator {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{2,50}$");

    @Override
    public ImportDataType dataType() {
        return ImportDataType.TOPIC;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            row.matching("code", CODE, "mã phải theo mẫu ^[A-Z0-9_-]{2,50}$");
            row.integer("hsk_level", 1, 9, true);
            row.integer("order_index", 0, 100000, false);
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
