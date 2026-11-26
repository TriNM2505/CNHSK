package com.cnhsk.learning.practice;

public enum ItemType {
    STUDY_CARD(false),
    MEANING_CHOICE(true),
    AUDIO_WORD_CHOICE(true),
    CLOZE_CHOICE(true),
    PINYIN_INPUT(true),
    PRON_CHOICE(true),
    CHAR_MEANING_CHOICE(true),
    AUDIO_CHAR_CHOICE(true),
    WRITING_RESULT(false),
    LESSON_READ(false),
    GRAMMAR_CHOICE(true),
    QUESTION_CHOICE(true),
    /** Bank question answered by putting fragments in order; the answer is the keys in that order. */
    QUESTION_ORDER(true),
    /** Bank question answered by typing the missing characters. */
    QUESTION_FILL(true),
    FLASHCARD_RATING(false),
    TRACE_CHAR(true),
    PINYIN_PAIR(true),
    RADICAL_CHOICE(true),
    CATCH_CHOICE(true),
    SINO_VIET_INPUT(true);

    private final boolean gradable;

    ItemType(boolean gradable) {
        this.gradable = gradable;
    }

    public boolean gradable() {
        return gradable;
    }
}
