package com.cnhsk.learning.practice;

public enum ItemType {
    STUDY_CARD(false),
    MEANING_CHOICE(true),
    AUDIO_WORD_CHOICE(true),
    CLOZE_CHOICE(true),
    PINYIN_INPUT(true),
    LESSON_READ(false),
    GRAMMAR_CHOICE(true);

    private final boolean gradable;

    ItemType(boolean gradable) {
        this.gradable = gradable;
    }

    public boolean gradable() {
        return gradable;
    }
}
