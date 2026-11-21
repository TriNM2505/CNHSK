package com.cnhsk.learning.aigen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.PrerequisiteLink;
import com.cnhsk.learning.knowledge.PersonalizedPageRank;

/**
 * Traces wrong answers of all learners back to the prerequisites they depend on.
 *
 * <p>Each KID starts with its wrong-answer count and the count flows to the nodes it requires, so
 * a prerequisite shared by several weak nodes surfaces as the root cause even when learners rarely
 * fail it directly. Support links carry less than prerequisites.
 */
final class RootCauseRanker {

    static final double SUPPORT_WEIGHT = 0.3;

    private RootCauseRanker() { }

    /**
     * Returns a root score per KID in wrong-answer units. When no node with wrong answers has an
     * outgoing link, the scores are the exact wrong counts, so selection is unchanged.
     */
    static Map<String, Long> scores(Map<String, Long> wrongCounts, List<PrerequisiteLink> links) {
        Map<String, Map<String, Double>> required = new HashMap<>();
        for (PrerequisiteLink link : links) {
            double weight = link.support() ? SUPPORT_WEIGHT : 1.0;
            required.computeIfAbsent(link.dependentKid(), ignored -> new HashMap<>())
                    .merge(link.requiredKid(), weight, Math::max);
        }
        // Kept exact without floating point: a prerequisite may itself have wrong answers, so the
        // test is whether any weak node has a link, not whether new nodes appear.
        if (wrongCounts.keySet().stream().noneMatch(required::containsKey)) return Map.copyOf(wrongCounts);

        Map<String, Double> seeds = new HashMap<>();
        wrongCounts.forEach((kid, count) -> seeds.put(kid, count.doubleValue()));
        Map<String, Long> scores = new HashMap<>();
        PersonalizedPageRank.rank(seeds, required).forEach((kid, score) -> scores.put(kid, Math.round(score)));
        return Map.copyOf(scores);
    }
}
