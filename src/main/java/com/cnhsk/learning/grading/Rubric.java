package com.cnhsk.learning.grading;

import java.util.List;

/**
 * Rubrics of the competency tree v2.0 (appendix E). LEGACY is the four 0–25 marks used before; it is still read
 * for requests graded that way but never produces evidence.
 */
public enum Rubric {
    /** Speaking, scale 0–3: C1 task, C2 pronunciation, C3 vocabulary, C4 grammar, C5 fluency. */
    R_NOI(3, List.of("C1", "C2", "C3", "C4", "C5")),
    /** Writing, scale 0–5: C1 task, C2 grammar, C3 vocabulary, C4 characters and layout. */
    R_VIET(5, List.of("C1", "C2", "C3", "C4")),
    LEGACY(25, List.of());

    private final int max;
    private final List<String> criteria;

    Rubric(int max, List<String> criteria) {
        this.max = max;
        this.criteria = criteria;
    }

    public int max() { return max; }
    public List<String> criteria() { return criteria; }
}
