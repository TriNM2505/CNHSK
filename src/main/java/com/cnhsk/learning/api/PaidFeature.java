package com.cnhsk.learning.api;

/** Stable billing feature keys stored in plan benefits and monthly usage. */
public enum PaidFeature {
    AI_GENERATE, TRANSLATE, ASSISTANT, GRADING,
    VOCAB, GRAMMAR, PRONUNCIATION, HANZI;

    /** Learning features have their own free allowance and are blocked, not charged, once it runs out. */
    public boolean learning() {
        return this == VOCAB || this == GRAMMAR || this == PRONUNCIATION || this == HANZI;
    }
}
