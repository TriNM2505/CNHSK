package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.PracticeSourceHandler.GradedItem;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PronStageHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T06:00:00Z");
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final PronStageRepository stages = mock(PronStageRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query upsert = mock(Query.class, RETURNS_SELF);
    private final Query openNext = mock(Query.class, RETURNS_SELF);
    private final Query selectBest = mock(Query.class, RETURNS_SELF);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Short> maxStage = mock(TypedQuery.class);
    private final PronStageHandler handler = new PronStageHandler(attempts, stages, entityManager);
    private PronStage first;

    @BeforeEach
    void setup() {
        first = stage(1);
        when(stages.findById(101L)).thenReturn(Optional.of(first));
        when(stages.findByStageIndex((short) 2)).thenReturn(Optional.of(stage(2)));
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT percent") ? selectBest : sql.contains("DO NOTHING") ? openNext : upsert;
        });
        when(upsert.executeUpdate()).thenReturn(1);
        when(openNext.executeUpdate()).thenReturn(1);
        when(selectBest.getResultList()).thenReturn(List.of(new BigDecimal("90.00")));
        when(entityManager.createQuery("SELECT MAX(s.stageIndex) FROM PronStage s", Short.class)).thenReturn(maxStage);
        when(maxStage.getSingleResult()).thenReturn((short) 8);
    }

    @Test
    void FR010_AT08_audioFailedIsAnsweredButSixUnrecordedItemsPreventFinish() {
        var context = context(10, 2, 0, 2);

        assertThatThrownBy(() -> handler.onFinish(context)).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(ErrorCode.STAGE_INCOMPLETE);
            assertThat(failure.getStatus().value()).isEqualTo(422);
            assertThat(failure.getExtra()).containsExactlyEntriesOf(Map.of("remaining", 6));
        });
        verifyNoInteractions(stages, entityManager);
    }

    @ParameterizedTest
    @CsvSource({"1,31,3.13,76.87,false", "2,1,66.67,13.33,false", "4,1,80.00,0.00,true",
            "7,3,70.00,10.00,false", "9,1,90.00,0.00,true", "10,0,100.00,0.00,true"})
    void FR011_FR016_accuracyUsesScaleTwoHalfUpAndCurrentAttemptThreshold(int correct, int wrong,
            String expected, String needed, boolean passed) {
        var extra = handler.onFinish(context(correct + wrong, correct, wrong, 0));

        assertThat(extra.get("accuracy")).isEqualTo(new BigDecimal(expected));
        assertThat(extra.get("needed")).isEqualTo(new BigDecimal(needed));
        assertThat(extra.get("passed")).isEqualTo(passed);
        assertThat(extra.get("nextStageIndex")).isEqualTo((short) 2);
        assertThat(extra.get("noScore")).isEqualTo(false);
        verify(upsert).setParameter("accuracy", new BigDecimal(expected));
    }

    @Test
    void FR011_AT09_audioFailureDoesNotReduceAccuracy() {
        var extra = handler.onFinish(context(10, 9, 0, 1));

        assertThat(extra.get("accuracy")).isEqualTo(new BigDecimal("100.00"));
        assertThat(extra.get("passed")).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR011_noScoreKeepsNullBestWithoutCreatingOrUpdatingProgress(boolean existingNullPercent) {
        when(selectBest.getResultList()).thenReturn(existingNullPercent ? Collections.singletonList(null) : List.of());

        var extra = handler.onFinish(context(10, 0, 0, 10));

        assertThat(extra).containsEntry("accuracy", null).containsEntry("needed", null).containsEntry("bestAccuracy", null)
                .containsEntry("passed", false).containsEntry("unlockedNextStage", false)
                .containsEntry("completedAllStages", false).containsEntry("noScore", true)
                .containsEntry("nextStageIndex", (short) 2);
        verifyNoInteractions(upsert, openNext, maxStage);
    }

    @Test
    void FR011_noScoreKeepsAnExistingBestAccuracyAndAllResultFields() {
        var extra = handler.onFinish(context(10, 0, 0, 10));

        assertThat(extra).containsOnlyKeys("stageIndex", "accuracy", "bestAccuracy", "passPercent", "passed",
                "unlockedNextStage", "nextStageIndex", "needed", "completedAllStages", "noScore");
        assertThat(extra.get("bestAccuracy")).isEqualTo(new BigDecimal("90.00"));
        assertThat(extra.get("accuracy")).isNull();
        assertThat(extra.get("needed")).isNull();
        assertThat(extra.get("noScore")).isEqualTo(true);
        verifyNoInteractions(upsert, openNext, maxStage);
    }

    @Test
    void FR012_AT11_lowerRetryReturnsStoredBestButDoesNotTreatTheRetryAsPassed() {
        var extra = handler.onFinish(context(10, 7, 3, 0));

        assertThat(extra).containsEntry("bestAccuracy", new BigDecimal("90.00"))
                .containsEntry("accuracy", new BigDecimal("70.00")).containsEntry("passed", false)
                .containsEntry("needed", new BigDecimal("10.00")).containsEntry("completedAllStages", false);
        verifyNoInteractions(openNext, maxStage);
        var order = inOrder(upsert, selectBest);
        order.verify(upsert).executeUpdate();
        order.verify(selectBest).getResultList();
    }

    @Test
    void FR012_progressUsesTheServerSubmissionTimeAndConfiguredPassPercent() {
        first.setPassPercent(new BigDecimal("82.50"));

        var extra = handler.onFinish(context(40, 33, 7, 0));

        assertThat(extra.get("accuracy")).isEqualTo(new BigDecimal("82.50"));
        assertThat(extra.get("passed")).isEqualTo(true);
        verify(upsert).setParameter("now", NOW);
        verify(upsert).setParameter("userId", 41L);
        verify(upsert).setParameter("stageId", 101L);
        verify(upsert).setParameter("passPercent", new BigDecimal("82.50"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void FR013_AT12_unlockFlagUsesWhetherTheInsertCreatedANewRow(int inserted) {
        when(openNext.executeUpdate()).thenReturn(inserted);

        var extra = handler.onFinish(context(10, 9, 1, 0));

        assertThat(extra.get("unlockedNextStage")).isEqualTo(inserted == 1);
        verify(openNext).setParameter("stageId", 102L);
        verify(openNext).setParameter("now", NOW);
    }

    @Test
    void FR013_missingImmediateSuccessorDoesNotOpenAnyOtherStage() {
        when(stages.findByStageIndex((short) 2)).thenReturn(Optional.empty());

        var extra = handler.onFinish(context(10, 10, 0, 0));

        assertThat(extra).containsEntry("nextStageIndex", null).containsEntry("unlockedNextStage", false)
                .containsEntry("completedAllStages", false);
        verifyNoInteractions(openNext);
        verify(stages, never()).findByStageIndex((short) 3);
    }

    @Test
    void FR015_AT15_lastImportedStageIsCompleteOnlyWhenThisAttemptPasses() {
        when(stages.findByStageIndex((short) 2)).thenReturn(Optional.empty());
        when(maxStage.getSingleResult()).thenReturn((short) 1);

        var extra = handler.onFinish(context(10, 9, 1, 0));

        assertThat(extra).containsEntry("completedAllStages", true).containsEntry("needed", new BigDecimal("0.00"))
                .containsEntry("nextStageIndex", null).containsEntry("unlockedNextStage", false);
        verifyNoInteractions(openNext);
    }

    @Test
    void FR012_AT19_persistenceFailurePropagatesWithoutCompletingTheHook() {
        doThrow(new DataIntegrityViolationException("Simulated progress failure")).when(upsert).executeUpdate();

        assertThatThrownBy(() -> handler.onFinish(context(10, 9, 1, 0))).isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(openNext, selectBest);
    }

    @ParameterizedTest
    @CsvSource({"10,7,3,false", "10,6,4,true", "10,0,3,false", "10,0,4,true", "10,0,10,true", "0,0,0,false"})
    void FR011_AT20_audioSuspicionUsesStrictlyMoreThanThirtyPercentOfServedItems(
            int total, int correct, int audioFailed, boolean expected) {
        assertThat(handler.isSuspicious(context(total, correct, 0, audioFailed))).isEqualTo(expected);
        verifyNoInteractions(stages, entityManager);
    }

    private FinishContext context(int total, int correct, int wrong, int audioFailed) {
        var snapshot = new ObjectMapper().createObjectNode();
        var items = snapshot.putArray("items");
        for (int no = 1; no <= total; no++) {
            items.addObject().put("no", no);
        }
        Attempt attempt = new Attempt(41L, "PRACTICE", "PRON_STAGE", 101L, NOW.minusSeconds(10), NOW.plusSeconds(604800), snapshot);
        when(attempts.findById(501L)).thenReturn(Optional.of(attempt));
        List<GradedItem> graded = new ArrayList<>();
        for (int no = 1; no <= correct + wrong + audioFailed; no++) {
            boolean failed = no > correct + wrong;
            var item = new ServedItem(no, ItemType.PRON_CHOICE, Skill.PRONUNCIATION, null,
                    new ServedItem.Prompt("Chọn âm", null, null, null, "https://example.test/audio.mp3", null, null, null, null),
                    List.of(new ServedItem.Option("A", "z"), new ServedItem.Option("B", "zh")), "A", null, null,
                    new ServedItem.Rules(500, 3, true));
            var answer = new Answer(no, failed ? null : no <= correct ? "A" : "B", 1000, 1, false, failed, null);
            graded.add(new GradedItem(item, answer, failed ? null : no <= correct, !failed,
                    failed ? Map.of("audioFailed", true) : Map.of()));
        }
        return new FinishContext(41L, 501L, PracticeSource.PRON_STAGE, 101L, (long) correct, (long) correct + wrong,
                false, NOW.minusSeconds(10), NOW, graded);
    }

    private PronStage stage(int index) {
        PronStage stage = new PronStage((short) index, "Tầng " + index);
        ReflectionTestUtils.setField(stage, "id", 100L + index);
        return stage;
    }
}
