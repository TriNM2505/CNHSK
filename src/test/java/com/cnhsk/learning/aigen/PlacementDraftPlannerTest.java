package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class PlacementDraftPlannerTest {
    private static final List<PlacementDraftPlanner.Node> NODES = List.of(
            new PlacementDraftPlanner.Node(1, "CH1-001", "CH"), new PlacementDraftPlanner.Node(2, "CH1-002", "CH"),
            new PlacementDraftPlanner.Node(3, "NP1-001", "NP"),
            new PlacementDraftPlanner.Node(4, "TV1-001", "TV"), new PlacementDraftPlanner.Node(5, "TV1-002", "TV"),
            new PlacementDraftPlanner.Node(6, "TV1-003", "TV"));

    @Test
    void splitsTheCountIntoBatchesThatCycleThroughDomainsFirst() {
        var plan = PlacementDraftPlanner.spread(NODES, 13, 5);
        assertThat(plan).extracting(AiGenerationQuota.Allocation::questions).containsExactly(5, 5, 3);
        assertThat(plan).extracting(allocation -> allocation.candidate().kid()).containsExactly("CH1-001", "NP1-001", "TV1-001");
    }

    @Test
    void visitsEveryNodeBeforeRepeatingOne() {
        var plan = PlacementDraftPlanner.spread(NODES, 50, 5);
        assertThat(plan).hasSize(10);
        Map<String, Long> perNode = plan.stream().collect(Collectors.groupingBy(a -> a.candidate().kid(), Collectors.counting()));
        assertThat(perNode).hasSize(6);
        assertThat(perNode.values()).allSatisfy(times -> assertThat(times).isBetween(1L, 2L));
        assertThat(plan.stream().mapToInt(AiGenerationQuota.Allocation::questions).sum()).isEqualTo(50);
    }

    @Test
    void nothingToPlanWithoutNodesOrQuestions() {
        assertThat(PlacementDraftPlanner.spread(List.of(), 10, 5)).isEmpty();
        assertThat(PlacementDraftPlanner.spread(NODES, 0, 5)).isEmpty();
    }
}
