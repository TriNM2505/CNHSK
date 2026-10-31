package com.cnhsk.learning.pronunciation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.progress.UserProgress;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PronStageQueryService {

    private final PronStageRepository stages;
    private final PronStageProgressRepository progress;
    private final PracticeService practice;

    public PronStageQueryService(PronStageRepository stages, PronStageProgressRepository progress,
            PracticeService practice) {
        this.stages = stages;
        this.progress = progress;
        this.practice = practice;
    }

    @Transactional(readOnly = true)
    public List<StageSummary> list(long userId) {
        List<PronStage> ordered = stages.findAll(Sort.by("stageIndex"));
        Map<Short, PronStage> byIndex = ordered.stream()
                .collect(Collectors.toMap(PronStage::getStageIndex, Function.identity()));
        Map<Long, UserProgress> byStage = progress.findByUserIdAndTargetType(userId, "PRON_STAGE").stream()
                .collect(Collectors.toMap(UserProgress::getTargetId, Function.identity()));
        return ordered.stream().map(stage -> {
            UserProgress current = byStage.get(stage.getId());
            PronStage previous = byIndex.get((short) (stage.getStageIndex() - 1));
            boolean open = stage.getStageIndex() == 1 || current != null
                    || (previous != null && completed(previous, byStage.get(previous.getId())));
            StageSummary.State state = completed(stage, current) ? StageSummary.State.COMPLETED
                    : open ? StageSummary.State.AVAILABLE : StageSummary.State.LOCKED;
            int unitCount = (int) stage.getUnits().stream()
                    .filter(unit -> unit.audioUrl() != null && !unit.audioUrl().isBlank()).count();
            var active = practice.findActive(userId, PracticeSource.PRON_STAGE, stage.getId());
            return new StageSummary(stage.getId(), stage.getStageIndex(), stage.getName(), stage.getDescription(),
                    stage.getTargetSounds(), stage.getPassPercent(), unitCount, unitCount > 0, state,
                    current == null ? null : current.getPercent(), active == null ? null : active.sessionId(),
                    active == null ? null : active.answered().size());
        }).toList();
    }

    private static boolean completed(PronStage stage, UserProgress progress) {
        return progress != null && progress.getPercent() != null
                && progress.getPercent().compareTo(stage.getPassPercent()) >= 0;
    }
}
