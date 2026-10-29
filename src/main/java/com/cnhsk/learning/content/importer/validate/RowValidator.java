package com.cnhsk.learning.content.importer.validate;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;

public interface RowValidator {

    ImportDataType dataType();

    RowValidationResult validate(CsvReader.ReadResult input);
}
