package com.cnhsk.learning.exam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scores the exact questions and weights served for an attempt. */
public final class ExamScoring {
    private ExamScoring() {}

    public static Score score(Snapshot exam, Set<Integer> correctNumbers) {
        Map<Integer, Long> scores = new HashMap<>();
        Map<Integer, Long> maxima = new HashMap<>();
        long score = 0;
        long maxScore = 0;
        for (var entry : exam.points().entrySet()) {
            int no = entry.getKey();
            long weight = entry.getValue();
            int section = exam.sectionOf().get(no);
            maxima.merge(section, weight, Long::sum);
            maxScore += weight;
            if (correctNumbers.contains(no)) {
                score += weight;
                scores.merge(section, weight, Long::sum);
            }
        }
        List<SectionScore> bySection = exam.sections().stream()
                .map(section -> new SectionScore(section.index(), section.name(),
                        scores.getOrDefault(section.index(), 0L), maxima.getOrDefault(section.index(), 0L)))
                .toList();
        int percent = maxScore == 0 ? 0 : Math.toIntExact(score * 100 / maxScore);
        return new Score(score, maxScore, percent, bySection);
    }

    public record Snapshot(long examId, String title, Integer hskLevel, List<Section> sections,
            Map<Integer, Integer> sectionOf, Map<Integer, Integer> points) {}
    public record Section(int index, String name) {}
    public record SectionScore(int index, String name, long score, long maxScore) {}
    public record Score(long score, long maxScore, int percent, List<SectionScore> bySection) {}
}
