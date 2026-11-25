package com.cnhsk.learning.aigen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;

/** Splits the questions still allowed today across ranked candidates in proportion to their wrong answers. */
final class AiGenerationQuota {
    private AiGenerationQuota() { }

    record Allocation(Candidate candidate, int questions) { }

    /**
     * Largest-remainder split of {@code remaining} by wrong-answer count. A node never receives more than
     * {@code batchSize}; its surplus is split again among the others. When there are fewer questions than
     * nodes only the leading nodes are kept, and every kept node receives at least one question.
     */
    static List<Allocation> allocate(List<Candidate> ranked, int remaining, int batchSize) {
        if (remaining <= 0 || batchSize <= 0 || ranked.isEmpty()) return List.of();
        List<Candidate> chosen = ranked.subList(0, Math.min(remaining, ranked.size()));
        int size = chosen.size();
        int[] counts = new int[size];
        boolean[] open = new boolean[size];
        java.util.Arrays.fill(open, true);
        int left = remaining;
        while (left > 0) {
            long weight = 0;
            for (int i = 0; i < size; i++) if (open[i]) weight += weight(chosen.get(i));
            if (weight == 0) break;
            int[] share = new int[size];
            double[] rest = new double[size];
            int given = 0;
            for (int i = 0; i < size; i++) {
                if (!open[i]) continue;
                double exact = (double) left * weight(chosen.get(i)) / weight;
                share[i] = (int) Math.floor(exact);
                rest[i] = exact - share[i];
                given += share[i];
            }
            List<Integer> order = IntStream.range(0, size).filter(i -> open[i]).boxed()
                    .sorted(Comparator.<Integer>comparingDouble(i -> -rest[i]).thenComparingInt(i -> i)).toList();
            for (int k = 0; k < left - given; k++) share[order.get(k % order.size())]++;
            int surplus = 0;
            for (int i = 0; i < size; i++) {
                if (!open[i]) continue;
                counts[i] += share[i];
                if (counts[i] >= batchSize) {
                    surplus += counts[i] - batchSize;
                    counts[i] = batchSize;
                    open[i] = false;
                }
            }
            left = surplus;
        }
        for (int i = 0; i < size; i++) {
            if (counts[i] > 0) continue;
            int donor = 0;
            for (int j = 1; j < size; j++) if (counts[j] > counts[donor]) donor = j;
            counts[donor]--;
            counts[i] = 1;
        }
        List<Allocation> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(new Allocation(chosen.get(i), counts[i]));
        return List.copyOf(result);
    }

    /** Nodes chosen only for their prerequisite role may have no wrong answers of their own. */
    private static long weight(Candidate candidate) { return Math.max(1, candidate.wrongCount()); }
}
