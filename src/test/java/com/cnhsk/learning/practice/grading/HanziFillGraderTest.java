package com.cnhsk.learning.practice.grading;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HanziFillGraderTest {

    @Test
    void spacingWidthAndPunctuationDoNotCount() {
        assertThat(HanziFillGrader.matches(" 学习。", List.of("学习"))).isTrue();
        assertThat(HanziFillGrader.matches("学 习", List.of("学习"))).isTrue();
        assertThat(HanziFillGrader.matches("ＡＢ", List.of("ab"))).isTrue();
    }

    @Test
    void anyListedFormIsAccepted() {
        assertThat(HanziFillGrader.matches("學", List.of("学", "學"))).isTrue();
    }

    @Test
    void wrongOrEmptyAnswersFail() {
        assertThat(HanziFillGrader.matches("字", List.of("学"))).isFalse();
        assertThat(HanziFillGrader.matches("。", List.of("学"))).isFalse();
        assertThat(HanziFillGrader.matches(null, List.of("学"))).isFalse();
        assertThat(HanziFillGrader.matches("学", null)).isFalse();
    }
}
