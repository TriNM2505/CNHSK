package com.cnhsk.community.game.radical;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

class RadicalMatchGameTest {
    private final RadicalMatchGame game = new RadicalMatchGame();

    @ParameterizedTest
    @CsvSource({"7,0", "8,8", "14,14", "22,15"})
    void AT09_roundSizeAndAvailabilityUseTheSameThresholds(int count, int expected) {
        var context = context(cards(count, "氵", "扌", "日", "木"));
        var items = game.buildRound(context, new Random(814));

        assertThat(game.isAvailable(context)).isEqualTo(expected > 0);
        assertThat(game.code()).isEqualTo(GameCode.RADICAL_MATCH);
        assertThat(game.timeLimitSeconds()).isEqualTo(90);
        assertThat(game.pointsPerCorrect()).isEqualTo(10);
        assertThat(game.uiConfig()).isEmpty();
        assertThat(items).hasSize(expected);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        assertThat(items).allSatisfy(item -> {
            int index = item.lexemeId().intValue();
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.RADICAL_CHOICE);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.CHARACTER);
            assertThat(item.prompt().text()).isEqualTo("字" + index);
            assertThat(item.prompt().hint()).isEqualTo("Nghĩa mẫu " + index);
            assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(700, 0, false));
            assertThat(item.options()).extracting(PracticeApi.Option::key).containsExactly("A", "B", "C", "D");
            assertThat(item.options()).extracting(PracticeApi.Option::text)
                    .containsExactlyInAnyOrder("氵", "扌", "日", "木");
            assertThat(item.options()).contains(new PracticeApi.Option(item.answer(),
                    List.of("氵", "扌", "日", "木").get(index % 4)));
        });
    }

    @ParameterizedTest
    @CsvSource({"氵,冫", "扌,才", "亻,彳", "日,曰", "木,禾", "礻,衤", "宀,冖", "艹,竹", "讠,言", "钅,金"})
    void AT10_usesLookalikesInBothDirections(String first, String second) {
        var pool = cards(8, first, second, "人", "口", "石");
        var items = game.buildRound(context(pool), new Random(816));
        assertThat(items).hasSize(8);
        for (var item : items) {
            String correct = pool.get(item.lexemeId().intValue()).radical();
            String preferred = correct.equals(first) ? second : first;
            if (correct.equals(first) || correct.equals(second)) {
                assertThat(item.options()).extracting(PracticeApi.Option::text).contains(preferred).doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void AT10_fillsAllDistractorsFromAvailableRadicalsWhenLookalikeIsAbsent() {
        var items = game.buildRound(context(cards(8, "氵", "扌", "日", "木", "口")), new Random(16));
        assertThat(items).hasSize(8);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.options()).hasSize(4);
            assertThat(item.options()).extracting(PracticeApi.Option::text)
                    .doesNotHaveDuplicates().doesNotContain("冫");
        });
    }

    @Test
    void AT11_whitespaceVariantsDoNotIncreaseTheNumberOfRadicals() {
        var pool = new ArrayList<>(cards(10, "氵", "氵", "木", "日"));
        pool.set(1, card(1, "CHAR", " 氵 "));
        pool.set(5, card(5, "CHAR", "　氵 "));
        assertUnavailable(pool);
        pool.set(1, card(1, "CHAR", "冫"));
        var items = game.buildRound(context(pool), new Random(4));
        assertThat(items).hasSize(10);
        assertThat(items).allSatisfy(item -> assertThat(item.options()).extracting(PracticeApi.Option::text)
                .containsExactlyInAnyOrder("氵", "冫", "木", "日"));
    }

    @Test
    void blankRadicalsAndDuplicateIdsDoNotSatisfyTheCharacterMinimum() {
        var pool = new ArrayList<>(cards(7, "氵", "冫", "木", "日"));
        pool.add(card(0, "CHAR", "口"));
        pool.add(card(8, "CHAR", ""));
        pool.add(card(9, "CHAR", "　 \t"));
        pool.add(card(10, "CHAR", null));
        pool.add(card(11, "WORD", "口"));
        assertUnavailable(pool);
    }

    private void assertUnavailable(List<LexemeCard> pool) {
        var context = context(pool);
        assertThat(game.isAvailable(context)).isFalse();
        assertThat(game.buildRound(context)).isEmpty();
    }

    private static GameContext context(List<LexemeCard> pool) {
        return new GameContext(21L, 3, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR"), Set.of(3), false, true, false, 21L, 200));
            return pool;
        });
    }

    private static List<LexemeCard> cards(int count, String... radicals) {
        return IntStream.range(0, count)
                .mapToObj(index -> card(index, "CHAR", radicals[index % radicals.length])).toList();
    }

    private static LexemeCard card(int index, String kind, String radical) {
        return new LexemeCard(index, kind, "字" + index, null, null, "Nghĩa mẫu " + index,
                Set.of(), radical, null, null, 3);
    }
}
