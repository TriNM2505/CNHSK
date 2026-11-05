package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import org.junit.jupiter.api.Test;

class FrontierServiceTest {

    private final FrontierService frontier = new FrontierService();
    private final MasteryConfig.InferenceSettings settings = new MasteryConfig.InferenceSettings(
            new BigDecimal("2.0"), new BigDecimal("80"));

    @Test
    void AT11_infersNotYetPrerequisiteOnlyWhenDependentMeetsBothThresholds() {
        KnowledgeNode prerequisite = node(1, "NP3-001", List.of());
        KnowledgeNode dependent = node(2, "NP3-002", List.of("NP3-001"));

        var inferred = frontier.decorate(List.of(prerequisite, dependent), Map.of(
                "NP3-001", snapshot(null, "notyet"),
                "NP3-002", snapshot("85", "extend")), settings);
        var belowConfidence = frontier.decorate(List.of(prerequisite, dependent), Map.of(
                "NP3-001", snapshot(null, "notyet"),
                "NP3-002", new FrontierService.MasterySnapshot(new BigDecimal("85"), new BigDecimal("1.5"),
                        MasteryState.EXTEND)), settings);

        assertThat(inferred.get("NP3-001").state()).isEqualTo(MasteryState.INFERRED);
        assertThat(inferred.get("NP3-001").frontier()).isFalse();
        assertThat(belowConfidence.get("NP3-001").state()).isEqualTo(MasteryState.NOT_YET);
    }

    @Test
    void AT14_frontierRequiresAllDirectPrerequisitesAndExcludesExtendAndInferred() {
        List<KnowledgeNode> nodes = List.of(
                node(1, "NP3-001", List.of()),
                node(2, "NP3-002", List.of()),
                node(3, "NP3-003", List.of("NP3-001", "NP3-002")),
                node(4, "NP3-004", List.of()));
        Map<String, FrontierService.MasterySnapshot> states = Map.of(
                "NP3-001", snapshot("65", "consolidate"),
                "NP3-002", snapshot("50", "review"),
                "NP3-003", snapshot(null, "notyet"),
                "NP3-004", snapshot("90", "extend"));

        var result = frontier.decorate(nodes, states, settings);
        assertThat(result.get("NP3-003").frontier()).isFalse();
        assertThat(result.get("NP3-001").frontier()).isTrue();
        assertThat(result.get("NP3-004").frontier()).isFalse();

        var prerequisitesReady = frontier.decorate(nodes, Map.of(
                "NP3-001", snapshot("65", "consolidate"),
                "NP3-002", snapshot("62", "consolidate"),
                "NP3-003", snapshot(null, "notyet"),
                "NP3-004", snapshot("90", "inferred")), settings);
        assertThat(prerequisitesReady.get("NP3-003").frontier()).isTrue();
        assertThat(prerequisitesReady.get("NP3-004").frontier()).isFalse();
    }

    private static KnowledgeNode node(long id, String kid, List<String> prerequisites) {
        return new KnowledgeNode(id, kid, null, 3, kid, null, null, null, prerequisites, List.of());
    }

    private static FrontierService.MasterySnapshot snapshot(String value, String state) {
        MasteryState masteryState = MasteryState.fromCode(state);
        return new FrontierService.MasterySnapshot(value == null ? null : new BigDecimal(value),
                "notyet".equals(state) ? BigDecimal.ZERO : new BigDecimal("2.5"), masteryState);
    }
}
