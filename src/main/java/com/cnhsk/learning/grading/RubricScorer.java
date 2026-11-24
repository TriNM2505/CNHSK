package com.cnhsk.learning.grading;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a rubric grade into the display score and the evidence of appendix E.3. Pure, so the table of the spec is
 * the test. Criteria C1 and C5 (speaking) describe the communicative node itself and are averaged into one
 * PRIMARY evidence; the other criteria feed the task's target nodes as SECONDARY evidence; each confirmed error of
 * severity 2 or 3 is one zero-score ERROR evidence on its node (E-NL-* on the task's NL node).
 */
public final class RubricScorer {
    private RubricScorer() { }

    /** Target nodes of a task: the NL node and, per criterion C2–C4, the nodes it feeds. */
    public record TaskTargets(String nlKid, Map<String, List<String>> byCriterion) {
        public TaskTargets {
            byCriterion = Map.copyOf(byCriterion);
        }
    }

    /** An error code a teacher confirmed; {@code kid} is null for communicative codes (E-NL-*). */
    public record TaggedError(String code, String kid, int severity) { }

    public record Evidence(String kid, String role, int score, String source) { }

    public record Result(int score100, List<Evidence> evidence) {
        public Result {
            evidence = List.copyOf(evidence);
        }
    }

    public static Result score(Rubric rubric, Map<String, Integer> scores, TaskTargets task, List<TaggedError> errors,
            boolean hasAudio) {
        if (rubric == Rubric.LEGACY) throw new IllegalArgumentException("Legacy grades produce no rubric score");
        for (String criterion : rubric.criteria()) {
            Integer value = scores.get(criterion);
            if (value == null || value < 0 || value > rubric.max()) {
                throw new IllegalArgumentException("Criterion " + criterion + " must be 0.." + rubric.max());
            }
        }
        if (scores.size() != rubric.criteria().size()) throw new IllegalArgumentException("Unknown criterion");
        int total = rubric.criteria().stream().mapToInt(scores::get).sum();
        int score100 = Math.round(100f * total / (rubric.max() * rubric.criteria().size()));

        List<Evidence> evidence = new ArrayList<>();
        if (task != null) {
            if (rubric == Rubric.R_NOI) {
                evidence.add(new Evidence(task.nlKid(), "PRIMARY",
                        percent((scores.get("C1") + scores.get("C5")) / 2f, rubric), "C1+C5"));
            } else {
                evidence.add(new Evidence(task.nlKid(), "PRIMARY", percent(scores.get("C1"), rubric), "C1"));
            }
            for (String criterion : List.of("C2", "C3", "C4")) {
                // Pronunciation can only be judged from the original recording (spec 5.10).
                if (rubric == Rubric.R_NOI && criterion.equals("C2") && !hasAudio) continue;
                for (String kid : task.byCriterion().getOrDefault(criterion, List.of())) {
                    evidence.add(new Evidence(kid, "SECONDARY", percent(scores.get(criterion), rubric), criterion));
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (TaggedError error : errors) {
            if (error.severity() < 2 || !seen.add(error.code())) continue;
            String kid = error.kid() != null ? error.kid() : task == null ? null : task.nlKid();
            if (kid != null) evidence.add(new Evidence(kid, "ERROR", 0, error.code()));
        }
        return new Result(score100, evidence);
    }

    private static int percent(float value, Rubric rubric) {
        return Math.round(100f * value / rubric.max());
    }
}
