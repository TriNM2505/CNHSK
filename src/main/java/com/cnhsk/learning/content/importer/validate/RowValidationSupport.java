package com.cnhsk.learning.content.importer.validate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.template.ColumnSpec;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.text.CjkUtils;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;

final class RowValidationSupport {

    private RowValidationSupport() {
    }

    static List<Context> rows(ImportTemplate template, CsvReader.ReadResult input) {
        return input.rows().stream().sorted(Comparator.comparingInt(CsvReader.Row::line))
                .map(row -> new Context(template, row)).toList();
    }

    static RowValidationResult finish(ImportTemplate template, CsvReader.ReadResult input, List<Context> rows) {
        List<RowError> errors = new ArrayList<>(input.errors());
        List<RowValidationResult.ValidatedRow> validRows = new ArrayList<>();
        Map<List<String>, Integer> firstValidLines = new HashMap<>();
        for (Context row : rows) {
            if (row.errors.isEmpty()) {
                List<String> key = template.naturalKey().stream().map(row::value).toList();
                Integer firstLine = firstValidLines.putIfAbsent(key, row.line);
                if (firstLine != null) {
                    row.error(null, "DUPLICATE_IN_FILE", "Trùng khoá với dòng " + firstLine);
                }
            }
            if (row.errors.isEmpty()) {
                String pinyinNorm = null;
                String meaningNorm = null;
                if (template.dataType() == ImportDataType.LEXEME) {
                    pinyinNorm = PinyinUtils.normalize(row.value("pinyin"));
                    meaningNorm = TextNormalizer.normalizeMeaning(row.value("meaning_vi"));
                }
                if (template.dataType() == ImportDataType.GRAMMAR) {
                    meaningNorm = TextNormalizer.normalizeMeaning(row.value("title_vi"));
                }
                validRows.add(new RowValidationResult.ValidatedRow(row.line, row.values, pinyinNorm, meaningNorm));
            }
            errors.addAll(row.errors.values());
        }
        Map<String, Integer> columnOrder = new HashMap<>();
        for (int index = 0; index < template.columns().size(); index++) {
            columnOrder.put(template.columns().get(index).name(), index);
        }
        errors.sort(Comparator.comparingInt(RowError::line)
                .thenComparingInt(error -> columnOrder.getOrDefault(error.column(), Integer.MAX_VALUE)));
        return new RowValidationResult(input.totalRows(), validRows, errors);
    }

    static String limitedValue(String value) {
        if (value == null || length(value) <= 100) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, 100));
    }

    static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    static final class Context {

        private final ImportTemplate template;
        private final int line;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, RowError> errors = new LinkedHashMap<>();

        private Context(ImportTemplate template, CsvReader.Row row) {
            this.template = template;
            this.line = row.line();
            for (ColumnSpec column : template.columns()) {
                if (row.values().containsKey(column.name())) {
                    values.put(column.name(), TextNormalizer.normalizeCell(column.name(), row.values().get(column.name())));
                }
            }
        }

        String value(String column) {
            return values.get(column);
        }

        void value(String column, String value) {
            values.put(column, value);
        }

        boolean present(String column) {
            String value = value(column);
            return value != null && !value.isEmpty();
        }

        boolean eligible(String column) {
            return present(column) && !errors.containsKey(column);
        }

        boolean valid() {
            return errors.isEmpty();
        }

        void canonicalInteger(String column) {
            if (eligible(column)) {
                value(column, Integer.toString(Integer.parseInt(value(column))));
            }
        }

        void containsCjk(String column) {
            if (eligible(column) && value(column).codePoints().noneMatch(CjkUtils::isCjk)) {
                error(column, "NOT_CJK", "Cột " + column + " phải là chữ Hán");
            }
        }

        void answer() {
            String message = "Đáp án phải là A–D, trỏ tới phương án có nội dung; các phương án không được trùng";
            if (eligible("answer") && (!Set.of("A", "B", "C", "D").contains(value("answer"))
                    || !present("option_" + value("answer").toLowerCase(java.util.Locale.ROOT)))) {
                error("answer", "INVALID_ANSWER", message);
            }
            Set<String> options = new HashSet<>();
            for (String column : List.of("option_a", "option_b", "option_c", "option_d")) {
                if (present(column) && !options.add(value(column))) {
                    error("answer", "INVALID_ANSWER", message);
                }
            }
        }

        void requiredAndBroken(Set<String> cjkColumns) {
            for (ColumnSpec column : template.columns()) {
                if (column.required() && !present(column.name())) {
                    error(column.name(), "REQUIRED_MISSING", "Thiếu giá trị bắt buộc ở cột " + column.name());
                } else if (CjkUtils.isBroken(value(column.name()), cjkColumns.contains(column.name()))) {
                    error(column.name(), "BROKEN_TEXT",
                            "Chữ Hán bị hỏng (có ? hoặc ký tự lạ) — kiểm tra file đã lưu UTF-8 chưa");
                }
            }
        }

        void lengths() {
            for (ColumnSpec column : template.columns()) {
                if (column.maxLength() != null && eligible(column.name())
                        && length(value(column.name())) > column.maxLength()) {
                    error(column.name(), "TOO_LONG",
                            "Cột " + column.name() + " dài quá " + column.maxLength() + " ký tự");
                }
            }
        }

        void require(String column) {
            if (!present(column)) {
                error(column, "REQUIRED_MISSING", "Thiếu giá trị bắt buộc ở cột " + column);
            }
        }

        void kind() {
            if (eligible("kind") && !Set.of("CHAR", "WORD").contains(value("kind"))) {
                invalid("kind", "chỉ nhận CHAR hoặc WORD");
            }
        }

        void integer(String column, int minimum, int maximum, boolean hskLevel) {
            if (!eligible(column)) {
                return;
            }
            try {
                int number = Integer.parseInt(value(column));
                if (number >= minimum && number <= maximum) {
                    return;
                }
            } catch (NumberFormatException exception) {
                // The same contract error covers malformed, overflowing and out-of-range numbers.
            }
            if (hskLevel) {
                error(column, "INVALID_HSK_LEVEL", "Cấp HSK phải từ 1 đến 9");
            } else {
                invalid(column, "phải là số nguyên từ " + minimum + " đến " + maximum);
            }
        }

        void matching(String column, Pattern pattern, String reason) {
            if (eligible(column) && !pattern.matcher(value(column)).matches()) {
                invalid(column, reason);
            }
        }

        void url(String column) {
            if (!eligible(column)) {
                return;
            }
            String value = value(column);
            if ((!value.startsWith("http://") && !value.startsWith("https://"))
                    || value.codePoints().anyMatch(character -> Character.isWhitespace(character)
                            || Character.isSpaceChar(character))) {
                error(column, "INVALID_URL", "Đường dẫn phải bắt đầu bằng http:// hoặc https://");
            }
        }

        void invalid(String column, String reason) {
            error(column, "INVALID_VALUE", "Giá trị không hợp lệ ở cột " + column + ": " + reason);
        }

        void error(String column, String code, String message) {
            errors.putIfAbsent(column, new RowError(line, column, limitedValue(value(column)), code, message));
        }
    }
}
