package com.cnhsk.learning.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PlacementFormBuilderTest {

    @Test
    void buildsFiveMixedSheetsOfTenQuestionsPerLevelWithoutRepeats() {
        Map<Integer, List<PlacementFormBuilder.Draft>> drafts = Map.of(1, drafts(1, 100, 50), 2, drafts(2, 200, 52),
                3, drafts(3, 300, 60));
        var forms = PlacementFormBuilder.assemble(drafts, new Random(7));
        assertThat(forms).hasSize(5).allSatisfy(form -> assertThat(form).hasSize(30));
        Set<Long> seen = new HashSet<>();
        forms.forEach(form -> form.forEach(id -> assertThat(seen.add(id)).as("question %s used twice", id).isTrue()));
        for (List<Long> form : forms) {
            for (int level = 1; level <= 3; level++) {
                long base = level * 100L;
                assertThat(form.stream().filter(id -> id >= base && id < base + 100)).hasSize(10);
                // Three domains dealt in turn: each level holds every domain three or four times.
                for (long domain = 0; domain < 3; domain++) {
                    long wanted = domain;
                    long times = form.stream().filter(id -> id >= base && id < base + 100 && id % 3 == wanted).count();
                    assertThat(times).isBetween(3L, 4L);
                }
            }
        }
    }

    @Test
    void eachSheetRunsFromEasierToHarderWithTheLevelsInterleaved() {
        Map<Integer, List<PlacementFormBuilder.Draft>> drafts = Map.of(1, drafts(1, 100, 50), 2, drafts(2, 200, 50),
                3, drafts(3, 300, 50));
        Map<Long, Double> difficulty = new java.util.HashMap<>();
        drafts.values().forEach(list -> list.forEach(draft -> difficulty.put(draft.id(), draft.difficulty())));
        int interleaved = 0;
        for (List<Long> form : PlacementFormBuilder.assemble(drafts, new Random(3))) {
            for (int index = 1; index < form.size(); index++) {
                assertThat(difficulty.get(form.get(index))).isGreaterThanOrEqualTo(difficulty.get(form.get(index - 1)));
            }
            int lastHsk1 = 0;
            int firstHsk2 = form.size();
            for (int index = 0; index < form.size(); index++) {
                long id = form.get(index);
                if (id < 200) lastHsk1 = index;
                if (id >= 200 && id < 300) firstHsk2 = Math.min(firstHsk2, index);
            }
            if (lastHsk1 > firstHsk2) interleaved++;
        }
        // Hard HSK 1 items sit after easy HSK 2 items: the levels mix instead of coming in blocks.
        assertThat(interleaved).isGreaterThan(0);
    }

    /** {@code count} drafts with ids from {@code base}; domain and difficulty (1–3) follow the id. */
    private static List<PlacementFormBuilder.Draft> drafts(int level, long base, int count) {
        List<PlacementFormBuilder.Draft> drafts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long id = base + index;
            drafts.add(new PlacementFormBuilder.Draft(id, List.of("NP", "TV", "CH").get((int) (id % 3)),
                    PlacementFormScoring.priorDifficulty(level, 1 + (int) (id % 7 % 3))));
        }
        return drafts;
    }
}
