package com.cnhsk.learning.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.importer.template.ColumnSpec;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.shared.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class CsvReaderTest {

    private static final String TOPIC_HEADER = "code,name,hsk_level\r\n";
    private static final String TOPIC_ROW = "DAILY_LIFE,Sinh hoạt hằng ngày,1\r\n";
    private static final String LEXEME_HEADER = "kind,text,pinyin,meaning_vi,hsk_level\r\n";
    private static final String LEXEME_ROW = "WORD,我们,wǒmen,chúng ta,1\r\n";

    private final CsvReader reader = new CsvReader();

    @ParameterizedTest(name = "FR002 unsupported filename {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"data.xlsx", "data.txt", "data.csv.exe"})
    void FR002_rejectsFilesWithoutCsvExtension(String fileName) {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.TOPIC, fileName, utf8(TOPIC_HEADER + TOPIC_ROW)));

        assertFileError(exception, ErrorCode.UNSUPPORTED_FILE_FORMAT, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR002_extensionCheckPrecedesSizeAndEncoding() {
        byte[] content = new byte[CsvReader.MAX_FILE_BYTES + 1];
        content[0] = (byte) 0xFF;

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.TOPIC, "data.xlsx", content));

        assertFileError(exception, ErrorCode.UNSUPPORTED_FILE_FORMAT, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"data.csv", "data.CSV", "data.Csv"})
    void FR002_acceptsCsvExtensionWithoutCaseSensitivity(String fileName) {
        assertThat(reader.read(ImportDataType.TOPIC, fileName, utf8(TOPIC_HEADER + TOPIC_ROW)).totalRows()).isEqualTo(1);
    }

    @Test
    void FR003_fileAboveTwoMiBIsRejectedBeforeDecoding() {
        byte[] content = new byte[2 * 1024 * 1024 + 1];
        content[0] = (byte) 0xFF;

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.TOPIC, "data.csv", content));

        assertFileError(exception, ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void FR003_fileExactlyTwoMiBIsReadableBeforeRowValueValidation() {
        String prefix = TOPIC_HEADER + "DAILY_LIFE,";
        String suffix = ",1\r\n";
        int padding = 2 * 1024 * 1024 - utf8(prefix + suffix).length;
        byte[] content = utf8(prefix + "a".repeat(padding) + suffix);

        assertThat(content).hasSize(2 * 1024 * 1024);
        CsvReader.ReadResult result = reader.read(ImportDataType.TOPIC, "data.csv", content);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void FR004_AT003_windows1258WithChineseAndVietnameseIsRejected() {
        byte[] content = (LEXEME_HEADER + LEXEME_ROW).getBytes(Charset.forName("windows-1258"));

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.LEXEME, "data.csv", content));

        assertFileError(exception, ErrorCode.ENCODING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ParameterizedTest(name = "FR004 invalid UTF-8 {0}")
    @ValueSource(strings = {"c3", "80", "e028a1", "c0af", "eda080", "f4908080", "ff"})
    void FR004_invalidUtf8BytesAreNeverReplacedSilently(String hex) {
        byte[] content = HexFormat.of().parseHex(hex);

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.TOPIC, "data.csv", content));

        assertFileError(exception, ErrorCode.ENCODING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR004_encodingCheckPrecedesHeaderValidation() {
        byte[] content = utf8("wrong_header\r\nvalue");
        content[content.length - 1] = (byte) 0xFF;

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.TOPIC, "data.csv", content));

        assertFileError(exception, ErrorCode.ENCODING_ERROR, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR004_AT004_utf8BomIsRemovedAndChineseIsPreserved() {
        CsvReader.ReadResult result = reader.read(ImportDataType.LEXEME, "data.csv",
                utf8("\uFEFF" + LEXEME_HEADER + LEXEME_ROW));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.rows().get(0).line()).isEqualTo(2);
        assertThat(result.rows().get(0).values()).containsEntry("text", "我们")
                .containsEntry("pinyin", "wǒmen").containsEntry("meaning_vi", "chúng ta");
        assertThat(result.errors()).isEmpty();
    }

    @ParameterizedTest(name = "FR005 RFC 4180 line separator [{index}]")
    @ValueSource(strings = {"\r\n", "\n", "\r"})
    void FR005_AT005_quotedCommaNewlineAndEscapedQuoteKeepOriginalValues(String separator) {
        String csv = "code,name,hsk_level" + separator
                + "FIRST,\"Xin chào," + separator + "bạn \"\"mới\"\"\",1" + separator
                + "SECOND,Tiếng Trung,2";

        CsvReader.ReadResult result = readTopic(csv);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.rows()).extracting(CsvReader.Row::line).containsExactly(2, 4);
        assertThat(result.rows().get(0).values()).containsEntry("name", "Xin chào," + separator + "bạn \"mới\"");
        assertThat(result.rows().get(1).values()).containsEntry("name", "Tiếng Trung");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void FR005_cellWhitespaceIsPreservedForLaterNormalization() {
        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + "DAILY_LIFE,  Tên   chủ đề  ,1");

        assertThat(result.rows().get(0).values()).containsEntry("name", "  Tên   chủ đề  ");
    }

    @Test
    void FR005_extraCellsFailOnlyTheirRowAndKeepFollowingRows() {
        assertWidthError("BAD,Tên lỗi,1,ô thừa", 4);
    }

    @Test
    void FR005_missingCellsFailOnlyTheirRowAndKeepFollowingRows() {
        assertWidthError("BAD,Tên lỗi", 2);
    }

    @Test
    void FR005_widthErrorsUsePhysicalStartLinesAfterQuotedAndBlankLines() {
        String csv = TOPIC_HEADER
                + "FIRST,\"Có dấu phẩy,\r\nvà chữ Hán 我\",1\r\n"
                + "\r\n"
                + "BAD,\"Dòng lỗi\r\nvẫn thiếu ô\"\r\n"
                + "LAST,Dòng sau vẫn đọc,1\r\n";

        CsvReader.ReadResult result = readTopic(csv);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.rows()).extracting(CsvReader.Row::line).containsExactly(2, 7);
        assertThat(result.errors()).containsExactly(new CsvReader.RowError(5, null, null, "INVALID_VALUE",
                "Dòng có 2 ô, mẫu cần 3 ô — nếu nội dung có dấu phẩy, đặt ô đó trong ngoặc kép"));
    }

    @ParameterizedTest(name = "FR005 malformed quoted CSV [{index}]")
    @ValueSource(strings = {"BAD,\"Nội dung chưa đóng,1", "BAD,\"Nội dung\"sai,1"})
    void FR005_unclosedQuoteOrInvalidQuotedStructureRejectsWholeFile(String badRow) {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic(TOPIC_HEADER + TOPIC_ROW + badRow));

        assertFileError(exception, ErrorCode.UNSUPPORTED_FILE_FORMAT, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getMessage()).doesNotContain("Nội dung", "BAD", "IOException", "java.");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getExtra()).isEmpty();
    }

    @Test
    void FR006_AT006_missingPinyinAndUnexpectedNoteBothHaveDetails() {
        String csv = "kind,text,meaning_vi,hsk_level,note\r\nWORD,我,tôi,1,ghi chú\r\n";

        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> reader.read(ImportDataType.LEXEME, "data.csv", utf8(csv)));

        assertFileError(exception, ErrorCode.TEMPLATE_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getExtra()).containsOnlyKeys("details");
        assertThat(exception.getExtra().get("details")).isEqualTo(List.of(
                Map.of("field", "pinyin", "message", "Thiếu cột bắt buộc: pinyin"),
                Map.of("field", "note", "message", "Cột không có trong mẫu: note")));
    }

    @Test
    void FR006_headerMismatchPrecedesDataRowCount() {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic("code,name,extra\r\n"));

        assertFileError(exception, ErrorCode.TEMPLATE_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getExtra().get("details")).isEqualTo(List.of(
                Map.of("field", "hsk_level", "message", "Thiếu cột bắt buộc: hsk_level"),
                Map.of("field", "extra", "message", "Cột không có trong mẫu: extra")));
    }

    @Test
    void FR006_AT007_reorderedHeadersAreTrimmedAndMappedByExactName() {
        String csv = " \thsk_level\u00A0 ,\u3000pinyin\t,meaning_vi, text , kind \r\n"
                + "1,wǒmen,chúng ta,我们,WORD\r\n";

        CsvReader.ReadResult result = reader.read(ImportDataType.LEXEME, "data.csv", utf8(csv));

        assertThat(result.rows().get(0).values()).isEqualTo(Map.of(
                "kind", "WORD", "text", "我们", "pinyin", "wǒmen", "meaning_vi", "chúng ta", "hsk_level", "1"));
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void FR006_headerNamesRemainCaseSensitive() {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic("Code,name,hsk_level\r\n" + TOPIC_ROW));

        assertFileError(exception, ErrorCode.TEMPLATE_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getExtra().get("details")).isEqualTo(List.of(
                Map.of("field", "code", "message", "Thiếu cột bắt buộc: code"),
                Map.of("field", "Code", "message", "Cột không có trong mẫu: Code")));
    }

    @Test
    void FR006_duplicateNamesAfterTrimReportEveryDuplicatedColumn() {
        String csv = "code,name,hsk_level, name , code , name\r\nA,B,1,C,D,E\r\n";

        ImportFileException exception = assertThrows(ImportFileException.class, () -> readTopic(csv));

        assertFileError(exception, ErrorCode.TEMPLATE_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.getExtra().get("details")).isEqualTo(List.of(
                Map.of("field", "code", "message", "Tên cột bị trùng sau khi bỏ khoảng trắng: code"),
                Map.of("field", "name", "message", "Tên cột bị trùng sau khi bỏ khoảng trắng: name")));
    }

    @Test
    void FR006_missingUnexpectedAndDuplicateColumnsAreReportedTogether() {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic("code,name,extra, name \r\nA,B,C,D\r\n"));

        assertThat(exception.getExtra().get("details")).isEqualTo(List.of(
                Map.of("field", "hsk_level", "message", "Thiếu cột bắt buộc: hsk_level"),
                Map.of("field", "name", "message", "Tên cột bị trùng sau khi bỏ khoảng trắng: name"),
                Map.of("field", "extra", "message", "Cột không có trong mẫu: extra")));
    }

    @ParameterizedTest(name = "FR006 shared template {0}")
    @EnumSource(ImportDataType.class)
    void FR006_allSevenSharedTemplatesReadTheirExampleRow(ImportDataType dataType) throws IOException {
        ImportTemplate template = ImportTemplate.forType(dataType);
        StringWriter output = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.RFC4180)) {
            printer.printRecord(template.columns().stream().map(ColumnSpec::name).toList());
            printer.printRecord(template.columns().stream().map(ColumnSpec::example).toList());
        }

        CsvReader.ReadResult result = reader.read(dataType, "data.csv", utf8(output.toString()));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.rows().get(0).values()).isEqualTo(template.columns().stream()
                .collect(Collectors.toMap(ColumnSpec::name, ColumnSpec::example)));
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void FR006_optionalHeaderColumnsMayBeOmittedAndEmptyCellsRemainPresent() {
        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + "DAILY_LIFE,,1\r\n");

        assertThat(result.rows().get(0).values()).containsEntry("name", "");
        assertThat(result.rows().get(0).values()).doesNotContainKey("order_index");
        assertThat(result.errors()).isEmpty();
    }

    @ParameterizedTest(name = "FR007 empty data [{index}]")
    @ValueSource(strings = {"", "\r\n", "  \t \r\n", ",,\r\n", "\" \r\n \", , \r\n"})
    void FR007_AT008_headerWithoutNonblankDataIsEmptyFile(String blankRows) {
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic(TOPIC_HEADER + blankRows));

        assertFileError(exception, ErrorCode.EMPTY_FILE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR007_AT008_fiveThousandRowsAreAllowedAndFiveThousandOneAreRejected() {
        String data = IntStream.rangeClosed(1, 5000)
                .mapToObj(index -> "TOPIC_" + index + ",Chủ đề tự soạn,1\r\n").collect(Collectors.joining());

        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + data);

        assertThat(result.totalRows()).isEqualTo(5000);
        assertThat(result.rows()).hasSize(5000);
        assertThat(result.rows().get(4999).line()).isEqualTo(5001);
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic(TOPIC_HEADER + data + "EXTRA,Dòng thêm,1\r\n"));
        assertFileError(exception, ErrorCode.TOO_MANY_ROWS, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR007_blankRecordsDoNotCountButPhysicalLineNumbersIncludeThem() {
        String csv = "code,name,hsk_level\n\n \t \n,,\nFIRST,Tên thứ nhất,1\n"
                + "\" \n \",,\nSECOND,Tên thứ hai,1";

        CsvReader.ReadResult result = readTopic(csv);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.rows()).extracting(CsvReader.Row::line).containsExactly(5, 8);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void FR007_widthErrorsCountAsDataRowsEvenWhenNoRowIsReadable() {
        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + "BAD,Tên thiếu ô\r\n");

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void FR007_widthErrorsAreIncludedInTheFiveThousandRowLimit() {
        String data = "BAD,Tên thiếu ô\r\n".repeat(5000);

        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + data);

        assertThat(result.totalRows()).isEqualTo(5000);
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).hasSize(5000);
        ImportFileException exception = assertThrows(ImportFileException.class,
                () -> readTopic(TOPIC_HEADER + data + "BAD,Thêm một dòng\r\n"));
        assertFileError(exception, ErrorCode.TOO_MANY_ROWS, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR005_readResultsCannotBeChangedBeforeRowValidation() {
        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + TOPIC_ROW + "BAD,Thiếu ô\r\n");

        assertThatThrownBy(() -> result.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.rows().get(0).values().put("name", "Đã thay đổi"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertWidthError(String badRow, int cellCount) {
        CsvReader.ReadResult result = readTopic(TOPIC_HEADER + TOPIC_ROW + badRow + "\r\n"
                + "LAST,Dòng sau vẫn đọc,1\r\n");

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.rows()).extracting(CsvReader.Row::line).containsExactly(2, 4);
        assertThat(result.rows().get(1).values()).containsEntry("code", "LAST");
        assertThat(result.errors()).containsExactly(new CsvReader.RowError(3, null, null, "INVALID_VALUE",
                "Dòng có " + cellCount + " ô, mẫu cần 3 ô — nếu nội dung có dấu phẩy, đặt ô đó trong ngoặc kép"));
    }

    private CsvReader.ReadResult readTopic(String csv) {
        return reader.read(ImportDataType.TOPIC, "data.csv", utf8(csv));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void assertFileError(ImportFileException exception, ErrorCode code, HttpStatus status) {
        assertThat(exception.getCode()).isEqualTo(code);
        assertThat(exception.getStatus()).isEqualTo(status);
        assertThat(exception.getMessage()).isNotBlank();
    }
}
