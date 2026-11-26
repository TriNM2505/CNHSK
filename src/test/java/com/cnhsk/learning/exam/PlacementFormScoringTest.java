package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cnhsk.learning.exam.PlacementFormScoring.Confidence;
import com.cnhsk.learning.exam.PlacementFormScoring.Item;
import com.cnhsk.learning.exam.PlacementFormScoring.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlacementFormScoringTest {
    private final PlacementConfig config = new PlacementConfig();

    @ParameterizedTest(name = "{0}/{1}/{2} right of ten per level -> {3}")
    @CsvSource({
            "0, 0, 0, BELOW_1",
            "3, 1, 2, BELOW_1", // about what guessing gives
            "7, 2, 2, HSK1",
            "10, 2, 2, HSK1",
            "10, 7, 3, HSK2",
            "10, 10, 2, HSK2",
            "10, 10, 7, HSK3",
            "9, 9, 9, HSK3",
            "10, 10, 10, ABOVE_3",
    })
    void typicalAnswerSheetsLandOnTheExpectedLevel(int hsk1, int hsk2, int hsk3, Level expected) {
        assertThat(PlacementFormScoring.decide(sheet(hsk1, hsk2, hsk3, 0.25), config).level()).isEqualTo(expected);
    }

    @Test
    void luckyRightAnswersOnHardItemsDoNotOutweighMissingTheEasyOnes() {
        // Three hard items right by luck while most easy ones are wrong: still a beginner, not HSK 3.
        var level = PlacementFormScoring.decide(sheet(3, 3, 3, 0.25), config).level();
        assertThat(level).isIn(Level.BELOW_1, Level.HSK1);
    }

    @Test
    void trueFalseItemsCountLessBecauseHalfOfThemAreRightByGuessing() {
        double multipleChoice = PlacementFormScoring.estimate(sheet(6, 6, 6, 0.25), 2).ability();
        double trueFalse = PlacementFormScoring.estimate(sheet(6, 6, 6, 0.5), 2).ability();
        assertThat(trueFalse).isLessThan(multipleChoice);
    }

    @Test
    void manyUnansweredOrAnUncertainEstimateIsTentative() {
        List<Item> skipped = new ArrayList<>(sheet(10, 10, 4, 0.25));
        assertThat(PlacementFormScoring.decide(skipped, config).confidence()).isEqualTo(Confidence.SURE);
        for (int index = 0; index < 6; index++) {
            Item item = skipped.get(29 - index);
            skipped.set(29 - index, new Item(item.difficulty(), item.guess(), false, false));
        }
        assertThat(PlacementFormScoring.decide(skipped, config).confidence()).isEqualTo(Confidence.TENTATIVE);
        assertThat(PlacementFormScoring.decide(sheet(1, 0, 0, 0.25).subList(0, 3), config).confidence())
                .isEqualTo(Confidence.TENTATIVE);
    }

    @Test
    void simulatedLearnersAreMostlyPlacedAtTheirTrueLevel() {
        Random random = new Random(42);
        // Abilities in the middle of each level's band: below HSK 1, HSK 1, HSK 2, HSK 3.
        double[] trueAbilities = {-2.6, -0.75, 0.9, 2.15};
        Level[] expected = {Level.BELOW_1, Level.HSK1, Level.HSK2, Level.HSK3};
        for (int group = 0; group < trueAbilities.length; group++) {
            int hits = 0;
            int farOff = 0;
            for (int learner = 0; learner < 300; learner++) {
                List<Item> items = new ArrayList<>();
                for (int level = 1; level <= 3; level++) {
                    for (int index = 0; index < 10; index++) {
                        double difficulty = PlacementFormScoring.priorDifficulty(level, 1 + index % 3);
                        double p = 0.25 + 0.75 / (1 + Math.exp(-(trueAbilities[group] - difficulty)));
                        items.add(new Item(difficulty, 0.25, random.nextDouble() < p, true));
                    }
                }
                Level placed = PlacementFormScoring.decide(items, config).level();
                Level target = expected[group];
                if (placed == target || (target == Level.HSK3 && placed == Level.ABOVE_3)) hits++;
                if (Math.abs(placed.ordinal() - target.ordinal()) > 1) farOff++;
            }
            assertThat(hits).as("learners at %s placed right", expected[group]).isGreaterThanOrEqualTo(180);
            assertThat(farOff).as("learners at %s placed two levels off", expected[group]).isLessThanOrEqualTo(6);
        }
    }

    @Test
    void calibrationReadsAnItemsDifficultyFromRealAnswers() {
        // Learners of ability 0 got it right 62.5 % of the time on four options: the item sits at about b = 0.
        assertThat(PlacementFormScoring.calibrated(0, 0.625, 0.25)).isCloseTo(0, within(0.01));
        // Everyone got it right: it is easy, far below their ability.
        assertThat(PlacementFormScoring.calibrated(0, 1.0, 0.25)).isLessThan(-3);
    }

    @Test
    void theProfileStartsBelowHskOneAtOneAndCapsAboveThreeAtThree() {
        assertThat(PlacementFormScoring.profileLevel(Level.BELOW_1)).isEqualTo(1);
        assertThat(PlacementFormScoring.profileLevel(Level.HSK2)).isEqualTo(2);
        assertThat(PlacementFormScoring.profileLevel(Level.ABOVE_3)).isEqualTo(3);
    }

    /** Ten items per level at their starting difficulty; the first {@code right} of each level answered correctly. */
    private static List<Item> sheet(int hsk1, int hsk2, int hsk3, double guess) {
        int[] right = {hsk1, hsk2, hsk3};
        List<Item> items = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            for (int index = 0; index < 10; index++) {
                items.add(new Item(PlacementFormScoring.priorDifficulty(level, 2), guess, index < right[level - 1], true));
            }
        }
        return items;
    }
}
