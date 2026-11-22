package com.cnhsk.community.game.pinyin;

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

class PinyinMatchGameTest {
    private final PinyinMatchGame game = new PinyinMatchGame();

    @Test
    void AT05_onlyWordsWithAUsablePronunciationCount() {
        var invalid = new ArrayList<LexemeCard>();
        for (int index = 0; index < 10; index++) {
            invalid.add(card(index, "CHAR", "字", "zì", "zi4"));
        }
        assertThat(game.isAvailable(context(invalid))).isFalse();
        assertThat(game.buildRound(context(invalid))).isEmpty();

        invalid.addAll(uniqueCards(5));
        invalid.add(card(30, "WORD", "多音", " ", "duo1yin1"));
        invalid.add(card(31, "WORD", "多讀", "duō dú", null));
        invalid.add(card(32, "WORD", "詞", "cí", "ci2"));
        assertThat(game.isAvailable(context(invalid))).isFalse();
        assertThat(game.buildRound(context(invalid))).isEmpty();
    }

    @Test
    void AT06_oneThousandSeededRoundsHaveDistinctKeysAndWordsInEveryBoard() {
        var pool = IntStream.range(0, 30).mapToObj(index -> word(index, index % 6)).toList();
        // Six pronunciation groups of five words fill exactly five boards.
        var random = new Random(810603L);
        var context = context(pool);
        for (int round = 0; round < 1_000; round++) {
            var items = game.buildRound(context, random);
            assertThat(items).hasSize(30);
            assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
            for (int index = 0; index < items.size(); index += 6) {
                var board = items.subList(index, index + 6);
                assertThat(board).extracting(item -> item.prompt().text()).doesNotHaveDuplicates();
                assertThat(board).extracting(item -> pool.get(item.lexemeId().intValue()).pinyinKey())
                        .doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void alternativePronunciationsOfTheSameTextDoNotCountAsTwoWords() {
        var pool = new ArrayList<>(uniqueCards(5));
        var first = pool.get(0);
        pool.add(card(100, "WORD", first.text(), "chóng fù", "chong2fu4"));
        assertThat(game.isAvailable(context(pool))).isFalse();
        assertThat(game.buildRound(context(pool), new Random(8))).isEmpty();
        pool.add(word(5, 5));
        var items = game.buildRound(context(pool), new Random(8));

        assertThat(items).hasSize(6);
        assertThat(items).extracting(item -> item.prompt().text()).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @CsvSource({"6,1", "14,2", "18,3", "40,5"})
    void AT07_AT08_boardsShareShuffledOptionsAndListExactlyTheServedNumbers(int size, int count) {
        var items = game.buildRound(context(uniqueCards(size)), new Random(115));
        assertThat(game.code()).isEqualTo(GameCode.PINYIN_MATCH);
        assertThat(game.timeLimitSeconds()).isEqualTo(600);
        assertThat(game.pointsPerCorrect()).isEqualTo(10);
        assertThat(items).hasSize(count * 6);
        var expectedBoards = IntStream.range(0, count)
                .mapToObj(index -> IntStream.rangeClosed(index * 6 + 1, index * 6 + 6).boxed().toList())
                .toList();
        assertThat(game.uiConfig(items)).containsEntry("boards", expectedBoards);
        for (int offset = 0; offset < items.size(); offset += 6) {
            var board = items.subList(offset, offset + 6);
            var options = board.get(0).options();
            assertThat(options).extracting(PracticeApi.Option::key).containsExactly("A", "B", "C", "D", "E", "F");
            assertThat(board).extracting(PracticeApi.ServedItem::answer).doesNotHaveDuplicates();
            assertThat(board).allSatisfy(item -> {
                assertThat(item.options()).isEqualTo(options);
                assertThat(item.type()).isEqualTo(PracticeApi.ItemType.PINYIN_PAIR);
                assertThat(item.skill()).isEqualTo(PracticeApi.Skill.VOCAB);
                assertThat(item.prompt().pinyin()).isNull();
                assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(600, 0, false));
                assertThat(options).contains(new PracticeApi.Option(item.answer(), "pīn " + item.lexemeId()));
            });
        }
    }

    @Test
    void poolCapacityAccountsForPronunciationGroupsBeforeCreatingBoards() {
        var pool = IntStream.range(0, 24).mapToObj(index -> word(index, Math.max(0, index - 18))).toList();
        assertThat(game.buildRound(context(pool), new Random(13))).hasSize(6);
    }

    private static GameContext context(List<LexemeCard> cards) {
        return new GameContext(21L, 3, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("WORD"), Set.of(3), false, false, false, 21L, 200));
            return cards;
        });
    }

    private static List<LexemeCard> uniqueCards(int count) {
        return IntStream.range(0, count).mapToObj(index -> word(index, index)).toList();
    }

    private static LexemeCard word(int index, int pronunciation) {
        return card(index, "WORD", "詞" + (char) (0x4e00 + index), "pīn " + pronunciation, "pin1" + pronunciation);
    }

    private static LexemeCard card(int id, String kind, String text, String pinyin, String key) {
        return new LexemeCard(id, kind, text, pinyin, key, "Từ mẫu", Set.of(), null, null, null, 3);
    }
}
