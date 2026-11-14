package com.cnhsk.learning.leaderboard;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

import org.springframework.stereotype.Component;

/** Current leaderboard intervals in Vietnam time, with an exclusive upper bound. */
@Component
public final class LeaderboardPeriods {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;

    public LeaderboardPeriods(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public Interval resolve(String period) {
        String selected = period == null ? "week" : period;
        if ("all".equals(selected)) {
            return new Interval(selected, null, null);
        }
        var today = clock.instant().atZone(ZONE).toLocalDate();
        return switch (selected) {
            case "week" -> {
                var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Interval(selected, monday.atStartOfDay(ZONE).toOffsetDateTime(),
                        monday.plusWeeks(1).atStartOfDay(ZONE).toOffsetDateTime());
            }
            case "month" -> {
                var first = today.withDayOfMonth(1);
                yield new Interval(selected, first.atStartOfDay(ZONE).toOffsetDateTime(),
                        first.plusMonths(1).atStartOfDay(ZONE).toOffsetDateTime());
            }
            default -> throw new IllegalArgumentException("Tham số kỳ không hợp lệ.");
        };
    }

    public record Interval(String period, OffsetDateTime start, OffsetDateTime end) {
        public Interval {
            boolean all = "all".equals(period);
            boolean bounded = "week".equals(period) || "month".equals(period);
            if (!(all && start == null && end == null)
                    && !(bounded && start != null && end != null && start.toInstant().isBefore(end.toInstant()))) {
                throw new IllegalArgumentException("Khoảng thời gian không hợp lệ.");
            }
        }
    }
}
