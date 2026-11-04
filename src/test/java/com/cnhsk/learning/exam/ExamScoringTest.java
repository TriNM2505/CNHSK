package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExamScoringTest {
    @Test
    void FR012_usesAllWeightsAndRoundsDownLikeExamList() {
        var exam = new ExamScoring.Snapshot(1, "Đề tự soạn", 1,
                List.of(new ExamScoring.Section(0, "Đọc"), new ExamScoring.Section(1, "Viết")),
                Map.of(1, 0, 2, 0, 3, 1), Map.of(1, 2, 2, 1, 3, 3));
        var score = ExamScoring.score(exam, Set.of(1, 99));
        assertThat(score.score()).isEqualTo(2);
        assertThat(score.maxScore()).isEqualTo(6);
        assertThat(score.percent()).isEqualTo(33);
        assertThat(score.bySection()).containsExactly(new ExamScoring.SectionScore(0, "Đọc", 2, 3),
                new ExamScoring.SectionScore(1, "Viết", 0, 3));
    }
}
