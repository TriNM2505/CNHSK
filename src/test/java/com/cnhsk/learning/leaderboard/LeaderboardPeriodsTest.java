package com.cnhsk.learning.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.cnhsk.learning.leaderboard.LeaderboardPeriods.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LeaderboardPeriodsTest {
    @ParameterizedTest
    @CsvSource({
            "2026-11-15T16:59:59Z,2026-11-09T00:00:00+07:00,2026-11-16T00:00:00+07:00",
            "2026-11-15T17:00:00Z,2026-11-16T00:00:00+07:00,2026-11-23T00:00:00+07:00",
            "2027-01-03T17:00:00Z,2027-01-04T00:00:00+07:00,2027-01-11T00:00:00+07:00"})
    void FR005_AT07_weekChangesAtVietnamMonday(String now, String start, String end) {
        assertInterval(periods(now).resolve("week"), "week", start, end);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-30T16:59:59Z,2026-11-01T00:00:00+07:00,2026-12-01T00:00:00+07:00",
            "2026-11-30T17:00:00Z,2026-12-01T00:00:00+07:00,2027-01-01T00:00:00+07:00",
            "2026-12-31T17:00:00Z,2027-01-01T00:00:00+07:00,2027-02-01T00:00:00+07:00",
            "2028-02-29T16:59:59Z,2028-02-01T00:00:00+07:00,2028-03-01T00:00:00+07:00"})
    void FR005_AT08_monthChangesAtVietnamMidnight(String now, String start, String end) {
        assertInterval(periods(now).resolve("month"), "month", start, end);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Los_Angeles", "Asia/Tokyo"})
    void FR005_clockZoneDoesNotChangeVietnamInterval(String zone) {
        var clock = Clock.fixed(Instant.parse("2026-11-15T17:00:00Z"), ZoneId.of(zone));
        assertInterval(new LeaderboardPeriods(clock).resolve("week"), "week",
                "2026-11-16T00:00:00+07:00", "2026-11-23T00:00:00+07:00");
    }

    @Test
    void FR004_missingPeriodDefaultsToWeek() {
        var periods = periods("2026-11-14T00:00:00Z");
        assertThat(periods.resolve(null)).isEqualTo(periods.resolve("week"));
    }

    @Test
    void FR006_allHasNoArtificialBounds() {
        var interval = periods("2026-11-14T00:00:00Z").resolve("all");
        assertThat(interval).isEqualTo(new Interval("all", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "WEEK", "2026-W39", "year", " week "})
    void FR004_rejectsUnsupportedPeriods(String period) {
        assertThatIllegalArgumentException().isThrownBy(() -> periods("2026-11-14T00:00:00Z").resolve(period));
    }

    @Test
    void FR005_intervalRejectsMissingReversedOrInconsistentBounds() {
        var start = OffsetDateTime.parse("2026-11-16T00:00:00+07:00");
        var end = start.plusWeeks(1);
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("week", null, end));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("month", start, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("week", end, start));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("week", start, start));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("all", start, end));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval("year", start, end));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval(null, null, null));
    }

    private static LeaderboardPeriods periods(String instant) {
        return new LeaderboardPeriods(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static void assertInterval(Interval interval, String period, String start, String end) {
        assertThat(interval.period()).isEqualTo(period);
        assertThat(interval.start()).isEqualTo(OffsetDateTime.parse(start));
        assertThat(interval.end()).isEqualTo(OffsetDateTime.parse(end));
        assertThat(interval.start().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        assertThat(interval.end().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
    }
}
