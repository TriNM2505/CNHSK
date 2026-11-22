package com.cnhsk.community.game;

import com.cnhsk.community.game.brush.BrushStrokeGame;
import com.cnhsk.community.game.catchgame.CharCatchGame;
import com.cnhsk.community.game.dart.FlyingDartGame;
import com.cnhsk.community.game.pinyin.PinyinMatchGame;
import com.cnhsk.community.game.radical.RadicalMatchGame;
import com.cnhsk.community.game.rain.CharRainGame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The ranked rules of each game: round length and how many wrong answers a round tolerates. */
class GameRulesTest {
    /** Games that end on lives or on finishing every item still need a ceiling so a round cannot hang. */
    private static final int ROUND_CEILING_SECONDS = 600;

    @Test void inkJarIsASixtySecondWritingRoundWhoseInkTheClientCounts() {
        var game = new CharRainGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(60);
        assertThat(game.maxLives()).isZero();
        assertThat(game.uiConfig()).containsEntry("inkLives", 3).containsEntry("strokeOrder", true)
                .containsEntry("startGuide", false);
    }

    @Test void pinyinMatchIsFiveBoardsWithThreeLives() {
        var game = new PinyinMatchGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(ROUND_CEILING_SECONDS);
        assertThat(game.maxLives()).isEqualTo(3);
    }

    @Test void radicalMatchIsFifteenQuestionsWithThreeLives() {
        var game = new RadicalMatchGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(ROUND_CEILING_SECONDS);
        assertThat(game.maxLives()).isEqualTo(3);
    }

    @Test void characterCatchIsSixtySecondsWithoutAPerQuestionTimer() {
        var game = new CharCatchGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(60);
        assertThat(game.maxLives()).isZero();
        assertThat(game.uiConfig()).doesNotContainKey("perItemMs").containsEntry("gridColumns", 3);
    }

    @Test void characterRainFallsFasterEachCharacterAndEndsOnThreeLives() {
        var game = new BrushStrokeGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(ROUND_CEILING_SECONDS);
        assertThat(game.maxLives()).isEqualTo(3);
        assertThat(game.uiConfig()).containsEntry("fallStartMs", 10000).containsEntry("fallStepMs", 100)
                .containsEntry("fallMinMs", 4000).containsEntry("freeWriting", true);
    }

    @Test void flyingDartIsSixtySecondsWithThreeMisses() {
        var game = new FlyingDartGame();
        assertThat(game.timeLimitSeconds()).isEqualTo(60);
        assertThat(game.maxLives()).isEqualTo(3);
    }
}
