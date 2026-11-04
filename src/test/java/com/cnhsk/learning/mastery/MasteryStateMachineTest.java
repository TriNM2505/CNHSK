package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cnhsk.learning.mastery.MasteryCalculator.Evidence;
import com.cnhsk.learning.mastery.MasteryCalculator.Result;
import com.cnhsk.learning.mastery.MasteryConfig.Settings;
import com.cnhsk.learning.mastery.MasteryStateMachine.Decision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryStateMachineTest {

    private static final Instant NOW = Instant.parse("2026-11-01T17:00:00Z");
    private static final Settings SETTINGS = new Settings(8, 3, 3, 2, new BigDecimal("1.0"), 30,
            new BigDecimal("60"), new BigDecimal("80"), new BigDecimal("3"), 3, 7, 14);
    private final MasteryCalculator calculator = new MasteryCalculator();
    private final MasteryStateMachine machine = new MasteryStateMachine();

    @Test
    void AT10_noEvidenceAndInsufficientEvidenceHaveNoDueDate() {
        assertThat(machine.transition(null, result(null, "0", 0, 0), NOW, SETTINGS))
                .isEqualTo(new Decision(MasteryState.NOT_YET, null));
        assertThat(machine.transition(MasteryState.REVIEW, result("90", "2", 5, 1), NOW, SETTINGS))
                .isEqualTo(new Decision(MasteryState.INSUFFICIENT, null));
    }

    @Test
    void distinctDaysUseVietnamCalendarAcrossUtcDateBoundary() {
        Result oneVietnamDay = calculator.calculate(List.of(
                evidence(3, "2026-01-01T17:30:00Z", 80),
                evidence(2, "2026-01-02T16:30:00Z", 80),
                evidence(1, "2026-01-02T16:00:00Z", 80)), Instant.parse("2026-01-03T00:00:00Z"), SETTINGS);
        assertThat(oneVietnamDay.distinctDays()).isEqualTo(1);
        assertThat(machine.transition(null, oneVietnamDay, NOW, SETTINGS).state()).isEqualTo(MasteryState.INSUFFICIENT);

        Result twoVietnamDays = calculator.calculate(List.of(
                evidence(3, "2026-01-01T16:30:00Z", 80),
                evidence(2, "2026-01-01T17:30:00Z", 80),
                evidence(1, "2026-01-02T16:30:00Z", 80)), Instant.parse("2026-01-03T00:00:00Z"), SETTINGS);
        assertThat(twoVietnamDays.distinctDays()).isEqualTo(2);
        assertThat(machine.transition(null, twoVietnamDays, NOW, SETTINGS).state()).isEqualTo(MasteryState.EXTEND);
    }

    @Test
    void hysteresisUsesRoundedValueAtExactThresholds() {
        assertState(null, "59.94", MasteryState.REVIEW);
        assertState(null, "59.95", MasteryState.CONSOLIDATE);
        assertState(MasteryState.CONSOLIDATE, "58", MasteryState.CONSOLIDATE);
        assertState(MasteryState.CONSOLIDATE, "56.9", MasteryState.REVIEW);
        assertState(MasteryState.EXTEND, "78", MasteryState.EXTEND);
        assertState(MasteryState.EXTEND, "76.9", MasteryState.CONSOLIDATE);
    }

    @Test
    void transitionsCanMoveAcrossMultipleStates() {
        assertState(MasteryState.EXTEND, "56", MasteryState.REVIEW);
        assertState(MasteryState.REVIEW, "85", MasteryState.EXTEND);
        assertState(MasteryState.INSUFFICIENT, "61", MasteryState.CONSOLIDATE);
    }

    @Test
    void AT10_dueDateUsesVietnamLocalCalendarAndStateDuration() {
        Decision decision = machine.transition(null, result("53.3", "2.407", 5, 3), NOW, SETTINGS);

        assertThat(decision.state()).isEqualTo(MasteryState.REVIEW);
        assertThat(decision.nextReviewAt()).isEqualTo(Instant.parse("2026-11-04T17:00:00Z"));
    }

    @Test
    void E10_shortBreaksKeepValueAndDecayConfidenceAtZeroThirtyAndSixtyDays() {
        List<Evidence> evidence = List.of(
                new Evidence(3L, Instant.parse("2026-01-01T17:00:00Z"), new BigDecimal("0.5"), 80, false),
                new Evidence(2L, Instant.parse("2026-01-01T17:00:00Z"), new BigDecimal("0.5"), 80, false),
                new Evidence(1L, Instant.parse("2026-01-02T17:00:00Z"), new BigDecimal("0.5"), 80, false));
        Instant base = Instant.parse("2026-01-02T17:00:00Z");

        Result day0 = calculator.calculate(evidence, base, SETTINGS);
        Result day30 = calculator.calculate(evidence, base.plusSeconds(30L * 86_400), SETTINGS);
        Result day60 = calculator.calculate(evidence, base.plusSeconds(60L * 86_400), SETTINGS);

        assertThat(day0.value()).isEqualByComparingTo("80.0");
        assertThat(day30.value()).isEqualByComparingTo(day0.value());
        assertThat(day60.value()).isEqualByComparingTo(day0.value());
        assertThat(day0.confidence()).isGreaterThan(day30.confidence());
        assertThat(day30.confidence()).isGreaterThan(day60.confidence());
        assertThat(day0.confidence()).isGreaterThanOrEqualTo(new BigDecimal("1.0"));
        assertThat(day30.confidence()).isLessThan(new BigDecimal("1.0"));
        assertThat(machine.transition(null, day0, base, SETTINGS).state()).isEqualTo(MasteryState.EXTEND);
        assertThat(machine.transition(MasteryState.EXTEND, day30, base.plusSeconds(30L * 86_400), SETTINGS).state())
                .isEqualTo(MasteryState.INSUFFICIENT);
        assertThat(machine.transition(MasteryState.EXTEND, day60, base.plusSeconds(60L * 86_400), SETTINGS).state())
                .isEqualTo(MasteryState.INSUFFICIENT);
    }

    private void assertState(MasteryState previous, String value, MasteryState expected) {
        Decision decision = machine.transition(previous, result(value, "2", 5, 3), NOW, SETTINGS);
        assertThat(decision.state()).isEqualTo(expected);
    }

    private static Result result(String value, String confidence, int count, int days) {
        BigDecimal mastery = value == null ? null : new BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_UP);
        List<Evidence> evidence = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> evidence(index + 1L, NOW.minusSeconds(index * 86_400L).toString(), 80))
                .toList();
        return new Result(mastery, new BigDecimal(confidence), count, days, evidence);
    }

    private static Evidence evidence(long id, String answeredAt, int score) {
        return new Evidence(id, Instant.parse(answeredAt), BigDecimal.ONE, score, false);
    }
}
