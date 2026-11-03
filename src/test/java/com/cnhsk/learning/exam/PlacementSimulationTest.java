package com.cnhsk.learning.exam;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cnhsk.learning.exam.PlacementAlgorithm.BlockResult;
import com.cnhsk.learning.exam.PlacementAlgorithm.Conclusion;
import com.cnhsk.learning.exam.PlacementAlgorithm.Decision;
import com.cnhsk.learning.exam.PlacementAlgorithm.Next;
import com.cnhsk.learning.exam.PlacementAlgorithm.PlacementLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementSimulationTest {

    private static final int RUNS_PER_TRUE_LEVEL = 1_000;
    private static final long RANDOM_SEED = 20261104L;

    private final PlacementAlgorithm algorithm = new PlacementAlgorithm();
    private final PlacementConfig config = new PlacementConfig();

    @Test
    void FR017_AT24_deterministicSimulationMeetsPlacementAccuracyBudget() {
        Random random = new Random(RANDOM_SEED);

        for (int trueLevel = 0; trueLevel <= 3; trueLevel++) {
            SimulationSummary summary = simulate(trueLevel, random, false);

            assertThat(summary.correctRate()).as("true level %s", trueLevel).isGreaterThanOrEqualTo(0.80d);
            assertThat(summary.moreThanOneLevelOffRate()).as("true level %s", trueLevel).isLessThanOrEqualTo(0.02d);
        }
    }

    @Test
    void FR017_AT24_randomGuesserNeverReachesHsk3OrAbove() {
        SimulationSummary summary = simulate(0, new Random(RANDOM_SEED + 1), true);

        assertThat(summary.hsk3OrAboveCount()).isZero();
    }

    private SimulationSummary simulate(int trueLevel, Random random, boolean randomGuesser) {
        int correct = 0;
        int moreThanOneLevelOff = 0;
        int hsk3OrAbove = 0;
        for (int run = 0; run < RUNS_PER_TRUE_LEVEL; run++) {
            PlacementLevel result = run(trueLevel, random, randomGuesser);
            int actual = numeric(result);
            if (actual == trueLevel) {
                correct++;
            }
            if (Math.abs(actual - trueLevel) > 1) {
                moreThanOneLevelOff++;
            }
            if (result == PlacementLevel.HSK3 || result == PlacementLevel.ABOVE_3) {
                hsk3OrAbove++;
            }
        }
        return new SimulationSummary((double) correct / RUNS_PER_TRUE_LEVEL,
                (double) moreThanOneLevelOff / RUNS_PER_TRUE_LEVEL, hsk3OrAbove);
    }

    private PlacementLevel run(int trueLevel, Random random, boolean randomGuesser) {
        List<BlockResult> blocks = new ArrayList<>();
        while (true) {
            Decision decision = algorithm.decide(blocks, config, false);
            if (decision instanceof Conclusion conclusion) {
                return conclusion.level();
            }
            int level = ((Next) decision).level();
            blocks.add(new BlockResult(level, correctAnswers(level, trueLevel, random, randomGuesser), config.getBlockSize()));
        }
    }

    private int correctAnswers(int level, int trueLevel, Random random, boolean randomGuesser) {
        double probability = randomGuesser ? 0.30d : probability(level, trueLevel);
        int correct = 0;
        for (int item = 0; item < config.getBlockSize(); item++) {
            if (random.nextDouble() < probability) {
                correct++;
            }
        }
        return correct;
    }

    private double probability(int level, int trueLevel) {
        if (level <= trueLevel) {
            return 0.85d;
        }
        return level == trueLevel + 1 ? 0.40d : 0.30d;
    }

    private int numeric(PlacementLevel level) {
        return switch (level) {
            case BELOW_1 -> 0;
            case HSK1 -> 1;
            case HSK2 -> 2;
            case HSK3, ABOVE_3 -> 3;
        };
    }

    private record SimulationSummary(double correctRate, double moreThanOneLevelOffRate, int hsk3OrAboveCount) {
    }
}
