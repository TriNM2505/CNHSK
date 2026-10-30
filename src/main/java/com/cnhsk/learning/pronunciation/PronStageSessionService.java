package com.cnhsk.learning.pronunciation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PronStageSessionService {

    private final PronStageRepository stages;
    private final PronStageQueryService query;
    private final PracticeService practice;

    public PronStageSessionService(PronStageRepository stages, PronStageQueryService query, PracticeService practice) {
        this.stages = stages;
        this.query = query;
        this.practice = practice;
    }

    @Transactional
    public StartResult start(long userId, int stageIndex) {
        if (stageIndex < 1 || stageIndex > 8) {
            throw notFound();
        }
        PronStage stage = stages.findByStageIndex((short) stageIndex).orElseThrow(PronStageSessionService::notFound);
        StageSummary summary = query.list(userId).stream().filter(item -> item.id() == stage.getId())
                .findFirst().orElseThrow(PronStageSessionService::notFound);
        if (summary.state() == StageSummary.State.LOCKED) {
            throw new ApiException(ErrorCode.STAGE_LOCKED, HttpStatus.FORBIDDEN,
                    "Hoàn thành tầng trước để mở tầng này.", Map.of("requiredStageIndex", stageIndex - 1));
        }
        List<PronUnit> units = new ArrayList<>(stage.getUnits().stream()
                .filter(unit -> unit.audioUrl() != null && !unit.audioUrl().isBlank()).toList());
        if (units.isEmpty()) {
            throw new ApiException(ErrorCode.STAGE_HAS_NO_UNITS, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nội dung tầng đang được cập nhật.", Map.of());
        }
        var active = practice.findActive(userId, PracticeSource.PRON_STAGE, stage.getId());
        if (active != null) {
            return new StartResult(active, false);
        }
        Collections.shuffle(units, ThreadLocalRandom.current());
        List<ServedItem> items = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            items.add(item(index + 1, units.get(index)));
        }
        var session = practice.start(new PracticeService.StartPracticeCommand(userId, PracticeSource.PRON_STAGE,
                stage.getId(), null, items, null, null, null));
        return new StartResult(session, true);
    }

    private static ServedItem item(int no, PronUnit unit) {
        List<PronUnit.Option> shuffled = new ArrayList<>(unit.options());
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        List<ServedItem.Option> options = new ArrayList<>();
        String answer = null;
        for (int index = 0; index < shuffled.size(); index++) {
            PronUnit.Option option = shuffled.get(index);
            String key = Character.toString((char) ('A' + index));
            options.add(new ServedItem.Option(key, option.text()));
            if (option.key().equals(unit.answerKey())) {
                answer = key;
            }
        }
        return new ServedItem(no, ItemType.PRON_CHOICE, Skill.PRONUNCIATION, null,
                new ServedItem.Prompt(unit.prompt(), null, null, null, unit.audioUrl(), null, null, null, null),
                options, Objects.requireNonNull(answer, "Imported pronunciation unit must have an answer key."),
                null, unit.explanation(), new ServedItem.Rules(500, 3, true));
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.STAGE_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy tầng phát âm.", Map.of());
    }

    public record StartResult(PracticeService.SessionView session, boolean created) {
    }
}
