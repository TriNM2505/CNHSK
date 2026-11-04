package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.mastery.MasteryCalculator.Evidence;
import com.cnhsk.learning.mastery.MasteryCalculator.Result;
import com.cnhsk.learning.mastery.MasteryConfig.Settings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryCalculatorTest {

    private static final Instant TODAY = Instant.parse("2026-11-01T12:00:00Z");
    private static final Settings SETTINGS = settings();
    private final MasteryCalculator calculator = new MasteryCalculator();

    @Test
    void AT09_quocBaoExampleRoundsTo53Point3AndExamRaisesItTo61Point1() {
        List<Evidence> evidence = List.of(
                tableEvidence(901L, 1, "0.4886", 0, true),
                tableEvidence(877L, 3, "0.5598", 50, false),
                tableEvidence(850L, 6, "0.5223", 100, false),
                tableEvidence(812L, 9, "0.2437", 100, false),
                tableEvidence(790L, 13, "0.5923", 40, false));

        Result result = calculator.calculate(evidence, TODAY, SETTINGS);
        assertThat(result.value()).isEqualByComparingTo("53.3");
        assertThat(result.confidence()).isCloseTo(new BigDecimal("2.407"), within(new BigDecimal("0.005")));

        List<Evidence> withExam = new ArrayList<>(evidence);
        withExam.add(new Evidence(999L, TODAY, BigDecimal.ONE, 80, false));
        assertThat(calculator.calculate(withExam, TODAY, SETTINGS).value()).isEqualByComparingTo("61.1");
    }

    @Test
    void AT09_windowUsesEightNewestEvidenceAndBackfillsAfterPracticeCap() {
        List<Evidence> evidence = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            evidence.add(new Evidence(100L + day, TODAY.minus(day, ChronoUnit.DAYS), BigDecimal.ONE, 100, true));
        }
        for (int day = 5; day < 10; day++) {
            evidence.add(new Evidence(100L + day, TODAY.minus(day, ChronoUnit.DAYS), BigDecimal.ONE, 0, false));
        }

        Result result = calculator.calculate(evidence, TODAY, SETTINGS);

        assertThat(result.evidenceCount()).isEqualTo(8);
        assertThat(result.evidence()).hasSize(8);
        assertThat(result.evidence()).filteredOn(Evidence::practice).hasSize(3);
        assertThat(result.evidence()).extracting(Evidence::itemId)
                .containsExactly(100L, 101L, 102L, 105L, 106L, 107L, 108L, 109L);
    }

    @Test
    void emptyWindowHasNullValueAndZeroConfidence() {
        Result result = calculator.calculate(List.of(), TODAY, SETTINGS);

        assertThat(result.value()).isNull();
        assertThat(result.confidence()).isZero();
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void futureEvidenceHasZeroAgeAndDuplicateIdsRemainAsInput() {
        Evidence first = new Evidence(5L, TODAY.plus(1, ChronoUnit.DAYS), BigDecimal.ONE, 100, false);
        Evidence duplicateId = new Evidence(5L, TODAY, BigDecimal.ONE, 0, false);

        Result result = calculator.calculate(List.of(first, duplicateId), TODAY, SETTINGS);

        assertThat(result.evidence()).containsExactly(first, duplicateId);
        assertThat(result.confidence()).isEqualByComparingTo("2.0");
        assertThat(result.value()).isEqualByComparingTo("50.0");
    }

    @Test
    void fractionalHalfDayAgeAppliesFractionalHalfLife() {
        Evidence halfDayOld = new Evidence(1L, TODAY.minus(12, ChronoUnit.HOURS), BigDecimal.ONE, 100, false);

        Result result = calculator.calculate(List.of(halfDayOld), TODAY, SETTINGS);

        assertThat(result.confidence()).isCloseTo(BigDecimal.valueOf(Math.pow(0.5, 0.5 / 30)),
                within(new BigDecimal("0.000000000001")));
        assertThat(result.value()).isEqualByComparingTo("100.0");
    }

    private static Evidence tableEvidence(long itemId, int ageDays, String effectiveWeight, int score, boolean practice) {
        double decay = Math.pow(0.5, (double) ageDays / SETTINGS.halfLifeDays());
        BigDecimal staticWeight = new BigDecimal(effectiveWeight)
                .divide(BigDecimal.valueOf(decay), MathContext.DECIMAL128);
        return new Evidence(itemId, TODAY.minus(ageDays, ChronoUnit.DAYS), staticWeight, score, practice);
    }

    private static Settings settings() {
        return new Settings(8, 3, 3, 2, new BigDecimal("1.0"), 30, new BigDecimal("60"),
                new BigDecimal("80"), new BigDecimal("3"), 3, 7, 14);
    }

    private static org.assertj.core.data.Offset<BigDecimal> within(BigDecimal delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
