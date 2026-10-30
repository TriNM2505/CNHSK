package com.cnhsk.learning.content.importer;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.importer.template.ColumnSpec;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.shared.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CsvReader {

    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 5000;

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false)
            .setLenientEof(false)
            .setTrailingData(false)
            .build();

    private final ImportProperties properties;

    public CsvReader() {
        this(new ImportProperties());
    }

    @Autowired
    public CsvReader(ImportProperties properties) {
        this.properties = properties;
    }

    public ReadResult read(ImportDataType dataType, String fileName, byte[] content) {
        Objects.requireNonNull(dataType, "dataType must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (!hasCsvExtension(fileName)) {
            throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }
        if (content.length > properties.getMaxFileSize().toBytes()) {
            throw new ImportFileException(ErrorCode.FILE_TOO_LARGE);
        }

        String decoded = decode(content);
        try (CSVParser parser = FORMAT.parse(new StringReader(decoded))) {
            Iterator<CSVRecord> records = parser.iterator();
            // Validate raw header cells before any name-based mapping can hide duplicate columns.
            List<String> headers = records.hasNext()
                    ? records.next().stream().map(CsvReader::trimHeader).toList()
                    : List.of();
            validateHeaders(headers, ImportTemplate.forType(dataType));
            return readRows(records, decoded, headers);
        } catch (IOException | UncheckedIOException | IllegalStateException exception) {
            throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }
    }

    private static String decode(byte[] content) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
        } catch (CharacterCodingException exception) {
            throw new ImportFileException(ErrorCode.ENCODING_ERROR);
        }
    }

    static boolean hasCsvExtension(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static String trimHeader(String header) {
        int start = 0;
        int end = header.length();
        while (start < end && isWhitespace(header.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(header.charAt(end - 1))) {
            end--;
        }
        return header.substring(start, end);
    }

    private static boolean isWhitespace(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    private static void validateHeaders(List<String> headers, ImportTemplate template) {
        Set<String> expected = template.columns().stream().map(ColumnSpec::name).collect(Collectors.toSet());
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        headers.forEach(name -> occurrences.merge(name, 1, Integer::sum));
        List<Map<String, String>> details = new ArrayList<>();
        for (ColumnSpec column : template.columns()) {
            if (column.required() && !occurrences.containsKey(column.name())) {
                details.add(Map.of("field", column.name(), "message", "Thiếu cột bắt buộc: " + column.name()));
            }
        }
        occurrences.forEach((name, count) -> {
            if (!expected.contains(name)) {
                details.add(Map.of("field", name, "message", "Cột không có trong mẫu: " + name));
            }
            if (count > 1) {
                details.add(Map.of("field", name, "message", "Tên cột bị trùng sau khi bỏ khoảng trắng: " + name));
            }
        });
        if (!details.isEmpty()) {
            throw new ImportFileException(ErrorCode.TEMPLATE_MISMATCH, details);
        }
    }

    private ReadResult readRows(Iterator<CSVRecord> records, String decoded, List<String> headers) {
        List<Row> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int totalRows = 0;
        int physicalLine = 1;
        int scannedOffset = 0;
        while (records.hasNext()) {
            CSVRecord record = records.next();
            int recordStart = Math.toIntExact(record.getCharacterPosition());
            // Scan each character at most once; CRLF is one physical line break.
            while (scannedOffset < recordStart) {
                char character = decoded.charAt(scannedOffset);
                if (character == '\r' || (character == '\n'
                        && (scannedOffset == 0 || decoded.charAt(scannedOffset - 1) != '\r'))) {
                    physicalLine++;
                }
                scannedOffset++;
            }
            if (record.stream().allMatch(value -> value.codePoints().allMatch(CsvReader::isWhitespace))) {
                continue;
            }
            totalRows++;
            if (totalRows > properties.getMaxRows()) {
                throw new ImportFileException(ErrorCode.TOO_MANY_ROWS);
            }
            if (record.size() != headers.size()) {
                errors.add(new RowError(physicalLine, null, null, "INVALID_VALUE",
                        "Dòng có " + record.size() + " ô, mẫu cần " + headers.size()
                                + " ô — nếu nội dung có dấu phẩy, đặt ô đó trong ngoặc kép"));
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                values.put(headers.get(index), record.get(index));
            }
            rows.add(new Row(physicalLine, values));
        }
        if (totalRows == 0) {
            throw new ImportFileException(ErrorCode.EMPTY_FILE);
        }
        return new ReadResult(totalRows, rows, errors);
    }

    /** Rows are structurally readable; T-06/T-07 validate and normalize their cell values. */
    public record ReadResult(int totalRows, List<Row> rows, List<RowError> errors) {
        public ReadResult {
            rows = List.copyOf(rows);
            errors = List.copyOf(errors);
        }
    }

    public record Row(int line, Map<String, String> values) {
        public Row {
            values = Map.copyOf(values);
        }
    }

    public record RowError(int line, String column, String value, String code, String message) {
    }
}
