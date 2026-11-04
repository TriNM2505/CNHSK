package com.cnhsk.learning.mastery;

import java.util.Arrays;

/** Persisted and derived MP-1.0 state codes. */
public enum MasteryState {
    NOT_YET("notyet"),
    INSUFFICIENT("insufficient"),
    REVIEW("review"),
    CONSOLIDATE("consolidate"),
    EXTEND("extend"),
    INFERRED("inferred");

    private final String code;

    MasteryState(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MasteryState fromCode(String code) {
        return Arrays.stream(values())
                .filter(state -> state.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mastery state code: " + code));
    }
}
