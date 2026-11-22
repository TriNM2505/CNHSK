package com.cnhsk.community.game.catchgame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

class CharCatchGameTest {
    private final CharCatchGame game = new CharCatchGame();

    @ParameterizedTest
    @CsvSource({"7,0", "8,8", "12,12", "120,100"})
    void roundsUseSixOptionsAndRespectMinimumAndMaximum(int count, int expected) {
        var context = context(cards(count, true));
        var items = game.buildRound(context, new Random(519));
        assertThat(game.isAvailable(context, new Random(519))).isEqualTo(expected > 0);
        assertThat(game.code()).isEqualTo(GameCode.CHAR_CATCH);
        assertThat(game.timeLimitSeconds()).isEqualTo(60);
        assertThat(game.pointsPerCorrect()).isEqualTo(10);
        assertThat(game.uiConfig()).isEqualTo(Map.of("gridColumns", 3));
        assertThat(items).hasSize(expected);
        assertThat(items).extracting(PracticeApi.ServedItem::lexemeId).doesNotHaveDuplicates();
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            assertThat(item.no()).isEqualTo(index + 1);
            assertThat(item.type()).isEqualTo(PracticeApi.ItemType.CATCH_CHOICE);
            assertThat(item.skill()).isEqualTo(PracticeApi.Skill.LISTENING);
            assertThat(item.prompt().text()).isNull();
            assertThat(item.options()).extracting(PracticeApi.Option::key).containsExactly("A", "B", "C", "D", "E", "F");
            assertThat(item.options()).extracting(PracticeApi.Option::text).doesNotHaveDuplicates();
            assertThat(item.options()).contains(new PracticeApi.Option(item.answer(), "字" + item.lexemeId()));
            assertMode(item, index % 3);
        }
    }

    @Test
    void AT12_missingAudioUsesMeaningWithoutChangingSlotOrder() {
        var items = game.buildRound(context(cards(8, false)), new Random(520));
        assertThat(items).hasSize(8);
        for (int index = 0; index < items.size(); index++) {
            assertMode(items.get(index), index % 3 == 1 ? 1 : 0);
        }
    }

    @Test
    void rejectUnavailableSoundCandidatesWithoutChangingTheRequestedMode() {
        var pool = new ArrayList<>(cards(9, true));
        pool.set(0, card(0, "字0", null, null, "https://audio.example.test/a", "nghĩa0"));
        var context = context(pool);

        var items = IntStream.range(0, 64).mapToObj(seed -> game.buildRound(context, new Random(seed)))
                .filter(round -> round.stream().anyMatch(item -> item.lexemeId() == 0))
                .findFirst().orElseThrow();
        assertThat(items).hasSize(9);
        var item = items.stream().filter(value -> value.lexemeId() == 0).findFirst().orElseThrow();
        assertThat((item.no() - 1) % 3).isZero();
        assertMode(item, 0);
        assertMode(items.get(1), 1);
        assertMode(items.get(2), 2);
    }

    @Test
    void buildRoundReturnsEmptyWhenASecondDrawCannotReachEight() {
        var pool = new ArrayList<>(cards(8, false));
        pool.set(6, card(6, "字6", "pīn6", "pin6", null, null));
        pool.set(7, card(7, "字7", "pīn7", "pin7", null, null));
        var context = context(pool);
        int readySeed = IntStream.range(0, 128)
                .filter(seed -> game.isAvailable(context, new Random(seed))).findFirst().orElseThrow();
        int shortSeed = IntStream.range(0, 128)
                .filter(seed -> !game.isAvailable(context, new Random(seed))).findFirst().orElseThrow();
        assertThat(game.buildRound(context, new Random(readySeed))).hasSize(8);
        assertThat(game.buildRound(context, new Random(shortSeed))).isEmpty();
    }

    @Test
    void AT14_sameToneIsExcludedButDifferentToneIsKeptForPinyinAndAudio() {
        var pool = new ArrayList<>(cards(8, true));
        var target = card(0, "是", "shì", "shi4", "https://audio.example.test/shi", "là");
        var sameTone = card(1, "事", "shì", "shi4", "https://audio.example.test/shi", "việc");
        var otherTone = card(2, "诗", "shī", "shi1", "https://audio.example.test/shi1", "thơ");
        pool.set(0, target);
        pool.set(1, sameTone);
        pool.set(2, otherTone);
        pool.set(7, card(7, "字7", "pīn3", "pin3", "https://audio.example.test/a", "nghĩa7"));
        assertThat(target.pinyinKey()).isEqualTo(sameTone.pinyinKey()).isNotEqualTo(otherTone.pinyinKey());

        for (int mode : List.of(1, 2)) {
            var item = itemDrawnFor(context(pool), target.id(), mode);
            assertThat(item.options()).extracting(PracticeApi.Option::text).contains("是", "诗").doesNotContain("事");
            assertMode(item, mode);
        }
    }

    @Test
    void meaningExcludesCardsWithOverlappingKeys() {
        var pool = new ArrayList<>(cards(8, true));
        pool.set(0, card(0, "字0", "pīn0", "pin0", null, "gặp"));
        pool.set(1, new LexemeCard(1, "WORD", "字1", "pīn1", "pin1", "thấy",
                Set.of("thấy", "gặp"), null, null, null, 3));
        var item = itemDrawnFor(context(pool), 0, 0);
        assertThat(item.options()).extracting(PracticeApi.Option::text).contains("字0").doesNotContain("字1");
    }

    private PracticeApi.ServedItem itemDrawnFor(GameContext context, long id, int mode) {
        return IntStream.range(0, 128).mapToObj(seed -> game.buildRound(context, new Random(seed)))
                .flatMap(List::stream).filter(item -> item.lexemeId() == id && (item.no() - 1) % 3 == mode)
                .findFirst().orElseThrow();
    }

    private static void assertMode(PracticeApi.ServedItem item, int mode) {
        var prompt = item.prompt();
        assertThat(prompt.text()).isNull();
        if (mode == 0) assertThat(prompt.hint()).isNotBlank();
        else assertThat(prompt.hint()).isNull();
        if (mode == 1) assertThat(prompt.pinyin()).isNotBlank();
        else assertThat(prompt.pinyin()).isNull();
        if (mode == 2) assertThat(prompt.audioUrl()).isNotBlank();
        else assertThat(prompt.audioUrl()).isNull();
        assertThat(item.rules()).isEqualTo(new PracticeApi.Rules(300, mode == 2 ? 1 : 0, mode == 2));
    }

    private static GameContext context(List<LexemeCard> pool) {
        return new GameContext(21L, 3, query -> {
            assertThat(query).isEqualTo(new GameContentQuery(Set.of("CHAR", "WORD"), Set.of(3), false, false, false, 21L, 200));
            return pool;
        });
    }

    private static List<LexemeCard> cards(int count, boolean audio) {
        return IntStream.range(0, count).mapToObj(id -> card(id, "字" + id, "pīn" + id, "pin" + id,
                audio ? "https://audio.example.test/" + id : null, "nghĩa" + id)).toList();
    }

    private static LexemeCard card(long id, String text, String pinyin, String key, String audio, String meaning) {
        return new LexemeCard(id, id % 2 == 0 ? "CHAR" : "WORD", text, pinyin, key, meaning,
                meaning == null ? Set.of() : Set.of(meaning), null, null, audio, 3);
    }
}
