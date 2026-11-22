package com.cnhsk.community.game.dart;

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

class FlyingDartGameTest {
    private final FlyingDartGame game = new FlyingDartGame();

    @Test
    void asksForTheSinoVietnameseReadingOfEachCharacter() {
        var items = game.buildRound(context(cards(0, 80, "nhân")));

        assertThat(items).hasSize(60);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        assertThat(items).extracting(PracticeApi.ServedItem::no)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 60).boxed().toList());
        assertThat(items).allSatisfy(item -> {
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.SINO_VIET_INPUT);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.CHARACTER);
            assertThat(item.prompt().text()).isEqualTo("字" + item.lexemeId());
            assertThat(item.prompt().hint()).isNull();
            assertThat(item.answer()).isNull();
            assertThat(item.accept()).containsExactly("nhân");
        });
        assertThat(game.uiConfig()).containsEntry("maxInputLength", 20).doesNotContainKey("fallStartMs");
    }

    @Test
    void everyReadingOfAMultiReadingCharacterIsAccepted() {
        var items = game.buildRound(context(cards(0, 20, " nhân, nhơn ;nhân ")));
        assertThat(items).allSatisfy(item -> assertThat(item.accept()).containsExactly("nhân", "nhơn"));
    }

    @Test
    void charactersWithoutAReadingDoNotCountTowardsAvailability() {
        var pool = new ArrayList<>(cards(0, 19, "đại"));
        pool.add(card(30, null));
        pool.add(card(31, "  "));
        pool.add(card(0, "đại"));

        assertThat(game.isAvailable(context(pool))).isFalse();
        assertThat(game.buildRound(context(pool))).isEmpty();
        pool.add(card(32, "tiểu"));
        assertThat(game.isAvailable(context(pool))).isTrue();
        assertThat(game.buildRound(context(pool))).hasSize(20)
                .extracting(PracticeApi.ServedItem::lexemeId).doesNotContain(30L, 31L);
    }

    private static GameContext context(List<LexemeCard> pool) {
        return new GameContext(11L, 2, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR"), Set.of(2), false, false, false, 11L, 200));
            return pool;
        });
    }

    private static List<LexemeCard> cards(int first, int count, String reading) {
        return IntStream.range(first, first + count).mapToObj(id -> card(id, reading)).toList();
    }

    private static LexemeCard card(int id, String reading) {
        return new LexemeCard(id, "CHAR", "字" + id, "zì", null, "nghĩa mẫu", Set.of(), null, null, null, 2, reading);
    }
}
