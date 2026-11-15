package com.cnhsk.community.game.rain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CharRainGameTest {
    private final CharRainGame game = new CharRainGame();

    @Test
    void roundUsesTraceItemsAndPreservesPerCharacterDuration() {
        var items = game.buildRound(new GameContext(21L, 3, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR"), Set.of(3), false, false, true, 21L, 200));
            return cards(30);
        }));

        assertThat(game.code()).isEqualTo(GameCode.CHAR_RAIN);
        assertThat(game.skill()).isEqualTo(PracticeApi.Skill.WRITING);
        assertThat(game.timeLimitSeconds()).isEqualTo(180);
        assertThat(game.pointsPerCorrect()).isEqualTo(10);
        assertThat(game.uiConfig()).isEqualTo(Map.of("requiredStrokes", "ALL", "strokeOrder", true));
        assertThat(items).hasSize(10);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        assertThat(items).extracting(PracticeApi.ServedItem::no).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(items).allSatisfy(item -> {
            int id = item.lexemeId().intValue();
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.TRACE_CHAR);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.WRITING);
            assertThat(item.prompt().text()).isEqualTo("字" + id);
            assertThat(item.prompt().pinyin()).isEqualTo("zì");
            assertThat(item.prompt().hint()).isEqualTo("Chữ mẫu " + id);
            assertThat(item.options()).isNull();
            assertThat(item.answer()).isEqualTo("PASS");
            assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(id * 300, 0, false));
        });
    }

    @ParameterizedTest
    @CsvSource({"4,0", "5,5", "9,9"})
    void availabilityAndRoundSizeUseTheSameMinimum(int poolSize, int roundSize) {
        var context = new GameContext(null, null, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR"), Set.of(), false, false, true, null, 200));
            return cards(poolSize);
        });

        assertThat(game.isAvailable(context)).isEqualTo(roundSize > 0);
        assertThat(game.buildRound(context)).hasSize(roundSize);
    }

    @Test
    void duplicateCardsAndInvalidStrokeCountsDoNotMeetMinimum() {
        List<LexemeCard> pool = new ArrayList<>(cards(4));
        pool.add(card(1, 1));
        pool.add(card(5, null));
        pool.add(card(6, 0));
        var context = new GameContext(21L, null, query -> pool);

        assertThat(game.isAvailable(context)).isFalse();
        assertThat(game.buildRound(context)).isEmpty();
    }

    private static List<LexemeCard> cards(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> card(id, id)).toList();
    }

    private static LexemeCard card(int id, Integer strokes) {
        return new LexemeCard(id, "CHAR", "字" + id, "zì", "zi4", "Chữ mẫu " + id, Set.of(), null, strokes, null, 3);
    }
}
