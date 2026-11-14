package com.cnhsk.community.game;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;

/** Stateless game extension point. Answer keys are server-only parts of the served snapshot. */
public interface GameDefinition {
    GameCode code();
    default String name() { return code().displayName(); }
    String description();
    PracticeApi.Skill skill();
    String howToPlay();
    int timeLimitSeconds();
    int pointsPerCorrect();
    boolean isAvailable(GameContext context);
    List<PracticeApi.ServedItem> buildRound(GameContext context);

    /** Extra rules may reject with VALIDATION_FAILED/422; the practice engine owns grading and score. */
    void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs);

    Map<String, Object> uiConfig();
}
