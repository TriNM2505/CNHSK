package com.cnhsk.learning.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import org.springframework.stereotype.Component;

/**
 * Records a teacher's rubric grade as one finished attempt of the learner, one item per evidence row, so mastery
 * is rebuilt from attempt items like every other source (the score and role travel in the item meta).
 */
@Component
public class RubricEvidenceRecorder {

    private final PracticeService practice;

    public RubricEvidenceRecorder(PracticeService practice) {
        this.practice = practice;
    }

    public void record(long learnerId, long requestId, boolean speaking, List<RubricScorer.Evidence> evidence) {
        if (evidence.isEmpty()) {
            return;
        }
        Skill skill = speaking ? Skill.SPEAKING : Skill.WRITING;
        List<ServedItem> items = new ArrayList<>();
        List<Answer> answers = new ArrayList<>();
        int no = 0;
        for (RubricScorer.Evidence row : evidence) {
            no++;
            items.add(new ServedItem(no, ItemType.WRITING_RESULT, skill, null,
                    new ServedItem.Prompt(row.source(), null, null, null, null, null, null, null, null),
                    null, null, null, null, new ServedItem.Rules(0, 0, false), null, row.kid()));
            answers.add(new Answer(no, row.source(), 0, 0, false, false,
                    Map.of("score", row.score(), "role", row.role(), "criterion", row.source(),
                            "requestId", requestId)));
        }
        practice.recordAndFinish(new PracticeService.StartPracticeCommand(learnerId, PracticeSource.RUBRIC_TASK,
                requestId, null, items, answers, null, Map.of()));
    }
}
