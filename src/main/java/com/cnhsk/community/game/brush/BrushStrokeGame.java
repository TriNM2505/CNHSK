package com.cnhsk.community.game.brush;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.common.GameItemBuilders;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;

public final class BrushStrokeGame implements GameDefinition {
    private static final int MIN_ITEMS = 20;
    private static final int MAX_ITEMS = 60;

    @Override public GameCode code() { return GameCode.BRUSH_STROKE; }
    @Override public String description() { return "Tô nét đầu tiên của chữ Hán đang rơi."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.WRITING; }
    @Override public String howToPlay() { return "Tô đúng nét đầu tiên trước khi chữ chạm đất."; }
    @Override public int timeLimitSeconds() { return 60; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return pool(context).stream().map(LexemeCard::id).distinct().limit(MIN_ITEMS).count() == MIN_ITEMS;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        var cards = GameItemBuilders.selectQuestions(pool(context), MAX_ITEMS, RandomGenerator.getDefault());
        if (cards.size() < MIN_ITEMS) return List.of();

        List<PracticeApi.ServedItem> items = new ArrayList<>(cards.size());
        for (LexemeCard card : cards) {
            var prompt = new PracticeApi.Prompt(card.text(), null, null, null, null, null,
                    card.meaningShort(), null, null);
            items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.TRACE_CHAR,
                    skill(), card.id(), prompt, null, "PASS", null, null, new PracticeApi.Rules(300, 0, false)));
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The practice engine grades PASS and checks duration from the snapshot.
    }

    @Override
    public Map<String, Object> uiConfig() {
        return Map.of("requiredStrokes", 1, "fallStartMs", 6000, "fallStepMs", 150, "fallMinMs", 3000);
    }

    private static List<LexemeCard> pool(GameContext context) {
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        return context.contentLookup().pool(new GameContentQuery(Set.of("CHAR"), levels, false, false, true,
                        context.userId(), 200)).stream()
                .filter(card -> "CHAR".equals(card.kind()) && card.strokeCount() != null && card.strokeCount() > 0)
                .toList();
    }
}
