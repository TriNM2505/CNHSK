package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.CsvReader.RowError;

public record RowValidationResult(int totalRows, List<ValidatedRow> validRows, List<RowError> errors) {

    public RowValidationResult {
        validRows = List.copyOf(validRows);
        errors = List.copyOf(errors);
    }

    public record ValidatedRow(int line, Map<String, String> values, String pinyinNorm, String meaningNorm) {
        public ValidatedRow {
            values = Map.copyOf(values);
        }
    }
}
