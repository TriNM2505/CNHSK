package com.cnhsk.learning.vocab;

import static org.assertj.core.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopicPrerequisiteGraphTest {
    @Test void selfAndLongerCyclesReturnClosedDeterministicPaths() {
        assertThat(TopicPrerequisiteGraph.cycle(Map.of(1L, List.of(1L)))).containsExactly(1L, 1L);
        assertThat(TopicPrerequisiteGraph.cycle(Map.of(1L, List.of(2L), 2L, List.of(1L)))).containsExactly(1L, 2L, 1L);
        assertThat(TopicPrerequisiteGraph.cycle(Map.of(1L, List.of(2L), 2L, List.of(3L), 3L, List.of(1L))))
                .containsExactly(1L, 2L, 3L, 1L);
    }

    @Test void disconnectedCyclesAreFoundEvenWhenAnotherComponentHasARoot() {
        var graph = Map.of(1L, List.<Long>of(), 2L, List.of(3L), 3L, List.of(2L));
        assertThat(TopicPrerequisiteGraph.hasRoot(graph)).isTrue();
        assertThat(TopicPrerequisiteGraph.cycle(graph)).containsExactly(2L, 3L, 2L);
    }

    @Test void diamondsDuplicateEdgesAndMissingIdsDoNotCreateCycles() {
        var graph = Map.of(1L, List.of(3L, 2L, 2L, 99L), 2L, List.of(4L), 3L, List.of(4L), 4L, List.of(99L));
        assertThat(TopicPrerequisiteGraph.cycle(graph)).isEmpty();
        assertThat(TopicPrerequisiteGraph.hasRoot(graph)).isTrue();
        assertThat(TopicPrerequisiteGraph.hasRoot(Map.of(1L, List.of(99L)))).isTrue();
        assertThat(TopicPrerequisiteGraph.hasRoot(Map.of(1L, List.of(1L)))).isFalse();
        assertThat(TopicPrerequisiteGraph.hasRoot(Map.of())).isFalse();
    }

    @Test void deepAndDenseTwoThousandNodeGraphsUseTheStackAndLogMeasurements() {
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        for (long id = 1; id <= 2000; id++) {
            graph.put(id, java.util.stream.LongStream.rangeClosed(Math.max(1, id - 10), id - 1).boxed().toList());
        }
        for (int warmup = 0; warmup < 5; warmup++) TopicPrerequisiteGraph.cycle(graph);
        long start = System.nanoTime();
        var result = TopicPrerequisiteGraph.cycle(graph);
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        assertThat(result).isEmpty();
        System.out.println("Topic prerequisite DFS: nodes=2000, edges=" + graph.values().stream().mapToInt(List::size).sum() + ", elapsed ms=" + elapsed);
        graph.clear();
        for (long id = 1; id <= 2000; id++) graph.put(id, id == 2000 ? List.of() : List.of(id + 1));
        assertThat(TopicPrerequisiteGraph.cycle(graph)).isEmpty();
        graph.put(2000L, List.of(1L));
        var cycle = TopicPrerequisiteGraph.cycle(graph);
        assertThat(cycle).hasSize(2001).startsWith(1L, 2L).endsWith(2000L, 1L);
    }
}
