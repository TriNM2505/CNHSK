package com.cnhsk.learning.content.importer;

/** Neutralizes spreadsheet formulas without changing the stored import data. */
final class CsvCellSanitizer {
    private CsvCellSanitizer() { }

    static Object sanitize(Object value) {
        if (value instanceof String text && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return value;
    }
}
