package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;

/** Writes one server-validated row inside the caller's transaction. */
public interface RowWriter {
    ImportDataType dataType();

    RowAction write(ValidatedRow row);
}
