package com.cnhsk.community.game.rain;

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

public final class CharRainGame implements GameDefinition {
    private static final int MIN_CHARACTERS = 5;
    private static final int MAX_CHARACTERS = 10;

    @Override public GameCode code() { return GameCode.CHAR_RAIN; }
    @Override public String description() { return "Tô từng nét của các chữ Hán lần lượt xuất hiện."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.WRITING; }
    @Override public String howToPlay() { return "Tô mọi nét theo thứ tự. Nếu sai, hãy thử lại nét đó."; }
    @Override public int timeLimitSeconds() { return 180; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return pool(context).stream().map(LexemeCard::id).distinct().limit(MIN_CHARACTERS).count() == MIN_CHARACTERS;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        var cards = GameItemBuilders.selectQuestions(pool(context), MAX_CHARACTERS, RandomGenerator.getDefault());
        if (cards.size() < MIN_CHARACTERS) return List.of();

        List<PracticeApi.ServedItem> items = new ArrayList<>(cards.size());
        for (LexemeCard card : cards) {
            var prompt = new PracticeApi.Prompt(card.text(), card.pinyin(), null, null, null, null,
                    card.meaningShort(), null, null);
            var rules = new PracticeApi.Rules(card.strokeCount() * 300, 0, false);
            items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.TRACE_CHAR,
                    skill(), card.id(), prompt, null, "PASS", null, null, rules));
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The engine grades PASS from the snapshot and checks the recorded duration.
    }

    @Override
    public Map<String, Object> uiConfig() {
        return Map.of("requiredStrokes", "ALL", "strokeOrder", true);
    }

    private List<LexemeCard> pool(GameContext context) {
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        return context.contentLookup().pool(new GameContentQuery(Set.of("CHAR"), levels, false, false, true,
                        context.userId(), 200)).stream()
                .filter(card -> "CHAR".equals(card.kind()) && card.strokeCount() != null && card.strokeCount() > 0)
                .toList();
    }
}
