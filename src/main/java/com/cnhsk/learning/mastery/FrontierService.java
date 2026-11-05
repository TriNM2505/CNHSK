package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.mastery.MasteryConfig.InferenceSettings;

/** Derives inferred and learning-frontier flags without persisting either state. */
@Service
public final class FrontierService {

    public Map<String, Decoration> decorate(List<KnowledgeNode> nodes, Map<String, MasterySnapshot> snapshots,
            InferenceSettings settings) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(settings, "settings");

        Map<String, MasteryState> states = new HashMap<>();
        snapshots.forEach((kid, snapshot) -> states.put(kid, snapshot.state()));
        for (KnowledgeNode candidate : nodes) {
            MasterySnapshot snapshot = snapshots.get(candidate.kid());
            if (snapshot == null || snapshot.state() != MasteryState.NOT_YET) {
                continue;
            }
            boolean inferred = nodes.stream().filter(dependent -> dependent.prereqKids().contains(candidate.kid()))
                    .map(dependent -> snapshots.get(dependent.kid()))
                    .filter(Objects::nonNull)
                    .anyMatch(dependent -> dependent.value() != null
                            && dependent.value().compareTo(settings.from()) >= 0
                            && dependent.confidence().compareTo(settings.minConfidence()) >= 0);
            if (inferred) {
                states.put(candidate.kid(), MasteryState.INFERRED);
            }
        }

        Map<String, Decoration> result = new HashMap<>();
        for (KnowledgeNode node : nodes) {
            MasteryState state = states.getOrDefault(node.kid(), MasteryState.NOT_YET);
            boolean prerequisitesSatisfied = node.prereqKids().stream()
                    .map(prerequisite -> states.getOrDefault(prerequisite, MasteryState.NOT_YET))
                    .allMatch(FrontierService::isConsolidated);
            boolean frontier = state != MasteryState.EXTEND && state != MasteryState.INFERRED
                    && prerequisitesSatisfied;
            result.put(node.kid(), new Decoration(state, frontier));
        }
        return Map.copyOf(result);
    }

    private static boolean isConsolidated(MasteryState state) {
        return state == MasteryState.CONSOLIDATE || state == MasteryState.EXTEND
                || state == MasteryState.INFERRED;
    }

    public record MasterySnapshot(BigDecimal value, BigDecimal confidence, MasteryState state) {
        public MasterySnapshot {
            Objects.requireNonNull(confidence, "confidence");
            Objects.requireNonNull(state, "state");
        }
    }

    public record Decoration(MasteryState state, boolean frontier) { }
}
