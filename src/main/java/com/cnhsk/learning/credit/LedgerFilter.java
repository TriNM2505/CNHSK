package com.cnhsk.learning.credit;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Inclusive Vietnam calendar dates shared by ledger listing and export. */
public record LedgerFilter(LocalDate from, LocalDate to, TransactionType type) {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public LedgerFilter {
        if (from == null || to == null || !from.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !to.toString().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0 || days > 92) throw invalid();
    }

    public static LedgerFilter parse(String from, String to, String type) {
        try {
            if (from == null || to == null || !from.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || !to.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
            return new LedgerFilter(LocalDate.parse(from), LocalDate.parse(to),
                    type == null ? null : TransactionType.valueOf(type));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    public Instant fromInclusive() { return from.atStartOfDay(ZONE).toInstant(); }

    public Instant toExclusive() { return to.plusDays(1).atStartOfDay(ZONE).toInstant(); }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid ledger query parameters.", Map.of());
    }
}
