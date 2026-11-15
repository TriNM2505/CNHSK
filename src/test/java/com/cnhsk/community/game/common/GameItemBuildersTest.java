package com.cnhsk.community.game.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.PracticeApi.Option;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GameItemBuildersTest {
    @Test
    void selectsEachQuestionIdOnlyOnceAndDoesNotChangeThePool() {
        var one = card(1, "甲", "jia3", "thứ nhất");
        var duplicate = card(1, "甲", "jia3", "thứ nhất");
        var two = card(2, "乙", "yi3", "thứ hai");
        var pool = List.of(one, duplicate, two);
        var picked = GameItemBuilders.selectQuestions(pool, 3, new Random(11));
        assertThat(picked).extracting(LexemeCard::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(GameItemBuilders.selectQuestions(pool, 1, new Random(11))).hasSize(1);
        assertThat(pool).containsExactly(one, duplicate, two);
    }

    @Test
    void meaningCandidatesExcludeSameIdTextOrAnySharedMeaningKey() {
        var correct = card(1, "父亲", "fu4qin1", "cha", "bố");
        var pool = List.of(
                card(1, "先生", "xian1sheng5", "thầy"),
                card(2, "父亲", "fu4qin1", "ông"),
                card(3, "爸爸", "ba4ba5", "bố", "ba"),
                card(4, "老师", "lao3shi1", "giáo viên"),
                card(5, "母亲", "mu3qin1", "mẹ"));
        var choice = GameItemBuilders.chooseOptions(correct, pool, GameItemBuilders.DistractorMode.MEANING,
                LexemeCard::text, 3, new Random(15)).orElseThrow();
        assertThat(choice.options()).extracting(Option::text).containsExactlyInAnyOrder("父亲", "老师", "母亲");
    }

    @Test
    void soundCandidatesExcludeHomophonesAndAllowTheSameSyllableWithADifferentTone() {
        var correct = card(1, "是", "shi4", "là");
        var sameTone = card(2, "事", "shi4", "việc");
        var differentTone = card(3, "诗", "shi1", "thơ");
        var choice = GameItemBuilders.chooseOptions(correct, List.of(sameTone, differentTone),
                GameItemBuilders.DistractorMode.SOUND, LexemeCard::text, 2, new Random(3)).orElseThrow();
        assertThat(choice.options()).extracting(Option::text).containsExactlyInAnyOrder("是", "诗");
    }

    @Test
    void returnsEmptyForMissingMatchKeysOrAnInsufficientPool() {
        var soundless = card(1, "字", null, "chữ");
        var correct = card(2, "是", "shi4", "là");
        var meaningless = card(3, "事", "shi4");
        assertThat(GameItemBuilders.chooseOptions(soundless, List.of(correct), GameItemBuilders.DistractorMode.SOUND,
                LexemeCard::text, 2, new Random(2))).isEmpty();
        assertThat(GameItemBuilders.chooseOptions(correct, List.of(soundless, meaningless), GameItemBuilders.DistractorMode.SOUND,
                LexemeCard::text, 2, new Random(2))).isEmpty();
        assertThat(GameItemBuilders.chooseOptions(correct, List.of(meaningless), GameItemBuilders.DistractorMode.MEANING,
                LexemeCard::text, 2, new Random(2))).isEmpty();
    }

    @Test
    void assignsKeysAfterShufflingWithoutPinningTheAnswer() {
        var answerKeys = IntStream.range(0, 20).mapToObj(seed -> {
            var choice = GameItemBuilders.shuffleOptions("正", List.of("一", "二", "三", "四", "五"), new Random(seed));
            assertThat(choice.options()).extracting(Option::key).containsExactly("A", "B", "C", "D", "E", "F");
            assertThat(choice.options()).filteredOn(option -> option.key().equals(choice.answer()))
                    .extracting(Option::text).containsExactly("正");
            return choice.answer();
        }).distinct().toList();
        assertThat(answerKeys).hasSizeGreaterThan(1);
    }

    private static LexemeCard card(long id, String text, String pinyinKey, String... meaningKeys) {
        return new LexemeCard(id, "CHAR", text, null, pinyinKey, null, Set.of(meaningKeys), null, null, null, 1);
    }
}
