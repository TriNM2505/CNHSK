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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FlyingDartGameTest {
    private final FlyingDartGame game = new FlyingDartGame();

    @Test
    void wordsAreSelectedBeforeCharactersWithinTheSharedPool() {
        var pool = new ArrayList<>(cards(0, 40, "CHAR", "zì"));
        pool.addAll(cards(40, 30, "WORD", "xuéxí"));
        var items = game.buildRound(context(pool));

        assertThat(items).hasSize(60);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        assertThat(items.subList(0, 30)).extracting(PracticeApi.ServedItem::lexemeId).allMatch(id -> id >= 40);
        assertThat(items.subList(30, 60)).extracting(PracticeApi.ServedItem::lexemeId).allMatch(id -> id < 40);
        assertThat(game.uiConfig()).containsEntry("maxInputLength", 40).containsEntry("fallStartMs", 6000)
                .containsEntry("fallStepMs", 150).containsEntry("fallMinMs", 3000);
    }

    @ParameterizedTest
    @CsvSource({"zì,zi,550", "xuéxí,xuexi,700", "xué xí,xuexi,700", "xue xi,xuexi,700", "lǜ,lv,550"})
    void durationAndTonelessAcceptanceComeFromStoredSyllables(String pinyin, String withoutTones, int duration) {
        var items = game.buildRound(context(cards(1, 20, "WORD", pinyin)));
        assertThat(items).hasSize(20).allSatisfy(item -> {
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.PINYIN_INPUT);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.VOCAB);
            assertThat(item.prompt().text()).isEqualTo("詞" + item.lexemeId());
            assertThat(item.prompt().hint()).isEqualTo("từ mẫu");
            assertThat(item.prompt().pinyin()).isNull();
            assertThat(item.answer()).isNull();
            assertThat(item.accept()).containsExactly(pinyin, withoutTones);
            assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(duration, 0, false));
        });
    }

    @Test
    void missingInvalidAndAmbiguousPinyinDoNotCountTowardsAvailability() {
        var pool = new ArrayList<>(cards(1, 19, "WORD", "xué xí"));
        pool.add(card(21, "WORD", null));
        pool.add(card(22, "WORD", "xue2xi"));
        pool.add(card(23, "WORD", "?"));
        pool.add(card(24, "WORD", "nǚér"));
        pool.add(card(1, "WORD", "xué xí"));

        assertThat(game.isAvailable(context(pool))).isFalse();
        assertThat(game.buildRound(context(pool))).isEmpty();
        pool.add(card(25, "CHAR", "zì"));
        assertThat(game.isAvailable(context(pool))).isTrue();
        assertThat(game.buildRound(context(pool))).hasSize(20);
    }

    private static GameContext context(List<LexemeCard> pool) {
        return new GameContext(11L, null, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("WORD", "CHAR"), Set.of(), false, false, false, 11L, 200));
            return pool;
        });
    }

    private static List<LexemeCard> cards(int first, int count, String kind, String pinyin) {
        return IntStream.range(first, first + count).mapToObj(id -> card(id, kind, pinyin)).toList();
    }

    private static LexemeCard card(int id, String kind, String pinyin) {
        return new LexemeCard(id, kind, "詞" + id, pinyin, null, "từ mẫu", Set.of(), null, null, null, 1);
    }
}
