package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Set;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import org.springframework.stereotype.Component;

@Component
public class GrammarRowValidator implements RowValidator {

    @Override
    public ImportDataType dataType() {
        return ImportDataType.GRAMMAR;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("text", "example1_cn", "example2_cn", "example3_cn"));
            for (int index = 1; index <= 3; index++) {
                if (row.present("example" + index + "_cn")) {
                    row.require("example" + index + "_vi");
                }
            }
            row.lengths();
            row.containsCjk("text");
            for (int index = 1; index <= 3; index++) {
                row.containsCjk("example" + index + "_cn");
            }
            row.integer("hsk_level", 1, 9, true);
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
