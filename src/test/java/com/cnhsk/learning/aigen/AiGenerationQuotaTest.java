package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cnhsk.learning.aigen.AiGenerationQuota.Allocation;
import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;
import org.junit.jupiter.api.Test;

class AiGenerationQuotaTest {
    private static final Candidate A = candidate(1, "NP1-001", 30);
    private static final Candidate B = candidate(2, "NP1-002", 10);
    private static final Candidate C = candidate(3, "NP1-003", 5);

    @Test
    void splitsTheRemainingQuestionsByWrongAnswersWithLargestRemainders() {
        assertThat(split(List.of(A, B, C), 6, 5)).containsExactly(
                Map.entry("NP1-001", 4), Map.entry("NP1-002", 1), Map.entry("NP1-003", 1));
    }

    @Test
    void capsEachNodeAtTheBatchSizeAndSharesTheSurplusWithTheOthers() {
        assertThat(split(List.of(A, B, C), 20, 5)).containsExactly(
                Map.entry("NP1-001", 5), Map.entry("NP1-002", 5), Map.entry("NP1-003", 5));
        assertThat(split(List.of(A, B, C), 12, 5)).containsExactly(
                Map.entry("NP1-001", 5), Map.entry("NP1-002", 5), Map.entry("NP1-003", 2));
    }

    @Test
    void fewerQuestionsThanNodesGivesOneQuestionToEachLeadingNode() {
        assertThat(split(List.of(A, B, C), 2, 5)).containsExactly(Map.entry("NP1-001", 1), Map.entry("NP1-002", 1));
        assertThat(split(List.of(A, B, C), 1, 5)).containsExactly(Map.entry("NP1-001", 1));
    }

    @Test
    void everySelectedNodeGetsAtLeastOneQuestionEvenWhenItsShareRoundsToZero() {
        Candidate heavy = candidate(4, "NP1-004", 1_000);
        Candidate light = candidate(5, "NP1-005", 1);
        Candidate lighter = candidate(6, "NP1-006", 1);
        assertThat(split(List.of(heavy, light, lighter), 3, 5)).containsExactly(
                Map.entry("NP1-004", 1), Map.entry("NP1-005", 1), Map.entry("NP1-006", 1));
    }

    @Test
    void nodesRankedOnlyByRootScoreStillReceiveAShare() {
        Candidate root = candidate(7, "NP1-007", 0);
        assertThat(split(List.of(root, A), 6, 5)).containsExactly(Map.entry("NP1-007", 1), Map.entry("NP1-001", 5));
    }

    @Test
    void nothingIsAllocatedWithoutRoomOrCandidates() {
        assertThat(AiGenerationQuota.allocate(List.of(A), 0, 5)).isEmpty();
        assertThat(AiGenerationQuota.allocate(List.of(A), -3, 5)).isEmpty();
        assertThat(AiGenerationQuota.allocate(List.of(), 10, 5)).isEmpty();
        assertThat(AiGenerationQuota.allocate(List.of(A), 10, 0)).isEmpty();
    }

    @Test
    void neverExceedsTheRemainingQuestionsOrTheBatchSize() {
        for (int remaining = 1; remaining <= 40; remaining++) {
            for (int batch = 1; batch <= 10; batch++) {
                var allocations = AiGenerationQuota.allocate(List.of(A, B, C), remaining, batch);
                int total = allocations.stream().mapToInt(Allocation::questions).sum();
                int limit = batch;
                assertThat(total).isEqualTo(Math.min(remaining, 3 * batch));
                assertThat(allocations).allSatisfy(allocation ->
                        assertThat(allocation.questions()).isBetween(1, limit));
            }
        }
    }

    private static Map<String, Integer> split(List<Candidate> ranked, int remaining, int batch) {
        return AiGenerationQuota.allocate(ranked, remaining, batch).stream().collect(Collectors.toMap(
                allocation -> allocation.candidate().kid(), Allocation::questions, (left, right) -> left,
                java.util.LinkedHashMap::new));
    }

    private static Candidate candidate(long id, String kid, long wrong) {
        return new Candidate(id, kid, wrong, 0, 0);
    }
}
