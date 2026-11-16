package com.cnhsk.community.game.dart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.cnhsk.learning.api.GamePinyin;
import com.cnhsk.learning.api.PracticeApi;

public final class FlyingDartGame implements GameDefinition {
    private static final int MIN_ITEMS = 20;
    private static final int MAX_ITEMS = 60;

    @Override public GameCode code() { return GameCode.FLYING_DART; }
    @Override public String description() { return "Gõ pinyin của chữ hoặc từ đang rơi."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.VOCAB; }
    @Override public String howToPlay() { return "Gõ pinyin rồi nhấn Enter trước khi chữ chạm đất."; }
    @Override public int timeLimitSeconds() { return 60; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return pool(context).size() >= MIN_ITEMS;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        var pool = pool(context);
        if (pool.size() < MIN_ITEMS) return List.of();
        var cards = pool.values().stream().map(Entry::card).toList();
        var selected = GameItemBuilders.selectQuestions(cards, cards.size(), RandomGenerator.getDefault()).stream()
                .sorted(Comparator.comparing(card -> !"WORD".equals(card.kind())))
                .limit(MAX_ITEMS).toList();

        List<PracticeApi.ServedItem> items = new ArrayList<>(selected.size());
        for (LexemeCard card : selected) {
            var pronunciation = pool.get(card.id()).pronunciation();
            var prompt = new PracticeApi.Prompt(card.text(), null, null, null, null, null,
                    card.meaningShort(), null, null);
            var rules = new PracticeApi.Rules(400 + 150 * pronunciation.syllableCount(), 0, false);
            items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.PINYIN_INPUT,
                    skill(), card.id(), prompt, null, null, List.of(card.pinyin(), pronunciation.withoutTones()), null,
                    rules));
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The practice engine checks the input against the pinyin saved in the snapshot.
    }

    @Override
    public Map<String, Object> uiConfig() {
        return Map.of("fallStartMs", 6000, "fallStepMs", 150, "fallMinMs", 3000, "maxInputLength", 40);
    }

    private static Map<Long, Entry> pool(GameContext context) {
        Set<String> kinds = Set.of("WORD", "CHAR");
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        var cards = context.contentLookup().pool(
                new GameContentQuery(kinds, levels, false, false, false, context.userId(), 200));
        Map<Long, Entry> eligible = new LinkedHashMap<>();
        for (LexemeCard card : cards) {
            if (kinds.contains(card.kind()) && card.text() != null && !card.text().isBlank()) {
                GamePinyin.parse(card.pinyin()).ifPresent(pronunciation ->
                        eligible.putIfAbsent(card.id(), new Entry(card, pronunciation)));
            }
        }
        return eligible;
    }

    private record Entry(LexemeCard card, GamePinyin.Pronunciation pronunciation) { }
}
