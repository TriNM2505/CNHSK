package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import com.cnhsk.learning.credit.web.LedgerRow;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerCsvWriterTest {
    private static final List<String> HEADER = List.of("id", "userId", "maskedEmail", "seq", "type", "amount",
            "balanceBefore", "balanceAfter", "featureCode", "reason", "createdAt");
    private static final Instant CREATED = Instant.parse("2028-02-29T17:00:00Z");
    private final LedgerCsvWriter writer = new LedgerCsvWriter();

    @Test void emptyExportHasExactlyTheElevenColumnHeaderUtf8WithoutBomAndCrLf() {
        byte[] bytes = writer.write(List.of());
        assertThat(bytes).isEqualTo((String.join(",", HEADER) + "\r\n").getBytes(StandardCharsets.UTF_8));
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotStartWith("\ufeff");
    }

    @Test void unicodeQuotesCommasAndEmbeddedCrLfRoundTripWithoutLosingNullsOrUtcTimestamp() throws Exception {
        String feature = "Tính năng 中文";
        String reason = "Điều chỉnh, \"中文\"\r\nDòng tiếp theo";
        LedgerRow row = row("ng••@example.test", feature, reason, 5, 0, 5);
        byte[] bytes = writer.write(List.of(row, row("kh••@example.test", null, null, 0, 5, 5)));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(bytes).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
        assertThat(text).doesNotStartWith("\ufeff").endsWith("\r\n")
                .contains("\"Điều chỉnh, \"\"中文\"\"\r\nDòng tiếp theo\"");
        try (CSVParser csv = parse(bytes)) {
            assertThat(csv.getHeaderNames()).isEqualTo(HEADER);
            List<CSVRecord> records = csv.getRecords();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).size()).isEqualTo(11);
            assertThat(records.get(0).get("maskedEmail")).isEqualTo("ng••@example.test");
            assertThat(records.get(0).get("featureCode")).isEqualTo(feature);
            assertThat(records.get(0).get("reason")).isEqualTo(reason);
            assertThat(records.get(0).get("createdAt")).isEqualTo(CREATED.toString());
            assertThat(records.get(1).get("featureCode")).isEmpty();
            assertThat(records.get(1).get("reason")).isEmpty();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"=1+1", "+SUM(A1)", "-1+2", "@SUM(A1)", "\tformula", "\rformula", "\nformula",
            " =1", "\t =1", "\r\n+1", "\u0001@value", "\ufeff=1", "\u2003-1", "\u00a0@value"})
    void formulaAndControlPrefixedCellsAreNeutralizedBeforeCsvQuoting(String value) throws Exception {
        try (CSVParser csv = parse(writer.write(List.of(row(value, value, value, -1, 1, 0))))) {
            CSVRecord record = csv.getRecords().get(0);
            for (String column : List.of("maskedEmail", "featureCode", "reason"))
                assertThat(record.get(column)).as(column).isEqualTo("'" + value);
            assertThat(record.get("amount")).isEqualTo("'-1");
            assertThat(record.get("balanceBefore")).isEqualTo("1");
            assertThat(record.get("balanceAfter")).isEqualTo("0");
        }
    }

    @Test void harmlessTextRemainsExactAndRepeatedExportsDoNotRetainPriorRows() throws Exception {
        for (String text : List.of("普通文本", "ordinary - reason", "123", "'already safe", "white space")) {
            try (CSVParser csv = parse(writer.write(List.of(row("ma••@example.test", text, text, 1, 0, 1))))) {
                CSVRecord record = csv.getRecords().get(0);
                assertThat(record.get("featureCode")).isEqualTo(text);
                assertThat(record.get("reason")).isEqualTo(text);
                assertThat(record.get("id")).isEqualTo("17");
                assertThat(record.get("userId")).isEqualTo("23");
                assertThat(record.get("seq")).isEqualTo("1");
                assertThat(record.get("type")).isEqualTo("ADJUSTMENT");
                assertThat(record.get("amount")).isEqualTo("1");
            }
        }
        assertThat(writer.write(List.of())).isEqualTo((String.join(",", HEADER) + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static LedgerRow row(String email, String feature, String reason, int amount, int before, int after) {
        return new LedgerRow(17, 23, email, 1, TransactionType.ADJUSTMENT, amount, before, after, feature, reason, CREATED);
    }
    private static CSVParser parse(byte[] bytes) throws Exception {
        return CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
    }
}
