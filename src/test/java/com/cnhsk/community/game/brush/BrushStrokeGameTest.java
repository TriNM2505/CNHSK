package com.cnhsk.community.game.brush;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.PracticeApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrushStrokeGameTest {
    private final BrushStrokeGame game = new BrushStrokeGame();

    @Test
    void AT20_buildsSixtyFirstStrokeItemsFromEightyCharacters() {
        var items = game.buildRound(context(cards(80)));
        var config = game.uiConfig();
        assertThat(items).hasSize(60);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        assertThat(config).containsEntry("requiredStrokes", 1)
                .containsEntry("fallStartMs", 6000).containsEntry("fallStepMs", 150)
                .containsEntry("fallMinMs", 3000);
        assertThat(IntStream.of(0, 20, 25).map(index -> Math.max((int) config.get("fallMinMs"),
                (int) config.get("fallStartMs") - index * (int) config.get("fallStepMs"))).boxed().toList())
                .containsExactly(6000, 3000, 3000);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.TRACE_CHAR);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.WRITING);
            assertThat(item.prompt().text()).isEqualTo("字" + item.lexemeId());
            assertThat(item.prompt().hint()).isEqualTo("chữ mẫu");
            assertThat(item.prompt().pinyin()).isNull();
            assertThat(item.options()).isNull();
            assertThat(item.answer()).isEqualTo("PASS");
            assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(300, 0, false));
        });
    }

    @Test
    void availabilityRequiresTwentyDistinctCharactersWithStrokeData() {
        var pool = new ArrayList<>(cards(19));
        pool.add(card(1, "CHAR", 5));
        pool.add(card(20, "CHAR", null));
        pool.add(card(21, "WORD", 4));
        pool.add(card(22, "CHAR", 0));

        assertThat(game.isAvailable(context(pool))).isFalse();
        assertThat(game.buildRound(context(pool))).isEmpty();
        pool.add(card(23, "CHAR", 4));
        assertThat(game.isAvailable(context(pool))).isTrue();
        assertThat(game.buildRound(context(pool))).hasSize(20);
    }

    private static GameContext context(List<LexemeCard> pool) {
        return new GameContext(11L, 2, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR"), Set.of(2), false, false, true, 11L, 200));
            return pool;
        });
    }

    private static List<LexemeCard> cards(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> card(id, "CHAR", 4)).toList();
    }

    private static LexemeCard card(int id, String kind, Integer strokes) {
        return new LexemeCard(id, kind, "字" + id, "zì", "zi4", "chữ mẫu", Set.of(), null, strokes, null, 2);
    }
}
