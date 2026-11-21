package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.PrerequisiteLink;
import org.junit.jupiter.api.Test;

class RootCauseRankerTest {

    private static final String BA = "NP3-901";
    private static final String BEI = "NP3-902";
    private static final String RESULT = "NP2-901";

    @Test
    void sharedPrerequisiteOfTwoWeakNodesOutranksBoth() {
        Map<String, Long> scores = RootCauseRanker.scores(Map.of(BA, 30L, BEI, 25L, RESULT, 5L),
                List.of(prereq(BA, RESULT), prereq(BEI, RESULT)));

        assertThat(scores.get(RESULT)).isGreaterThanOrEqualTo(20L)
                .isGreaterThan(scores.get(BA)).isGreaterThan(scores.get(BEI));
        // Mass is redistributed, not created.
        assertThat(scores.values().stream().mapToLong(Long::longValue).sum()).isBetween(59L, 61L);
    }

    @Test
    void reachesPrerequisitesWithoutWrongAnswersOfTheirOwn() {
        Map<String, Long> scores = RootCauseRanker.scores(Map.of(BA, 40L),
                List.of(prereq(BA, RESULT), prereq(RESULT, "NP1-901")));

        assertThat(scores).containsKeys(RESULT, "NP1-901");
        assertThat(scores.get(RESULT)).isPositive();
        assertThat(scores.get("NP1-901")).isPositive();
    }

    @Test
    void supportLinksCarryLessThanPrerequisites() {
        Map<String, Long> scores = RootCauseRanker.scores(Map.of(BA, 40L),
                List.of(prereq(BA, RESULT), new PrerequisiteLink(BA, "NL3-901", true)));

        assertThat(scores.get(RESULT)).isGreaterThan(scores.get("NL3-901"));
        assertThat(scores.get("NL3-901")).isPositive();
    }

    @Test
    void withoutLinksFromWeakNodesTheScoresAreTheExactWrongCounts() {
        Map<String, Long> wrong = Map.of(BA, 3_000_000_123L, BEI, 7L);

        assertThat(RootCauseRanker.scores(wrong, List.of())).isEqualTo(wrong);
        // Links elsewhere in the tree do not touch nodes nobody got wrong.
        assertThat(RootCauseRanker.scores(wrong, List.of(prereq("TV2-901", "TV1-901")))).isEqualTo(wrong);
    }

    @Test
    void emptyHistoryHasNoScores() {
        assertThat(RootCauseRanker.scores(Map.of(), List.of(prereq(BA, RESULT)))).isEmpty();
    }

    private static PrerequisiteLink prereq(String dependent, String required) {
        return new PrerequisiteLink(dependent, required, false);
    }
}
