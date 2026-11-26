package com.cnhsk.learning.mockprep;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;

import com.cnhsk.learning.exam.MockPaperFormat;
import com.cnhsk.learning.exam.MockPaperFormat.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaperBuilderTest {

    @ParameterizedTest
    @CsvSource({"1,40,2", "2,60,2", "3,80,3"})
    void layoutsMatchTheHskPaper(int level, int questions, int sections) {
        var layout = MockPaperFormat.of(level);
        assertThat(layout.questions()).isEqualTo(questions);
        assertThat(layout.sections()).hasSize(sections);
        assertThat(layout.parts()).allSatisfy(part -> assertThat(layout.sections())
                .anyMatch(section -> section.index() == part.section()));
    }

    @Test
    void papersShareNoQuestionAndFollowTheLayout() {
        var layout = MockPaperFormat.of(3);
        Map<String, List<Long>> approved = new LinkedHashMap<>();
        long next = 1;
        for (Part part : layout.parts()) {
            int pool = part.count() * MockPaperFormat.PAPERS + 2;
            approved.put(part.code(), LongStream.range(next, next + pool).boxed().toList());
            next += pool;
        }

        var papers = MockPaperBuilder.assemble(layout, approved, RandomGenerator.of("L64X128MixRandom"));

        assertThat(papers).hasSize(MockPaperFormat.PAPERS);
        Set<Long> seen = new HashSet<>();
        for (var paper : papers) {
            assertThat(paper).hasSize(80);
            assertThat(paper).extracting(MockPaperBuilder.Slot::section).isSorted();
            paper.forEach(slot -> assertThat(seen.add(slot.questionId())).isTrue());
            assertThat(paper.stream().filter(slot -> slot.section() == 2)).hasSize(10);
        }
    }

    @Test
    void questionCodesNameLevelAndPart() {
        assertThat(MockPaperFormat.questionPrefix(2, "L3")).isEqualTo("MCK2-L3-");
        assertThat(MockPaperFormat.paperCode(1, 2)).isEqualTo("MOCK-HSK1-02");
    }
}
