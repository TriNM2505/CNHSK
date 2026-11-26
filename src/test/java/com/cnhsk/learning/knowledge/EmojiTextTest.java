package com.cnhsk.learning.knowledge;

import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.QuestionRules.Option;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmojiTextTest {

    @Test
    void recognisesPicturesMadeOfEmoji() {
        assertThat(EmojiText.isEmojiOnly("🐱")).isTrue();
        assertThat(EmojiText.isEmojiOnly(" ☕ ")).isTrue();
        assertThat(EmojiText.isEmojiOnly("🧑‍⚕️")).isTrue();
        assertThat(EmojiText.isEmojiOnly("👍🏽")).isTrue();
    }

    @Test
    void textIsNotAPicture() {
        assertThat(EmojiText.isEmojiOnly("猫")).isFalse();
        assertThat(EmojiText.isEmojiOnly("🐱 猫")).isFalse();
        assertThat(EmojiText.isEmojiOnly("Đúng")).isFalse();
        assertThat(EmojiText.isEmojiOnly("")).isFalse();
        assertThat(EmojiText.isEmojiOnly(null)).isFalse();
    }

    @Test
    void emojiOptionsCountAsPicturesOfAPictureQuestion() {
        var draft = new QuestionRules.Draft(QuestionType.IMAGE_MATCH, 1, 2, "我喜欢猫。",
                List.of(new Option("A", "🐱", true, null, null), new Option("B", "🐶", false, null, null)),
                null, "Lời giải", null, null, "TV1-001", List.of());
        assertThat(QuestionRules.validate(draft, Set.of("TV1-001")).errors()).isEmpty();
    }
}
