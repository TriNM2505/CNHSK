package com.cnhsk.learning.pronunciation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.FeedbackMode;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PronStageSessionServiceTest {

    private static final long USER = 41L;
    private final PronStageRepository stages = mock(PronStageRepository.class);
    private final PronStageQueryService query = mock(PronStageQueryService.class);
    private final PracticeService practice = mock(PracticeService.class);
    private final PronStageSessionService service = new PronStageSessionService(stages, query, practice);
    private PronStage stage;

    @BeforeEach
    void setup() {
        stage = new PronStage((short) 1, "Tầng 1");
        ReflectionTestUtils.setField(stage, "id", 101L);
        stage.setUnits(List.of(unit(1, "https://example.test/1.mp3")));
        when(stages.findByStageIndex((short) 1)).thenReturn(Optional.of(stage));
        when(query.list(USER)).thenReturn(List.of(summary(StageSummary.State.AVAILABLE)));
        when(practice.start(any())).thenReturn(view(List.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE})
    void FR006_AT04_outOfRangeIs404BeforeAnyLookup(int index) {
        assertFailure(index, ErrorCode.STAGE_NOT_FOUND, 404);
        verifyNoInteractions(stages, query, practice);
    }

    @Test
    void FR006_missingImportedStageIs404BeforeCheckingLockOrAudio() {
        when(stages.findByStageIndex((short) 1)).thenReturn(Optional.empty());

        assertFailure(1, ErrorCode.STAGE_NOT_FOUND, 404);

        verifyNoInteractions(query, practice);
    }

    @Test
    void FR006_stageRemovedFromTheSummaryIs404RatherThanStartingASession() {
        when(query.list(USER)).thenReturn(List.of());

        assertFailure(1, ErrorCode.STAGE_NOT_FOUND, 404);

        verifyNoInteractions(practice);
    }

    @Test
    void FR006_AT03_lockedStageIs403BeforeEmptyAudioOrResuming() {
        stage.setStageIndex((short) 5);
        stage.setUnits(List.of());
        when(stages.findByStageIndex((short) 5)).thenReturn(Optional.of(stage));
        when(query.list(USER)).thenReturn(List.of(summary(StageSummary.State.LOCKED)));

        assertThatThrownBy(() -> service.start(USER, 5)).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(ErrorCode.STAGE_LOCKED);
            assertThat(failure.getStatus().value()).isEqualTo(403);
            assertThat(failure.getExtra()).containsExactlyEntriesOf(Map.of("requiredStageIndex", 4));
        });
        verifyNoInteractions(practice);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void FR006_AT02_missingAudioIs422BeforeLookingForAnActiveSession(String audio) {
        stage.setUnits(List.of(unit(1, audio)));

        assertFailure(1, ErrorCode.STAGE_HAS_NO_UNITS, 422);

        verifyNoInteractions(practice);
    }

    @Test
    void FR006_AT02_noUnitsIs422() {
        stage.setUnits(List.of());
        assertFailure(1, ErrorCode.STAGE_HAS_NO_UNITS, 422);
        verifyNoInteractions(practice);
    }

    @Test
    void FR007_AT05_activeSessionIsReturnedUnchangedWithItsAnsweredItems() {
        var answered = IntStream.rangeClosed(1, 4)
                .mapToObj(no -> new PracticeService.ItemState(no, "C", true, "C", true)).toList();
        var active = view(answered);
        when(practice.findActive(USER, PracticeSource.PRON_STAGE, stage.getId())).thenReturn(active);

        var result = service.start(USER, 1);

        assertThat(result.created()).isFalse();
        assertThat(result.session()).isSameAs(active);
        assertThat(result.session().answered()).hasSize(4);
        verify(practice, never()).start(any());
    }

    @ParameterizedTest
    @EnumSource(value = StageSummary.State.class, names = {"AVAILABLE", "COMPLETED"})
    void FR008_openAndCompletedStagesCanCreateNewSessions(StageSummary.State state) {
        when(query.list(USER)).thenReturn(List.of(summary(state)));

        var result = service.start(USER, 1);

        assertThat(result.created()).isTrue();
        assertThat(result.session().sessionId()).isEqualTo(501L);
        var command = ArgumentCaptor.forClass(PracticeService.StartPracticeCommand.class);
        verify(practice).start(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER);
        assertThat(command.getValue().source()).isEqualTo(PracticeSource.PRON_STAGE);
        assertThat(command.getValue().refId()).isEqualTo(stage.getId());
        assertThat(command.getValue().ttlOverride()).isNull();
        assertThat(command.getValue().gameCode()).isNull();
        assertThat(command.getValue().preRecorded()).isEmpty();
        assertThat(command.getValue().sessionMeta()).isEmpty();
    }

    @Test
    void FR008_onlyUnitsWithAudioAreServedEvenWhenTheStageContainsOtherUnits() {
        stage.setUnits(List.of(unit(1, null), unit(2, "https://example.test/2.mp3"), unit(3, " "),
                unit(4, "https://example.test/4.mp3")));

        service.start(USER, 1);

        var command = ArgumentCaptor.forClass(PracticeService.StartPracticeCommand.class);
        verify(practice).start(command.capture());
        assertThat(command.getValue().items()).hasSize(2).extracting(item -> item.prompt().audioUrl())
                .containsExactlyInAnyOrder("https://example.test/2.mp3", "https://example.test/4.mp3");
    }

    @Test
    void FR008_AT06_shufflingPreservesEveryUnitAndItsCorrectOptionAcrossOneHundredSessions() {
        var units = IntStream.rangeClosed(1, 10).mapToObj(no -> unit(no, "https://example.test/" + no + ".mp3")).toList();
        stage.setUnits(units);
        for (int round = 0; round < 100; round++) {
            service.start(USER, 1);
        }
        var commands = ArgumentCaptor.forClass(PracticeService.StartPracticeCommand.class);
        verify(practice, times(100)).start(commands.capture());
        for (var command : commands.getAllValues()) {
            assertThat(command.items()).extracting(item -> item.prompt().text())
                    .containsExactlyInAnyOrderElementsOf(units.stream().map(PronUnit::prompt).toList());
            assertThat(command.items()).extracting(item -> item.no())
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
            for (var item : command.items()) {
                PronUnit original = units.stream().filter(unit -> unit.prompt().equals(item.prompt().text()))
                        .findFirst().orElseThrow();
                assertThat(item.type()).isEqualTo(ItemType.PRON_CHOICE);
                assertThat(item.skill()).isEqualTo(Skill.PRONUNCIATION);
                assertThat(item.lexemeId()).isNull();
                assertThat(item.accept()).isNull();
                assertThat(item.explanation()).isEqualTo(original.explanation());
                assertThat(item.rules().hasAudio()).isTrue();
                assertThat(item.rules().maxReplays()).isEqualTo(3);
                assertThat(item.rules().minDurationMs()).isEqualTo(500);
                assertThat(item.options()).extracting(option -> option.key()).containsExactly("A", "B", "C", "D");
                assertThat(item.options()).extracting(option -> option.text())
                        .containsExactlyInAnyOrderElementsOf(original.options().stream().map(PronUnit.Option::text).toList());
                String expected = original.options().stream().filter(option -> option.key().equals(original.answerKey()))
                        .findFirst().orElseThrow().text();
                assertThat(item.options().stream().filter(option -> option.key().equals(item.answer()))
                        .findFirst().orElseThrow().text()).isEqualTo(expected);
            }
        }
    }

    private void assertFailure(int index, ErrorCode code, int status) {
        assertThatThrownBy(() -> service.start(USER, index)).isInstanceOfSatisfying(ApiException.class, failure -> {
            assertThat(failure.getCode()).isEqualTo(code);
            assertThat(failure.getStatus().value()).isEqualTo(status);
        });
    }

    private StageSummary summary(StageSummary.State state) {
        return new StageSummary(stage.getId(), stage.getStageIndex(), stage.getName(), null, List.of(),
                new BigDecimal("80.00"), stage.getUnits().size(), !stage.getUnits().isEmpty(), state, null, null, null);
    }

    private PracticeService.SessionView view(List<PracticeService.ItemState> answered) {
        return new PracticeService.SessionView(501L, PracticeSource.PRON_STAGE, stage.getId(), FeedbackMode.IMMEDIATE,
                "IN_PROGRESS", Instant.EPOCH, Instant.EPOCH.plusSeconds(604800), List.of(), answered, Map.of());
    }

    private PronUnit unit(int no, String audio) {
        return new PronUnit("U" + no, audio, "Chọn âm " + no, List.of(new PronUnit.Option("A", "z" + no),
                new PronUnit.Option("B", "zh" + no), new PronUnit.Option("C", "s" + no),
                new PronUnit.Option("D", "sh" + no)), "C", "Giải thích " + no);
    }
}
