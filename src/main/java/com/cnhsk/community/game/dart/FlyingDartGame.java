package com.cnhsk.community.game.dart;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.cnhsk.learning.api.PracticeApi;

/** The bear throws a dart at a character when the learner types its Sino-Vietnamese reading. */
public final class FlyingDartGame implements GameDefinition {
    private static final int MIN_ITEMS = 20;
    private static final int MAX_ITEMS = 60;
    private static final int MIN_ANSWER_MS = 500;

    @Override public GameCode code() { return GameCode.FLYING_DART; }
    @Override public String description() { return "Gõ âm Hán Việt để gấu phi dao hạ chữ."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.CHARACTER; }
    @Override public String howToPlay() {
        return "Gõ âm Hán Việt của chữ rồi nhấn Enter, có dấu hay không dấu đều được. "
                + "Phi trúng càng nhiều chữ trong 60 giây càng cao điểm; gõ sai ba lần là dừng.";
    }
    @Override public int timeLimitSeconds() { return 60; }
    @Override public int pointsPerCorrect() { return 10; }
    @Override public int maxLives() { return 3; }

    @Override
    public boolean isAvailable(GameContext context) {
        return pool(context).size() >= MIN_ITEMS;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        var pool = pool(context);
        if (pool.size() < MIN_ITEMS) return List.of();
        var selected = GameItemBuilders.selectQuestions(List.copyOf(pool.keySet()), MAX_ITEMS, RandomGenerator.getDefault());

        List<PracticeApi.ServedItem> items = new ArrayList<>(selected.size());
        for (LexemeCard card : selected) {
            // No meaning: for many characters it is the Sino-Vietnamese reading itself (学 = "học").
            var prompt = new PracticeApi.Prompt(card.text(), null, null, null, null, null, null, null, null);
            items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.SINO_VIET_INPUT,
                    skill(), card.id(), prompt, null, null, pool.get(card), null,
                    new PracticeApi.Rules(MIN_ANSWER_MS, 0, false)));
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The practice engine matches the input against the readings saved in the snapshot.
    }

    @Override
    public Map<String, Object> uiConfig() {
        return Map.of("maxInputLength", 20);
    }

    /** Characters with at least one recorded reading, each mapped to its distinct readings. */
    private static Map<LexemeCard, List<String>> pool(GameContext context) {
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        var cards = context.contentLookup().pool(
                new GameContentQuery(Set.of("CHAR"), levels, false, false, false, context.userId(), 200));
        Map<Long, LexemeCard> byId = new LinkedHashMap<>();
        Map<LexemeCard, List<String>> eligible = new LinkedHashMap<>();
        for (LexemeCard card : cards) {
            if (!"CHAR".equals(card.kind()) || card.text() == null || card.text().isBlank()) continue;
            var readings = readings(card.sinoViet());
            if (readings.isEmpty() || byId.putIfAbsent(card.id(), card) != null) continue;
            eligible.put(card, readings);
        }
        return eligible;
    }

    private static List<String> readings(String value) {
        if (value == null) return List.of();
        return Arrays.stream(value.split("[,;/]")).map(String::strip).filter(reading -> !reading.isEmpty())
                .distinct().toList();
    }
}
