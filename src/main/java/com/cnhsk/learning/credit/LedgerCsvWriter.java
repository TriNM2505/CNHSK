package com.cnhsk.learning.credit;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.cnhsk.learning.credit.web.LedgerRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class LedgerCsvWriter {
    public byte[] write(List<LedgerRow> rows) {
        StringWriter text = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(text, CSVFormat.RFC4180)) {
            csv.printRecord("id", "userId", "maskedEmail", "seq", "type", "amount", "balanceBefore",
                    "balanceAfter", "featureCode", "reason", "createdAt");
            for (LedgerRow row : rows) {
                csv.printRecord(cell(row.id()), cell(row.userId()), cell(row.maskedEmail()), cell(row.seq()),
                        cell(row.type()), cell(row.amount()), cell(row.balanceBefore()), cell(row.balanceAfter()),
                        cell(row.featureCode()), cell(row.reason()), cell(row.createdAt()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to construct ledger CSV.", exception);
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        int first = 0;
        while (first < text.length()) {
            char character = text.charAt(first);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)
                    && !Character.isISOControl(character) && Character.getType(character) != Character.FORMAT) break;
            first++;
        }
        if (first > 0 || (first < text.length() && "=+-@".indexOf(text.charAt(first)) >= 0)) return "'" + text;
        return text;
    }
}
