package com.cnhsk.learning.grading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.grading.web.GradingRequestViews;
import com.cnhsk.learning.grading.web.GradingResultWrite;
import com.cnhsk.learning.knowledge.entity.ErrorCode;
import com.cnhsk.learning.knowledge.entity.LearningTask;
import com.cnhsk.learning.knowledge.repository.ErrorCodeRepository;
import com.cnhsk.learning.knowledge.repository.LearningTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Turns a rubric grade into the stored result and the learner's knowledge evidence. The request's task (if the
 * learner answered one) decides which nodes C1–C5 feed; confirmed error codes always land on their own node.
 */
@Component
public class RubricGrading {

    private final ErrorCodeRepository errorCodes;
    private final LearningTaskRepository tasks;
    private final RubricEvidenceRecorder recorder;
    private final ObjectMapper mapper;

    public RubricGrading(ErrorCodeRepository errorCodes, LearningTaskRepository tasks,
            RubricEvidenceRecorder recorder, ObjectMapper mapper) {
        this.errorCodes = errorCodes;
        this.tasks = tasks;
        this.recorder = recorder;
        this.mapper = mapper;
    }

    public record Graded(int score100, ObjectNode stored, List<RubricScorer.Evidence> evidence, boolean speaking) { }

    public Graded grade(JsonNode payload, GradingResultWrite submission) {
        boolean speaking = "SPEAKING".equals(GradingRequestViews.mode(payload));
        Rubric expected = speaking ? Rubric.R_NOI : Rubric.R_VIET;
        if (submission.rubric() != expected) throw GradingResultWrite.invalid();

        Map<String, ErrorCode> known = new HashMap<>();
        errorCodes.findAllById(submission.errors()).forEach(code -> known.put(code.getCode(), code));
        if (known.size() != submission.errors().size()) throw GradingResultWrite.invalid();

        LearningTask task = taskOf(payload, speaking);
        List<RubricScorer.TaggedError> tagged = new ArrayList<>();
        List<GradingRequestViews.TaggedError> shown = new ArrayList<>();
        for (String code : submission.errors()) {
            ErrorCode error = known.get(code);
            tagged.add(new RubricScorer.TaggedError(code, error.getKid() == null ? null : error.getKid().getCode(),
                    error.getSeverity()));
            shown.add(new GradingRequestViews.TaggedError(code, error.getNameVi(), error.getSeverity(),
                    error.getFixHint()));
        }
        RubricScorer.Result scored = RubricScorer.score(expected, submission.scores(), targets(task), tagged,
                speaking && GradingRequestViews.audioUrl(payload) != null);

        ObjectNode stored = mapper.createObjectNode();
        stored.put("rubric", expected.name());
        stored.set("scores", mapper.valueToTree(submission.scores()));
        stored.set("errors", mapper.valueToTree(shown));
        stored.set("corrections", mapper.valueToTree(submission.corrections()));
        if (task != null) stored.put("taskCode", task.getCode());
        return new Graded(scored.score100(), stored, scored.evidence(), speaking);
    }

    public void recordEvidence(long learnerId, long requestId, Graded graded) {
        recorder.record(learnerId, requestId, graded.speaking(), graded.evidence());
    }

    private LearningTask taskOf(JsonNode payload, boolean speaking) {
        if (!payload.path("taskId").canConvertToLong()) return null;
        LearningTask task = tasks.findById(payload.get("taskId").longValue()).orElse(null);
        // A task of the other kind cannot be graded with this rubric; grade the work as free practice instead.
        return task != null && task.getKind() == (speaking ? LearningTask.Kind.SPEAKING : LearningTask.Kind.WRITING)
                ? task : null;
    }

    private static RubricScorer.TaskTargets targets(LearningTask task) {
        if (task == null) return null;
        Map<String, List<String>> byCriterion = new HashMap<>();
        for (LearningTask.Target target : task.getTargets()) {
            byCriterion.computeIfAbsent(target.getCriterion(), key -> new ArrayList<>()).add(target.getKid().getCode());
        }
        byCriterion.values().forEach(list -> list.sort(String::compareTo));
        return new RubricScorer.TaskTargets(task.getNlKid().getCode(), byCriterion);
    }
}
