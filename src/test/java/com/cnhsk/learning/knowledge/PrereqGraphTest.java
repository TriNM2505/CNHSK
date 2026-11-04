package com.cnhsk.learning.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.cnhsk.learning.knowledge.PrereqGraph.EdgeType;
import com.cnhsk.learning.knowledge.PrereqGraph.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PrereqGraphTest {
    @ParameterizedTest
    @EnumSource(EdgeType.class)
    void AT05_AT06_AT08_missingNodesAndSelfReferencesHaveSpecificErrors(EdgeType type) {
        PrereqGraph graph = graph(node(1), node(2));
        assertThat(graph.rejection("missing", kid(2), type)).get().extracting(PrereqGraph.Rejection::code).isEqualTo("REFERENCE_NOT_FOUND");
        assertThat(graph.rejection(kid(1), "missing", type)).get().extracting(PrereqGraph.Rejection::field).isEqualTo("to_kid");
        assertThat(graph.rejection(kid(1), kid(1), type)).get().extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_SELF");
        assertThat(graph.contains("missing", kid(1), type)).isFalse();
        assertThat(graph.contains(kid(1), "missing", type)).isFalse();
    }

    @Test
    void AT06_prerequisiteCannotHaveAHigherLevelButSupportCan() {
        PrereqGraph graph = graph(new Node(1, "TV3-001", 3, List.of(), List.of()),
                new Node(2, "TV2-002", 2, List.of(), List.of()));
        assertThat(graph.rejection("TV3-001", "TV2-002", EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("PREREQ_LEVEL_HIGHER");
        assertThat(graph.rejection("TV3-001", "TV2-002", EdgeType.SUPPORT)).isEmpty();
    }

    @Test
    void AT06_cyclesAndRedundantPathsIncludeDatabaseAndPreviouslyAddedEdges() {
        PrereqGraph graph = graph(node(1), node(2, 1), node(3));
        assertThat(graph.rejection(kid(2), kid(1), EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_CYCLE");
        assertThat(graph.rejection(kid(2), kid(3), EdgeType.PREREQ)).isEmpty();
        graph.add(kid(2), kid(3), EdgeType.PREREQ);
        assertThat(graph.rejection(kid(3), kid(1), EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_CYCLE");
        assertThat(graph.rejection(kid(1), kid(3), EdgeType.PREREQ)).get().satisfies(error -> {
            assertThat(error.code()).isEqualTo("EDGE_REDUNDANT");
            assertThat(error.message()).contains(kid(1), kid(3));
        });
    }

    @Test
    void AT06_fourDirectPrerequisitesAreAllowedAndReimportDoesNotConsumeAnotherSlot() {
        PrereqGraph graph = graph(node(1), node(2), node(3), node(4), node(5), node(6, 1, 2, 3));
        assertThat(graph.rejection(kid(4), kid(6), EdgeType.PREREQ)).isEmpty();
        graph.add(kid(4), kid(6), EdgeType.PREREQ);
        assertThat(graph.rejection(kid(5), kid(6), EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("TOO_MANY_PREREQS");
        assertThat(graph.rejection(kid(1), kid(6), EdgeType.PREREQ)).isEmpty();
        assertThat(graph.rejection(kid(5), kid(6), EdgeType.SUPPORT)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(EdgeType.class)
    void AT08_sameTypeIsIdempotentAndTheOtherTypeConflicts(EdgeType type) {
        PrereqGraph graph = graph(node(1), node(2));
        graph.add(kid(1), kid(2), type);
        assertThat(graph.contains(kid(1), kid(2), type)).isTrue();
        assertThat(graph.rejection(kid(1), kid(2), type)).isEmpty();
        assertThat(graph.rejection(kid(1), kid(2), type == EdgeType.PREREQ ? EdgeType.SUPPORT : EdgeType.PREREQ))
                .get().extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_TYPE_CONFLICT");
    }

    @Test
    void AT08_supportDoesNotCreatePrerequisitePathsOrDirectionIndependentConflicts() {
        PrereqGraph graph = graph(node(1), node(2), node(3));
        graph.add(kid(1), kid(2), EdgeType.SUPPORT);
        graph.add(kid(2), kid(3), EdgeType.SUPPORT);
        assertThat(graph.rejection(kid(3), kid(1), EdgeType.PREREQ)).isEmpty();
        assertThat(graph.rejection(kid(1), kid(3), EdgeType.PREREQ)).isEmpty();
    }

    @Test
    void snapshotArraysAreCopiedAndTraversalTerminatesEvenWithExistingCorruptCycles() {
        List<Long> original = new ArrayList<>(List.of(2L));
        Node first = new Node(1, kid(1), 1, original, List.of());
        original.clear();
        PrereqGraph graph = graph(first, node(2, 1), node(3), node(4));
        assertThat(graph.contains(kid(2), kid(1), EdgeType.PREREQ)).isTrue();
        assertThat(graph.rejection(kid(1), kid(3), EdgeType.PREREQ)).isEmpty();
    }

    @Test
    void fullSizeTreeUsesEveryIntermediateNodeForReachability() {
        PrereqGraph graph = new PrereqGraph(IntStream.rangeClosed(1, 600)
                .mapToObj(id -> id == 1 ? node(id) : node(id, id - 1)).toList());
        assertThat(graph.rejection(kid(600), kid(1), EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_CYCLE");
        assertThat(graph.rejection(kid(1), kid(600), EdgeType.PREREQ)).get()
                .extracting(PrereqGraph.Rejection::code).isEqualTo("EDGE_REDUNDANT");
    }

    private static PrereqGraph graph(Node... nodes) { return new PrereqGraph(List.of(nodes)); }
    private static String kid(long id) { return "TV1-%03d".formatted(id); }
    private static Node node(long id, long... prereqs) {
        return new Node(id, kid(id), 1, java.util.Arrays.stream(prereqs).boxed().toList(), List.of());
    }
}
