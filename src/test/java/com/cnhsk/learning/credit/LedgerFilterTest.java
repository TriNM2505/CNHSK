package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class LedgerFilterTest {
    @Test void vietnamDatesAreInclusiveAndConvertedToUtcHalfOpenBounds() {
        LedgerFilter filter = LedgerFilter.parse("2028-02-28", "2028-03-01", null);
        assertThat(filter.from()).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(filter.to()).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(filter.type()).isNull();
        assertThat(filter.fromInclusive()).isEqualTo(Instant.parse("2028-02-27T17:00:00Z"));
        assertThat(filter.toExclusive()).isEqualTo(Instant.parse("2028-03-01T17:00:00Z"));
        assertThat(ChronoUnit.HOURS.between(filter.fromInclusive(), filter.toExclusive())).isEqualTo(72);
    }

    @Test void sameDayAndNinetyTwoElapsedDaysAllowUpToNinetyThreeCalendarDates() {
        LocalDate from = LocalDate.of(2027, 12, 1);
        for (int days : List.of(0, 1, 31, 92)) {
            LedgerFilter filter = LedgerFilter.parse(from.toString(), from.plusDays(days).toString(), null);
            assertThat(ChronoUnit.DAYS.between(filter.fromInclusive(), filter.toExclusive())).isEqualTo(days + 1);
        }
        invalid("2027-12-01", from.plusDays(93).toString(), null);
        invalid("2028-01-02", "2028-01-01", null);
    }

    @Test void utcBoundsDoNotDependOnJvmDefaultTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                LedgerFilter filter = LedgerFilter.parse("2028-01-01", "2028-01-01", null);
                assertThat(filter.fromInclusive()).isEqualTo(Instant.parse("2027-12-31T17:00:00Z"));
                assertThat(filter.toExclusive()).isEqualTo(Instant.parse("2028-01-01T17:00:00Z"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest @MethodSource("invalidDates")
    void datesMustBePresentStrictIsoCalendarDates(String value) {
        invalid(value, "2028-03-01", null);
        invalid("2028-03-01", value, null);
    }

    static Stream<Arguments> invalidDates() {
        return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of(" "), Arguments.of("\t"),
                Arguments.of("2027-02-29"), Arguments.of("2028-02-30"), Arguments.of("2028-13-01"),
                Arguments.of("2028-00-01"), Arguments.of("2028-01-00"), Arguments.of("2028-3-01"),
                Arguments.of("28-03-01"), Arguments.of("2028-03-01T00:00:00Z"), Arguments.of("2028/03/01"),
                Arguments.of(" 2028-03-01"), Arguments.of("2028-03-01 "), Arguments.of("not-a-date"));
    }

    @Test void eachLockedTransactionTypeIsAcceptedWithoutInventingANewType() {
        assertThat(TransactionType.values()).extracting(TransactionType::name).containsExactly(
                "TOPUP", "DEDUCT", "REFUND", "ADJUSTMENT", "TEACHER_PAYOUT", "SUBSCRIPTION");
        for (TransactionType type : TransactionType.values())
            assertThat(LedgerFilter.parse("2028-01-01", "2028-01-01", type.name()).type()).isEqualTo(type);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "topup", "TOPUP ", " TOPUP", "UNKNOWN", "0", "null"})
    void suppliedTypeMustBeAnExactLockedEnumValue(String type) {
        invalid("2028-01-01", "2028-01-01", type);
    }

    private static void invalid(String from, String to, String type) {
        assertThatThrownBy(() -> LedgerFilter.parse(from, to, type)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(error.getMessage()).isNotBlank();
            assertThat(error.getCause()).isNull();
        });
    }
}
