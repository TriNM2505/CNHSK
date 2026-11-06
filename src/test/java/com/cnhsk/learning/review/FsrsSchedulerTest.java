package com.cnhsk.learning.review;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cnhsk.learning.review.FsrsConfig.Settings;
import com.cnhsk.learning.review.FsrsScheduler.Memory;
import com.cnhsk.learning.review.FsrsScheduler.Rating;
import com.cnhsk.learning.review.FsrsScheduler.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.data.Offset.offset;

class FsrsSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:34:56.123456789Z");
    private static final List<Double> WEIGHTS = List.of(0.40255, 1.18385, 3.173, 15.69105, 7.1949,
            0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698,
            0.2315, 2.9898, 0.51655, 0.6621);
    private final FsrsScheduler scheduler = new FsrsScheduler(new Settings(WEIGHTS, new BigDecimal("0.9"), 1, 365));

    @ParameterizedTest
    @CsvSource({"AGAIN, 1, 0.40255, 7.1949, 1", "HARD, 2, 1.18385, 6.488305268471, 1",
            "GOOD, 3, 3.173, 5.282434422319, 3", "EASY, 4, 15.69105, 3.224501589371, 16"})
    void AT19_firstReviewMatchesLockedWeightsAndStartsWithoutLapses(Rating rating, int grade,
            String stability, String difficulty, int days) {
        Result result = scheduler.first(rating, NOW);
        assertThat(rating.grade()).isEqualTo(grade);
        assertThat(result.stability()).isEqualByComparingTo(stability);
        close(result.difficulty(), difficulty);
        assertThat(result.lapseCount()).isZero();
        assertThat(result.intervalDays()).isEqualTo(days);
        assertThat(result.nextReviewAt()).isEqualTo(NOW.plus(days, ChronoUnit.DAYS));
    }

    @Test
    void AT19_secondGoodAtItsDeadlineHasALongerInterval() {
        Result first = scheduler.first(Rating.GOOD, NOW);
        Memory memory = new Memory(first.stability(), first.difficulty(), NOW, first.lapseCount());
        Result next = scheduler.review(memory, Rating.GOOD, first.nextReviewAt());
        close(next.stability(), "10.738925846132");
        close(next.difficulty(), "5.272967931287");
        assertThat(next.intervalDays()).isEqualTo(11).isGreaterThan(first.intervalDays());
        assertThat(next.lapseCount()).isZero();
        assertThat(next.nextReviewAt()).isEqualTo(NOW.plus(14, ChronoUnit.DAYS));
        assertThat(memory).isEqualTo(new Memory(new BigDecimal("3.173"), first.difficulty(), NOW, 0));
        assertThat(scheduler.review(memory, Rating.GOOD, first.nextReviewAt())).isEqualTo(next);
    }

    @Test
    void AT19_longTermAgainReducesStabilityAndIncrementsLapses() {
        Memory memory = memory("10", "5", 2);
        Result result = scheduler.review(memory, Rating.AGAIN, NOW.plus(10, ChronoUnit.DAYS));
        close(result.stability(), "2.107696257678");
        close(result.difficulty(), "6.607035107311");
        assertThat(result.stability()).isLessThanOrEqualTo(memory.stability());
        assertThat(result.lapseCount()).isEqualTo(3);
        assertThat(result.intervalDays()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"HARD, 15.313912114253, 15", "GOOD, 32.954263992452, 33", "EASY, 78.628658484634, 79"})
    void AT19_longTermRememberedRatingsUseTheirDistinctMultipliers(Rating rating, String stability, int days) {
        Result result = scheduler.review(memory("10", "5", 2), rating, NOW.plus(10, ChronoUnit.DAYS));
        close(result.stability(), stability);
        assertThat(result.intervalDays()).isEqualTo(days);
        assertThat(result.lapseCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"AGAIN, 5.010285241935, 6.607035107311, 3, 5",
            "HARD, 8.398413743809, 5.799433907311, 2, 8",
            "GOOD, 14.077712147375, 4.991832707311, 2, 14",
            "EASY, 23.597548936006, 4.184231507311, 2, 24"})
    void AT19_sameDayUsesShortTermStabilityButStillUpdatesDifficultyAndLapses(Rating rating,
            String stability, String difficulty, int lapses, int days) {
        Result result = scheduler.review(memory("10", "5", 2), rating, NOW.plus(12, ChronoUnit.HOURS));
        close(result.stability(), stability);
        close(result.difficulty(), difficulty);
        assertThat(result.lapseCount()).isEqualTo(lapses);
        assertThat(result.intervalDays()).isEqualTo(days);
    }

    @Test
    void AT19_exactDurationBoundarySelectsShortTermBelowTwentyFourHours() {
        Memory memory = memory("10", "5", 0);
        Result zero = scheduler.review(memory, Rating.GOOD, NOW);
        Result below = scheduler.review(memory, Rating.GOOD, NOW.plus(Duration.ofDays(1)).minusNanos(1));
        Result exact = scheduler.review(memory, Rating.GOOD, NOW.plus(Duration.ofDays(1)));
        Result above = scheduler.review(memory, Rating.GOOD, NOW.plus(Duration.ofDays(1)).plusNanos(1));
        close(zero.stability(), "14.077712147375");
        assertThat(below.stability()).isEqualByComparingTo(zero.stability());
        close(exact.stability(), "12.527989943727");
        close(above.stability(), "12.527989943727");
        assertThat(exact.stability()).isNotEqualByComparingTo(below.stability());
    }

    @ParameterizedTest
    @EnumSource(Rating.class)
    void AT19_clockSkewIsZeroElapsedTimeRatherThanAnError(Rating rating) {
        Memory future = new Memory(new BigDecimal("10"), new BigDecimal("5"), NOW.plusSeconds(1), 2);
        assertThat(scheduler.review(future, rating, NOW)).isEqualTo(scheduler.review(memory("10", "5", 2), rating, NOW));
    }

    @Test
    void AT19_elapsedTimeUsesFractionalUtcDaysRatherThanCalendarDates() {
        Instant last = Instant.parse("2026-11-05T23:59:00Z");
        Memory memory = new Memory(new BigDecimal("10"), new BigDecimal("5"), last, 0);
        close(scheduler.review(memory, Rating.GOOD, last.plusSeconds(120)).stability(), "14.077712147375");
        Result fractional = scheduler.review(memory, Rating.GOOD, last.plus(36, ChronoUnit.HOURS));
        Result wholeDay = scheduler.review(memory, Rating.GOOD, last.plus(24, ChronoUnit.HOURS));
        assertThat(fractional.stability()).isGreaterThan(wholeDay.stability());
        assertThat(fractional.nextReviewAt()).isEqualTo(last.plus(36, ChronoUnit.HOURS)
                .plus(fractional.intervalDays(), ChronoUnit.DAYS));
    }

    @Test
    void AT19_meanReversionUsesUnclampedInitialEasyDifficulty() {
        List<Double> weights = new ArrayList<>(WEIGHTS);
        weights.set(4, 1.0);
        weights.set(5, 1.0);
        FsrsScheduler custom = scheduler(weights, "0.9", 1, 365);
        Result result = custom.review(memory("10", "5", 0), Rating.GOOD, NOW.plus(1, ChronoUnit.DAYS));
        close(result.difficulty(), "4.893806530153");
    }

    @Test
    void AT19_generalRetentionAndConfiguredBoundsApplyWithoutRoundingMemoryState() {
        assertThat(scheduler(WEIGHTS, "0.8", 1, 365).first(Rating.GOOD, NOW).intervalDays()).isEqualTo(8);
        FsrsScheduler bounded = scheduler(WEIGHTS, "0.9", 2, 10);
        assertThat(bounded.first(Rating.AGAIN, NOW).intervalDays()).isEqualTo(2);
        Result easy = bounded.first(Rating.EASY, NOW);
        assertThat(easy.intervalDays()).isEqualTo(10);
        assertThat(easy.stability()).isEqualByComparingTo("15.69105");
        close(easy.difficulty(), "3.224501589371");
        assertThat(scheduler(WEIGHTS, "0.9", 7, 7).first(Rating.GOOD, NOW).intervalDays()).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({"2.4999, 2", "2.5, 3", "2.5001, 3", "1000, 365"})
    void AT19_retentionPointNineIsExactlyHalfUpStabilityBeforeClamping(String stability, int days) {
        List<Double> weights = new ArrayList<>(WEIGHTS);
        weights.set(2, Double.valueOf(stability));
        Result result = scheduler(weights, "0.9", 1, 365).first(Rating.GOOD, NOW);
        assertThat(result.stability()).isEqualByComparingTo(stability);
        assertThat(result.intervalDays()).isEqualTo(days);
    }

    @Test
    void AT19_forgettingCapsStabilityAndDifficultyIsClampedOnlyAfterItsFormula() {
        Result capped = scheduler.review(memory("0.1", "1", 0), Rating.AGAIN, NOW.plus(3650, ChronoUnit.DAYS));
        assertThat(capped.stability()).isEqualByComparingTo("0.1");
        assertThat(scheduler.review(memory("10", "10", 0), Rating.AGAIN, NOW).difficulty())
                .isBetween(BigDecimal.ONE, BigDecimal.TEN);
        List<Double> weights = new ArrayList<>(WEIGHTS);
        weights.set(6, 100.0);
        FsrsScheduler custom = scheduler(weights, "0.9", 1, 365);
        assertThat(custom.review(memory("10", "5", 0), Rating.AGAIN, NOW).difficulty()).isEqualByComparingTo("10");
        assertThat(custom.review(memory("10", "5", 0), Rating.EASY, NOW).difficulty()).isEqualByComparingTo("1");
        weights.set(4, -100.0);
        assertThat(scheduler(weights, "0.9", 1, 365).first(Rating.GOOD, NOW).difficulty()).isEqualByComparingTo("1");
        weights.set(4, 100.0);
        assertThat(scheduler(weights, "0.9", 1, 365).first(Rating.GOOD, NOW).difficulty()).isEqualByComparingTo("10");
    }

    @ParameterizedTest
    @CsvSource({"17, 2000, GOOD, 0", "17, -2000, GOOD, 0", "11, -1, AGAIN, 10", "13, 0, AGAIN, 10"})
    void AT19_invalidCalculatedStabilityFailsWithoutClampingIt(int index, double weight, Rating rating, int elapsedDays) {
        List<Double> weights = new ArrayList<>(WEIGHTS);
        weights.set(index, weight);
        FsrsScheduler custom = scheduler(weights, "0.9", 1, 365);
        assertThatIllegalStateException().isThrownBy(() -> custom.review(memory("10", "5", 0), rating,
                NOW.plus(elapsedDays, ChronoUnit.DAYS))).withMessageContaining("stability");
    }

    @Test
    void AT19_undefinedCalculatedDifficultyFailsRatherThanReturningNaN() {
        List<Double> weights = new ArrayList<>(WEIGHTS);
        weights.set(5, 1000.0);
        weights.set(7, 0.0);
        assertThatIllegalStateException().isThrownBy(() -> scheduler(weights, "0.9", 1, 365)
                .review(memory("10", "5", 0), Rating.GOOD, NOW)).withMessageContaining("difficulty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1e309", "1e-400"})
    void AT19_memoryRejectsStabilityOutsidePositiveFiniteNumericalDomain(String stability) {
        assertThatIllegalArgumentException().isThrownBy(() -> memory(stability, "5", 0));
    }

    @Test
    void AT19_invalidMemoryAndMissingArgumentsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> memory("10", "0.99", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> memory("10", "10.01", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> memory("10", "5", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Memory(null, BigDecimal.ONE, NOW, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Memory(BigDecimal.ONE, null, NOW, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Memory(BigDecimal.ONE, BigDecimal.ONE, null, 0));
        assertThatNullPointerException().isThrownBy(() -> new FsrsScheduler(null));
        assertThatNullPointerException().isThrownBy(() -> scheduler.first(null, NOW));
        assertThatNullPointerException().isThrownBy(() -> scheduler.first(Rating.GOOD, null));
        assertThatNullPointerException().isThrownBy(() -> scheduler.review(null, Rating.GOOD, NOW));
        assertThatNullPointerException().isThrownBy(() -> scheduler.review(memory("10", "5", 0), null, NOW));
        assertThatNullPointerException().isThrownBy(() -> scheduler.review(memory("10", "5", 0), Rating.GOOD, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(BigDecimal.ONE, BigDecimal.ONE, 0, 0, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(BigDecimal.ONE, BigDecimal.ONE, 0, 366, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(BigDecimal.ONE, BigDecimal.ONE, 0, 1, null));
    }

    @Test
    void AT19_seededMixedReviewHistoryKeepsIntervalsAndDifficultyWithinBounds() {
        Random random = new Random(110019L);
        Instant now = NOW;
        Result result = scheduler.first(Rating.GOOD, now);
        int lapses = 0;
        for (int index = 0; index < 2000; index++) {
            Memory previous = new Memory(result.stability(), result.difficulty(), now, result.lapseCount());
            now = now.plusSeconds(random.nextInt(40 * 86_400));
            Rating rating = Rating.values()[random.nextInt(Rating.values().length)];
            if (rating == Rating.AGAIN) {
                lapses++;
            }
            result = scheduler.review(previous, rating, now);
            assertThat(result.intervalDays()).isBetween(1, 365);
            assertThat(result.difficulty()).isBetween(BigDecimal.ONE, BigDecimal.TEN);
            assertThat(result.stability()).isPositive();
            assertThat(Double.isFinite(result.stability().doubleValue())).isTrue();
            assertThat(result.lapseCount()).isEqualTo(lapses);
            assertThat(result.nextReviewAt()).isEqualTo(now.plus(result.intervalDays(), ChronoUnit.DAYS));
        }
    }

    @ParameterizedTest
    @EnumSource(Rating.class)
    void T12_firstStabilityIsCappedWithoutChangingTheBoundedInterval(Rating rating) {
        List<Double> weights = new ArrayList<>(WEIGHTS);
        for (int index = 0; index < 4; index++) weights.set(index, 1_000_000.0);
        Result result = scheduler(weights, "0.9", 1, 365).first(rating, NOW);
        assertThat(result.stability()).isEqualByComparingTo("36500");
        assertThat(result.intervalDays()).isEqualTo(365);
        assertThat(result.nextReviewAt()).isEqualTo(NOW.plus(365, ChronoUnit.DAYS));
        assertThat(result.lapseCount()).isZero();
    }

    @Test
    void T12_sameDayEasySequenceSaturatesStabilityAtFsrsMaximum() {
        Result result = scheduler.first(Rating.EASY, NOW);
        for (int count = 1; count < 50; count++) {
            result = scheduler.review(new Memory(result.stability(), result.difficulty(), NOW, result.lapseCount()), Rating.EASY, NOW);
            assertThat(result.stability()).isLessThanOrEqualTo(new BigDecimal("36500"));
        }
        assertThat(result.stability()).isEqualByComparingTo("36500");
        assertThat(result.intervalDays()).isEqualTo(365);
        assertThat(result.nextReviewAt()).isEqualTo(NOW.plus(365, ChronoUnit.DAYS));
        assertThat(result.lapseCount()).isZero();
    }

    private Memory memory(String stability, String difficulty, int lapses) {
        return new Memory(new BigDecimal(stability), new BigDecimal(difficulty), NOW, lapses);
    }

    private FsrsScheduler scheduler(List<Double> weights, String retention, int min, int max) {
        return new FsrsScheduler(new Settings(weights, new BigDecimal(retention), min, max));
    }

    private void close(BigDecimal value, String expected) {
        assertThat(value).isCloseTo(new BigDecimal(expected), offset(new BigDecimal("0.000000001")));
    }
}
