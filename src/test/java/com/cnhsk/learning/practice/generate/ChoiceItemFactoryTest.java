package com.cnhsk.learning.practice.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.ServedItemView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
class ChoiceItemFactoryTest {

    private static final long SEED = 42;
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Map<String, List<Lexeme>> pools = new HashMap<>();
    private final List<String> statements = new ArrayList<>();
    private final List<Integer> limits = new ArrayList<>();
    private final List<Integer> offsets = new ArrayList<>();
    private ChoiceItemFactory factory;
    private Lexeme target;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        factory = new ChoiceItemFactory(entityManager, new Random(SEED));
        target = word(1, "我", "tôi", "wǒ", 1);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenAnswer(call -> {
            String statement = call.getArgument(0);
            statements.add(statement);
            TypedQuery<Long> query = mock(TypedQuery.class, RETURNS_SELF);
            when(query.getSingleResult()).thenReturn((long) pool(statement).size());
            return query;
        });
        when(entityManager.createQuery(anyString(), eq(Lexeme.class))).thenAnswer(call -> {
            String statement = call.getArgument(0);
            statements.add(statement);
            TypedQuery<Lexeme> query = mock(TypedQuery.class, RETURNS_SELF);
            AtomicInteger offset = new AtomicInteger();
            AtomicInteger limit = new AtomicInteger();
            when(query.setFirstResult(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(value -> {
                offset.set(value.getArgument(0));
                offsets.add(offset.get());
                return query;
            });
            when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(value -> {
                limit.set(value.getArgument(0));
                limits.add(limit.get());
                return query;
            });
            when(query.getResultList()).thenAnswer(value -> {
                assertThat(limit.get()).isBetween(1, ChoiceItemFactory.PAGE_SIZE);
                List<Lexeme> candidates = pool(statement);
                return new ArrayList<>(candidates.subList(offset.get(), Math.min(candidates.size(), offset.get() + limit.get())));
            });
            return query;
        });
    }

    @Test
    void FR011_AT19_oneHundredMeaningChoicesNeverContainTwoFatherAnswers() {
        target = word(1, "爸爸", "bố", "bàba", 1);
        pools.put("topic", List.of(word(2, "父亲", "bố", "fùqīn", 1),
                word(3, "妈妈", "mẹ", "māma", 1), word(4, "朋友", "bạn", "péngyou", 1),
                word(5, "老师", "giáo viên", "lǎoshī", 1)));
        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, 10L, ItemType.MEANING_CHOICE, 4).orElseThrow();
            assertThat(result.options()).filteredOn(option -> option.text().equals("bố")).hasSize(1);
            assertCorrect(result, "bố");
            assertThat(result.mixedDistractors()).isFalse();
        }
    }

    @Test
    void FR011_AT20_oneHundredListeningChoicesRejectHomophonesButKeepDifferentTones() {
        target = word(1, "是", "là", "shì", 1);
        pools.put("topic", List.of(word(2, "事", "việc", "shì", 1), word(3, "时", "thời gian", "shí", 1),
                word(4, "人", "người", "rén", 1), word(5, "我", "tôi", "wǒ", 1)));
        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, 10L, ItemType.AUDIO_WORD_CHOICE, 4).orElseThrow();
            assertThat(result.options()).extracting(ServedItem.Option::text).contains("是", "时").doesNotContain("事");
            assertCorrect(result, "是");
        }
    }

    @Test
    void FR011_AT21_smallTopicUsesSameLevelDistractorsAndMarksMixed() {
        pools.put("topic", List.of(word(2, "你", "bạn", "nǐ", 1), word(3, "他", "anh ấy", "tā", 1)));
        pools.put("level", IntStream.rangeClosed(4, 8).mapToObj(id -> word(id, "词" + id, "ngoài chủ đề " + id, "cí", 1)).toList());
        var result = factory.create(target, 10L, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).hasSize(4);
        assertThat(result.options()).extracting(ServedItem.Option::text).contains("tôi", "bạn", "anh ấy");
        assertThat(result.mixedDistractors()).isTrue();
        assertThat(statements).noneMatch(statement -> statement.contains("l.hskLevel <>"));
    }

    @Test
    void FR011_AT21_threeWordsInWholePoolReturnsEmptyAndWarnsOnlyWithLexemeId(CapturedOutput output) {
        pools.put("level", List.of(word(2, "你", "bạn", "nǐ", 1), word(3, "他", "anh ấy", "tā", 1)));
        assertThat(factory.create(target, null, ItemType.MEANING_CHOICE, 4)).isEmpty();
        assertThat(output.getOut()).contains("WARN", "lexemeId=1").doesNotContain("我", "tôi", "wǒ", "correctAnswer");
    }

    @Test
    void FR011_topicTierHasPriorityOverSameLevelAndOtherLevels() {
        pools.put("topic", alternatives(2, 4, 1));
        pools.put("level", alternatives(5, 8, 1));
        pools.put("other", alternatives(9, 12, 9));
        var result = factory.create(target, 10L, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).containsExactlyInAnyOrder("tôi", "nghĩa 2", "nghĩa 3", "nghĩa 4");
        assertThat(statements).allMatch(statement -> statement.contains(" and exists ("));
        assertThat(result.mixedDistractors()).isFalse();
    }

    @Test
    void FR011_sameLevelHasPriorityAndOtherLevelsOnlySupplyTheRemainingOptions() {
        pools.put("topic", List.of());
        pools.put("level", alternatives(2, 2, 1));
        pools.put("other", alternatives(3, 5, 9));
        var result = factory.create(target, 10L, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).contains("tôi", "nghĩa 2");
        assertThat(result.mixedDistractors()).isTrue();
        assertThat(statements).anyMatch(statement -> statement.contains("l.hskLevel = :level"))
                .anyMatch(statement -> statement.contains("l.hskLevel <> :level"));
        assertThat(statements.stream().filter(statement -> statement.contains("l.kind = :kind")))
                .allMatch(statement -> statement.contains("and not exists ("));
    }

    @Test
    void FR011_noTopicDoesNotSetMixedAndUsesTheSameLevelFirst() {
        pools.put("level", alternatives(2, 4, 1));
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.mixedDistractors()).isFalse();
        assertThat(statements).noneMatch(statement -> statement.contains("TopicItem"));
        assertCorrect(result, "tôi");
    }

    @Test
    void FR011_allMeaningFragmentsAreComparedWhileDisplayUsesOnlyFirstSemicolonPart() {
        target = word(1, "我", " Tôi, ta ; mình ", "wǒ", 1);
        pools.put("level", List.of(word(2, "你", "bạn; ta", "nǐ", 1), word(3, "他", "anh ấy, mình", "tā", 1),
                word(4, "朋友", "bạn bè", "péngyou", 1), word(5, "老师", "giáo viên", "lǎoshī", 1),
                word(6, "学生", "học sinh", "xuésheng", 1)));
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text)
                .containsExactlyInAnyOrder("Tôi, ta", "bạn bè", "giáo viên", "học sinh");
        assertCorrect(result, "Tôi, ta");
    }

    @Test
    void FR011_distractorsCannotRepeatIdTextOrOverlapEachOthersMeanings() {
        pools.put("level", List.of(word(1, "假", "sai id", "jiǎ", 1), word(2, "我", "sai chữ", "wǒ", 1),
                word(3, "妈妈", "mẹ; má", "māma", 1), word(4, "母亲", "má", "mǔqīn", 1),
                word(5, "老师", "giáo viên", "lǎoshī", 1), word(6, "学生", "học sinh", "xuésheng", 1),
                word(7, "朋友", "bạn bè", "péngyou", 1)));
        for (int round = 0; round < 100; round++) {
            var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
            List<String> values = result.options().stream().map(ServedItem.Option::text).toList();
            assertThat(values).doesNotContain("sai id", "sai chữ");
            assertThat(values.contains("mẹ") && values.contains("má")).isFalse();
        }
    }

    @Test
    void FR011_homophoneComparisonUsesExistingNumberedPinyinAcrossSpacingAndCase() {
        target = word(1, "我们", "chúng tôi", "WǑ MEN", 1);
        pools.put("level", List.of(word(2, "我门", "âm kiểm thử", "wǒmen", 1),
                word(3, "你", "bạn", "nǐ", 1), word(4, "他", "anh ấy", "tā", 1), word(5, "人", "người", "rén", 1)));
        var result = factory.create(target, null, ItemType.AUDIO_WORD_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).contains("我们").doesNotContain("我门");
    }

    @Test
    void FR011_clozeUsesHanziWithoutTheListeningHomophoneFilter() {
        target = word(1, "是", "là", "shì", 1);
        pools.put("level", List.of(word(2, "事", "việc", "shì", 1), word(3, "人", "người", "rén", 1),
                word(4, "我", "tôi", "wǒ", 1)));
        var result = factory.create(target, null, ItemType.CLOZE_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text).containsExactlyInAnyOrder("是", "事", "人", "我");
        assertCorrect(result, "是");
    }

    @Test
    void FR011_pagingContinuesBeyondRejectedPagesAndNeverMaterializesAnUnboundedPool() {
        List<Lexeme> candidates = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            candidates.add(word(index + 2, "词" + index, index < 3 ? "hợp lệ " + index : "tôi", "cí", 1));
        }
        pools.put("level", candidates);
        assertThat(new Random(SEED).nextInt(200)).isGreaterThan(3);
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::text)
                .containsExactlyInAnyOrder("tôi", "hợp lệ 0", "hợp lệ 1", "hợp lệ 2");
        assertThat(limits).hasSizeGreaterThan(1).allSatisfy(limit -> assertThat(limit).isBetween(1, 64));
        assertThat(offsets).contains(0);
        assertThat(statements).noneMatch(statement -> statement.contains("extra"));
    }

    @Test
    void FR011_injectedSeedMakesRepeatedGenerationReproducible() {
        pools.put("level", alternatives(2, 15, 1));
        List<ChoiceItemFactory.ChoiceOptions> first = IntStream.range(0, 20)
                .mapToObj(index -> factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow()).toList();
        ChoiceItemFactory replay = new ChoiceItemFactory(entityManager, new Random(SEED));
        List<ChoiceItemFactory.ChoiceOptions> second = IntStream.range(0, 20)
                .mapToObj(index -> replay.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow()).toList();
        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void FR011_keysAreAssignedAfterShuffleAndTheCorrectKeyStillMatches(int count) {
        pools.put("level", alternatives(2, 6, 1));
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, count).orElseThrow();
        assertThat(result.options()).extracting(ServedItem.Option::key).containsExactlyElementsOf(
                IntStream.range(0, count).mapToObj(index -> Character.toString((char) ('A' + index))).toList());
        assertCorrect(result, "tôi");
        assertThatThrownBy(() -> result.options().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void FR011_missingNormalizedMeaningUsesExistingTextNormalizerAndEmptyFragmentsAreIgnored() {
        target.setMeaningNorm(null);
        target.setMeaningVi(" Tôi ; , ");
        Lexeme same = word(2, "你", "tôi", "nǐ", 1);
        same.setMeaningNorm(null);
        pools.put("level", List.of(same, word(3, "人", "người;", "rén", 1), word(4, "朋友", "bạn bè,", "péngyou", 1),
                word(5, "老师", "giáo viên", "lǎoshī", 1)));
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        assertCorrect(result, "Tôi");
        assertThat(result.options()).filteredOn(option -> option.text().equals("tôi")).isEmpty();
    }

    @Test
    void FR011_nullLevelUsesSeparateNullAndNonNullTiers() {
        target.setHskLevel(null);
        pools.put("other", alternatives(2, 4, 9));
        assertThat(factory.create(target, null, ItemType.MEANING_CHOICE, 4)).isPresent();
        assertThat(statements).anyMatch(statement -> statement.contains("l.hskLevel is null"))
                .anyMatch(statement -> statement.contains("l.hskLevel is not null"));
    }

    @Test
    void FR011_correctAnswerIsOnlyCopiedIntoTheServerSnapshotAndNeverThePublicView() throws Exception {
        pools.put("level", alternatives(2, 4, 1));
        var result = factory.create(target, null, ItemType.MEANING_CHOICE, 4).orElseThrow();
        var snapshot = new ServedItem(1, ItemType.MEANING_CHOICE, com.cnhsk.learning.practice.Skill.VOCAB, target.getId(),
                new ServedItem.Prompt(target.getText(), null, null, null, null, null, null, null, null),
                result.options(), result.correctAnswer(), List.of("server-secret"), null, new ServedItem.Rules(800, 0, false));
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.valueToTree(snapshot).path("answer").asText()).isEqualTo(result.correctAnswer());
        var publicView = mapper.valueToTree(ServedItemView.from(snapshot));
        assertThat(publicView.has("answer")).isFalse();
        assertThat(publicView.has("accept")).isFalse();
        assertThat(publicView.has("correctAnswer")).isFalse();
        assertThat(mapper.valueToTree(result).has("correctAnswer")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7})
    void FR011_invalidOptionCountIsRejectedBeforeQuerying(int count) {
        assertThatThrownBy(() -> factory.create(target, null, ItemType.MEANING_CHOICE, count))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(statements).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ItemType.class, names = {"STUDY_CARD", "PINYIN_INPUT"})
    void FR011_nonChoiceTypesAreRejectedBeforeQuerying(ItemType type) {
        assertThatThrownBy(() -> factory.create(target, null, type, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThat(statements).isEmpty();
    }

    private List<Lexeme> pool(String statement) {
        String tier = statement.contains(" and exists (") ? "topic"
                : statement.contains("l.hskLevel = :level") || statement.contains("l.hskLevel is null")
                        && !statement.contains("l.hskLevel <>") ? "level" : "other";
        return pools.getOrDefault(tier, List.of());
    }

    private static List<Lexeme> alternatives(int first, int last, int level) {
        return IntStream.rangeClosed(first, last).mapToObj(id -> word(id, "词" + id, "nghĩa " + id, "cí", level)).toList();
    }

    private static Lexeme word(long id, String text, String meaning, String pinyin, int level) {
        Lexeme word = new Lexeme(LexemeKind.WORD, text, meaning);
        ReflectionTestUtils.setField(word, "id", id);
        word.setMeaningNorm(TextNormalizer.normalizeMeaning(meaning));
        word.setPinyin(pinyin);
        word.setHskLevel((short) level);
        return word;
    }

    private static void assertCorrect(ChoiceItemFactory.ChoiceOptions result, String text) {
        assertThat(result.options()).filteredOn(option -> option.key().equals(result.correctAnswer()))
                .extracting(ServedItem.Option::text).containsExactly(text);
    }
}
