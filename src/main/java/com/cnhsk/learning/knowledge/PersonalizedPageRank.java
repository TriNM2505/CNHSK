package com.cnhsk.learning.knowledge;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Personalized PageRank over the knowledge tree, used to trace weaknesses back to their roots.
 *
 * <p>Rank starts on the seed nodes in proportion to their weight and flows along the given links
 * (normally from a node to the nodes it requires). A node shared by several seeds collects their
 * rank. Rank held by nodes without outgoing links returns to the seed distribution. Results are
 * scaled back to the seed total, so they read in the same unit as the seed weights.
 */
public final class PersonalizedPageRank {

    public static final double DAMPING = 0.85;
    private static final int MAX_ITERATIONS = 50;
    private static final double TOLERANCE = 1e-6;

    private PersonalizedPageRank() { }

    /**
     * @param seeds positive starting weight per node
     * @param links weight of each link, keyed by source node then target node
     * @return score per node reachable from a seed; the seeds themselves when no seed has a link
     */
    public static Map<String, Double> rank(Map<String, Double> seeds, Map<String, Map<String, Double>> links) {
        if (seeds.isEmpty()) return Map.of();
        if (seeds.keySet().stream().noneMatch(kid -> !links.getOrDefault(kid, Map.of()).isEmpty())) {
            return Map.copyOf(seeds);
        }

        // Only nodes reachable from a seed take part; the rest of the tree has no rank.
        Set<String> reachable = new LinkedHashSet<>(seeds.keySet());
        ArrayDeque<String> pending = new ArrayDeque<>(seeds.keySet());
        while (!pending.isEmpty()) {
            for (String next : links.getOrDefault(pending.removeFirst(), Map.of()).keySet()) {
                if (reachable.add(next)) pending.add(next);
            }
        }

        double total = seeds.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> start = new HashMap<>();
        seeds.forEach((kid, weight) -> start.put(kid, weight / total));
        Map<String, Double> rank = new HashMap<>(start);
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            Map<String, Double> next = new HashMap<>();
            double returning = 0;
            for (String kid : reachable) {
                double value = rank.getOrDefault(kid, 0.0);
                Map<String, Double> targets = links.getOrDefault(kid, Map.of());
                if (targets.isEmpty()) {
                    returning += value;
                    continue;
                }
                double weights = targets.values().stream().mapToDouble(Double::doubleValue).sum();
                targets.forEach((target, weight) ->
                        next.merge(target, DAMPING * value * weight / weights, Double::sum));
            }
            double restart = (1 - DAMPING) + DAMPING * returning;
            start.forEach((kid, share) -> next.merge(kid, restart * share, Double::sum));
            double change = 0;
            for (String kid : reachable) {
                change += Math.abs(next.getOrDefault(kid, 0.0) - rank.getOrDefault(kid, 0.0));
            }
            rank = next;
            if (change < TOLERANCE) break;
        }

        Map<String, Double> scores = new HashMap<>();
        for (String kid : reachable) scores.put(kid, rank.getOrDefault(kid, 0.0) * total);
        return Map.copyOf(scores);
    }
}
