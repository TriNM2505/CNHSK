package com.cnhsk.learning.exam;

import java.util.List;
import java.util.UUID;

import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Mock exams follow the HSK paper, so they serve listening, picture, ordering and fill-in questions. */
class QuestionItemFactoryFullFormatTest {

    private final QuestionItemFactory factory = QuestionItemFactory.fullFormat();

    @Test
    void listeningQuestionIsSpokenNotShownAndPlaysTwice() {
        Question question = choice(QuestionType.TRUE_FALSE, "我喜欢喝茶。");
        question.setSkill("LISTENING");
        question.setImageUrl(" https://img.example/tea.png ");
        question.setOptions(List.of(option("A", "Đúng", true, null), option("B", "Sai", false, null)));

        ServedItem item = factory.create(question, 3).orElseThrow();

        assertThat(item.skill()).isEqualTo(Skill.LISTENING);
        assertThat(item.prompt().text()).isNull();
        assertThat(item.prompt().speakText()).isEqualTo("我喜欢喝茶。");
        assertThat(item.prompt().imageUrl()).isEqualTo("https://img.example/tea.png");
        assertThat(item.rules()).isEqualTo(new ServedItem.Rules(1500, 2, true));
        assertThat(item.answer()).isEqualTo("A");
    }

    @Test
    void listeningStatementMarkedWithAStarIsPrinted() {
        Question question = choice(QuestionType.TRUE_FALSE, "我今天很忙，不去看电影了。\n★ 他今天去看电影。");
        question.setSkill("LISTENING");
        question.setOptions(List.of(option("A", "Đúng", false, null), option("B", "Sai", true, null)));

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.prompt().text()).isEqualTo("★ 他今天去看电影。");
        assertThat(item.prompt().speakText()).isEqualTo("我今天很忙，不去看电影了。");
    }

    @Test
    void emojiPictureOfAListeningQuestionIsShownNotSpoken() {
        Question question = choice(QuestionType.TRUE_FALSE, "🐶\n猫");
        question.setSkill("LISTENING");
        question.setOptions(List.of(option("A", "Đúng", false, null), option("B", "Sai", true, null)));

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.prompt().text()).isEqualTo("🐶");
        assertThat(item.prompt().speakText()).isEqualTo("猫");
    }

    @Test
    void oneLineImportedStemStillSplitsPictureSpeechAndStatement() {
        assertThat(QuestionItemFactory.printed("🐱 我喜欢猫。 ★ 他喜欢狗。")).isEqualTo("🐱\n★ 他喜欢狗。");
        assertThat(QuestionItemFactory.spoken("🐱 我喜欢猫。 ★ 他喜欢狗。")).isEqualTo("我喜欢猫。");
        assertThat(QuestionItemFactory.printed("我喜欢猫。")).isNull();
    }

    @Test
    void pictureOptionsHideTheirDescription() {
        Question question = choice(QuestionType.IMAGE_MATCH, "他在做什么？");
        question.setImageUrl("https://img.example/stem.png");
        question.setOptions(List.of(option("A", "ảnh: uống trà", false, "https://img.example/a.png"),
                option("B", "ảnh: đọc sách", true, "https://img.example/b.png")));

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.options()).containsExactly(new ServedItem.Option("A", "", "https://img.example/a.png"),
                new ServedItem.Option("B", "", "https://img.example/b.png"));
        assertThat(item.prompt().text()).isEqualTo("他在做什么？");
        assertThat(item.skill()).isEqualTo(Skill.READING);
    }

    @Test
    void orderingQuestionAnswersWithKeysInOrder() {
        Question question = choice(QuestionType.SENTENCE_ORDER, "Sắp xếp thành câu");
        question.setSkill("WRITING");
        question.setOptions(List.of(option("A", "喜欢", false, null), option("B", "我", false, null),
                option("C", "看书", false, null)));
        question.setCorrectKey("BAC");

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.type()).isEqualTo(ItemType.QUESTION_ORDER);
        assertThat(item.skill()).isEqualTo(Skill.WRITING);
        assertThat(item.answer()).isEqualTo("BAC");
        assertThat(item.options()).extracting(ServedItem.Option::text).containsExactly("喜欢", "我", "看书");
    }

    @Test
    void orderingThatSkipsOrRepeatsAFragmentIsExcluded() {
        Question question = choice(QuestionType.SENTENCE_ORDER, "Sắp xếp thành câu");
        question.setOptions(List.of(option("A", "喜欢", false, null), option("B", "我", false, null)));
        question.setCorrectKey("AA");
        assertThat(factory.isUsable(question)).isFalse();
    }

    @Test
    void fillInAcceptsEveryListedForm() {
        Question question = choice(QuestionType.FILL_BLANK, "我们一起 ___（xué）习吧。");
        question.setSkill("WRITING");
        question.setOptions(List.of());
        question.setCorrectKey("学 | 學");

        ServedItem item = factory.create(question, 1).orElseThrow();

        assertThat(item.type()).isEqualTo(ItemType.QUESTION_FILL);
        assertThat(item.options()).isNull();
        assertThat(item.accept()).containsExactly("学", "學");
    }

    @Test
    void essayIsStillNotServed() {
        Question question = choice(QuestionType.ESSAY, "Viết đoạn văn");
        assertThat(factory.isUsable(question)).isFalse();
    }

    @Test
    void plainFactoryStillRefusesTheNewTypes() {
        Question question = choice(QuestionType.IMAGE_MATCH, "他在做什么？");
        question.setOptions(List.of(option("A", "x", true, "https://img.example/a.png"), option("B", "y", false, null)));
        assertThat(new QuestionItemFactory().isUsable(question)).isFalse();
    }

    private static Question choice(QuestionType type, String stem) {
        Question question = new Question("TEST-" + UUID.randomUUID(), type, (short) 1, stem);
        ReflectionTestUtils.setField(question, "id", 77L);
        question.setStatus(QuestionStatus.APPROVED);
        question.setExplanation("Lời giải");
        question.setOptions(List.of(option("A", "一", true, null), option("B", "二", false, null)));
        return question;
    }

    private static QuestionOption option(String key, String text, boolean correct, String image) {
        return new QuestionOption(key, text, correct, null, null, image);
    }
}
