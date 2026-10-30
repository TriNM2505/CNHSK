package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.FeedbackMode;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.progress.UserProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PronStageQueryServiceTest {

    private static final long USER = 41L;

    private final PronStageRepository stages = mock(PronStageRepository.class);
    private final PronStageProgressRepository progress = mock(PronStageProgressRepository.class);
    private final PracticeService practice = mock(PracticeService.class);
    private final PronStageQueryService query = new PronStageQueryService(stages, progress, practice);

    @BeforeEach
    void setup() {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of());
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE")).thenReturn(List.of());
    }

    @Test
    void FR001_noImportedStagesReturnsAnEmptyList() {
        assertThat(query.list(USER)).isEmpty();
        verifyNoInteractions(practice);
    }

    @Test
    void FR001_AT01_onlyTheFirstOfEightStagesIsInitiallyAvailable() {
        var ordered = java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(this::stage).toList();
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(ordered);

        var result = query.list(USER);

        assertThat(result).extracting(StageSummary::stageIndex)
                .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7, (short) 8);
        assertThat(result.get(0).state()).isEqualTo(StageSummary.State.AVAILABLE);
        assertThat(result.subList(1, 8)).extracting(StageSummary::state).containsOnly(StageSummary.State.LOCKED);
        assertThat(result).extracting(StageSummary::bestAccuracy).containsOnlyNulls();
        verify(stages).findAll(Sort.by("stageIndex"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void FR002_unitsWithoutAnAudioUrlAreExcluded(String missingAudio) {
        PronStage stage = stage(1);
        stage.setUnits(List.of(unit(missingAudio), unit("https://example.test/valid.mp3")));
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage));

        var summary = query.list(USER).get(0);

        assertThat(summary.unitCount()).isEqualTo(1);
        assertThat(summary.playable()).isTrue();
    }

    @Test
    void FR002_AT02_stageWithoutUnitsIsAvailableButNotPlayable() {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage(1)));

        var summary = query.list(USER).get(0);

        assertThat(summary.unitCount()).isZero();
        assertThat(summary.playable()).isFalse();
        assertThat(summary.state()).isEqualTo(StageSummary.State.AVAILABLE);
    }

    @ParameterizedTest
    @CsvSource({"79.99,AVAILABLE", "80.00,COMPLETED", "100.00,COMPLETED"})
    void FR003_completionUsesBestAccuracyAndIncludesTheExactThreshold(String accuracy, StageSummary.State expected) {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage(1)));
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE")).thenReturn(List.of(progress(1, accuracy)));

        var summary = query.list(USER).get(0);

        assertThat(summary.state()).isEqualTo(expected);
        assertThat(summary.bestAccuracy()).isEqualByComparingTo(accuracy);
    }

    @Test
    void FR004_AT16_completedPredecessorOpensANewlyImportedStageWithoutAProgressRow() {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage(2), stage(3), stage(4)));
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE")).thenReturn(List.of(progress(2, "90.00")));

        var result = query.list(USER);

        assertThat(result).extracting(StageSummary::state)
                .containsExactly(StageSummary.State.COMPLETED, StageSummary.State.AVAILABLE, StageSummary.State.LOCKED);
    }

    @Test
    void FR004_AT17_existingProgressRowKeepsTheNextStageOpenAfterTheThresholdIncreases() {
        PronStage first = stage(1);
        first.setPassPercent(new BigDecimal("95.00"));
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(first, stage(2)));
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE"))
                .thenReturn(List.of(progress(1, "90.00"), progress(2, null)));

        var result = query.list(USER);

        assertThat(result).extracting(StageSummary::state)
                .containsExactly(StageSummary.State.AVAILABLE, StageSummary.State.AVAILABLE);
        assertThat(result.get(1).bestAccuracy()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"89.99,LOCKED", "90.00,AVAILABLE"})
    void FR004_unlockUsesThePreviousStagesConfiguredThreshold(String accuracy, StageSummary.State expected) {
        PronStage first = stage(1);
        first.setPassPercent(new BigDecimal("90.00"));
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(first, stage(2)));
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE")).thenReturn(List.of(progress(1, accuracy)));

        assertThat(query.list(USER).get(1).state()).isEqualTo(expected);
    }

    @Test
    void FR004_missingPredecessorDoesNotUnlockALaterStage() {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage(1), stage(3)));
        when(progress.findByUserIdAndTargetType(USER, "PRON_STAGE")).thenReturn(List.of(progress(1, "100.00")));

        assertThat(query.list(USER).get(1).state()).isEqualTo(StageSummary.State.LOCKED);
    }

    @Test
    void FR005_activeSessionFieldsUseTheCallersSessionAndAnsweredCount() {
        PronStage stage = stage(1);
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage));
        var answered = List.of(new PracticeService.ItemState(1, "B", true, "B", true),
                new PracticeService.ItemState(2, null, null, null, false));
        when(practice.findActive(USER, PracticeSource.PRON_STAGE, stage.getId()))
                .thenReturn(new PracticeService.SessionView(501L, PracticeSource.PRON_STAGE, stage.getId(),
                        FeedbackMode.IMMEDIATE, "IN_PROGRESS", Instant.EPOCH, Instant.EPOCH.plusSeconds(604800),
                        List.of(), answered, Map.of()));

        var summary = query.list(USER).get(0);

        assertThat(summary.activeSessionId()).isEqualTo(501L);
        assertThat(summary.activeAnswered()).isEqualTo(2);
        verify(practice).findActive(USER, PracticeSource.PRON_STAGE, stage.getId());
    }

    @Test
    void FR005_noActiveSessionReturnsNullForBothFields() {
        when(stages.findAll(Sort.by("stageIndex"))).thenReturn(List.of(stage(1)));

        var summary = query.list(USER).get(0);

        assertThat(summary.activeSessionId()).isNull();
        assertThat(summary.activeAnswered()).isNull();
    }

    private PronStage stage(int index) {
        PronStage stage = new PronStage((short) index, "Tầng " + index);
        ReflectionTestUtils.setField(stage, "id", 100L + index);
        return stage;
    }

    private UserProgress progress(int index, String percent) {
        return new UserProgress(USER, "PRON_STAGE", 100L + index, percent == null ? null : new BigDecimal(percent));
    }

    private PronUnit unit(String audio) {
        return new PronUnit("U1", audio, "Chọn âm", List.of(new PronUnit.Option("A", "z"),
                new PronUnit.Option("B", "zh")), "B", "Giải thích nội bộ");
    }
}
