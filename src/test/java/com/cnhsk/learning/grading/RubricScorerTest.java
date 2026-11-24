package com.cnhsk.learning.grading;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.grading.RubricScorer.Evidence;
import com.cnhsk.learning.grading.RubricScorer.TaskTargets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricScorerTest {

    /** Appendix E.3: task NL3-006, all criteria 2/3, one confirmed E-NP-06 (moderate). */
    private static final TaskTargets KE_CUOI_TUAN = new TaskTargets("NL3-006", Map.of(
            "C2", List.of("NA1-006", "NA1-008"), "C3", List.of("TV2-008"), "C4", List.of("NP2-006", "NP2-013")));

    @Test
    void speakingExampleOfAppendixE3IsReproduced() {
        var result = RubricScorer.score(Rubric.R_NOI, Map.of("C1", 2, "C2", 2, "C3", 2, "C4", 2, "C5", 2),
                KE_CUOI_TUAN, List.of(new RubricScorer.TaggedError("E-NP-06", "NP2-013", 2)), true);
        assertThat(result.score100()).isEqualTo(67);
        assertThat(result.evidence()).containsExactlyInAnyOrder(
                new Evidence("NL3-006", "PRIMARY", 67, "C1+C5"),
                new Evidence("NA1-006", "SECONDARY", 67, "C2"),
                new Evidence("NA1-008", "SECONDARY", 67, "C2"),
                new Evidence("TV2-008", "SECONDARY", 67, "C3"),
                new Evidence("NP2-006", "SECONDARY", 67, "C4"),
                new Evidence("NP2-013", "SECONDARY", 67, "C4"),
                new Evidence("NP2-013", "ERROR", 0, "E-NP-06"));
    }

    @Test
    void pronunciationIsNotEvidencedWithoutTheOriginalAudio() {
        var result = RubricScorer.score(Rubric.R_NOI, Map.of("C1", 3, "C2", 3, "C3", 3, "C4", 3, "C5", 3),
                KE_CUOI_TUAN, List.of(), false);
        assertThat(result.evidence()).extracting(Evidence::kid).doesNotContain("NA1-006", "NA1-008");
        assertThat(result.score100()).isEqualTo(100);
    }

    @Test
    void writingUsesTheWritingRubricScaleAndCriteria() {
        TaskTargets task = new TaskTargets("NL3-008", Map.of("C2", List.of("NP3-004"), "C3", List.of("TV2-002"),
                "C4", List.of("CH3-010")));
        var result = RubricScorer.score(Rubric.R_VIET, Map.of("C1", 5, "C2", 3, "C3", 4, "C4", 0), task, List.of(), false);
        assertThat(result.score100()).isEqualTo(60);
        assertThat(result.evidence()).containsExactlyInAnyOrder(
                new Evidence("NL3-008", "PRIMARY", 100, "C1"),
                new Evidence("NP3-004", "SECONDARY", 60, "C2"),
                new Evidence("TV2-002", "SECONDARY", 80, "C3"),
                new Evidence("CH3-010", "SECONDARY", 0, "C4"));
    }

    @Test
    void minorErrorsAreFeedbackOnlyAndCommunicativeErrorsLandOnTheTaskNode() {
        var result = RubricScorer.score(Rubric.R_NOI, Map.of("C1", 1, "C2", 1, "C3", 1, "C4", 1, "C5", 1), KE_CUOI_TUAN,
                List.of(new RubricScorer.TaggedError("E-NP-09", "NP1-003", 1),
                        new RubricScorer.TaggedError("E-NL-01", null, 3)), true);
        assertThat(result.evidence()).filteredOn(e -> e.role().equals("ERROR"))
                .containsExactly(new Evidence("NL3-006", "ERROR", 0, "E-NL-01"));
    }

    @Test
    void freeWritingWithoutATaskOnlyEvidencesFixedErrorCodes() {
        var result = RubricScorer.score(Rubric.R_VIET, Map.of("C1", 4, "C2", 4, "C3", 4, "C4", 4), null,
                List.of(new RubricScorer.TaggedError("E-NP-01", "NP1-014", 3),
                        new RubricScorer.TaggedError("E-NL-02", null, 2)), false);
        assertThat(result.score100()).isEqualTo(80);
        assertThat(result.evidence()).containsExactly(new Evidence("NP1-014", "ERROR", 0, "E-NP-01"));
    }

    @Test
    void scoresOutsideTheScaleOrMissingCriteriaAreRejected() {
        assertThatThrownBy(() -> RubricScorer.score(Rubric.R_NOI, Map.of("C1", 4, "C2", 2, "C3", 2, "C4", 2, "C5", 2),
                null, List.of(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RubricScorer.score(Rubric.R_VIET, Map.of("C1", 4, "C2", 2, "C3", 2),
                null, List.of(), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
