package com.cnhsk.community.game.radical;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.common.GameItemBuilders;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;

public final class RadicalMatchGame implements GameDefinition {
    private static final int MIN_CHARACTERS = 8;
    private static final int MAX_CHARACTERS = 15;
    private static final int DISTRACTOR_COUNT = 3;
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("(?U)^\\s+|\\s+$");

    @Override public GameCode code() { return GameCode.RADICAL_MATCH; }
    @Override public String description() { return "Nhận diện bộ thủ của từng chữ Hán."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.CHARACTER; }
    @Override public String howToPlay() { return "Nhìn chữ và chọn bộ thủ đúng trong bốn phương án."; }
    @Override public int timeLimitSeconds() { return 90; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return loadPool(context).playable();
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        return buildRound(context, RandomGenerator.getDefault());
    }

    List<PracticeApi.ServedItem> buildRound(GameContext context, RandomGenerator random) {
        var pool = loadPool(context);
        if (!pool.playable()) return List.of();

        var cards = GameItemBuilders.selectQuestions(pool.cards(), MAX_CHARACTERS, random);
        List<PracticeApi.ServedItem> items = new ArrayList<>(cards.size());
        for (LexemeCard card : cards) {
            String correct = radicalOf(card);
            var choice = GameItemBuilders.shuffleOptions(correct, distractors(correct, pool.radicals(), random), random);
            var prompt = new PracticeApi.Prompt(card.text(), null, null, null, null, null,
                    card.meaningShort(), null, null);
            items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.RADICAL_CHOICE,
                    skill(), card.id(), prompt, choice.options(), choice.answer(), null, null,
                    new PracticeApi.Rules(700, 0, false)));
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The practice engine validates and grades the submitted option key.
    }

    @Override public Map<String, Object> uiConfig() { return Map.of(); }

    private static List<String> distractors(String correct, List<String> radicals, RandomGenerator random) {
        List<String> candidates = new ArrayList<>(radicals);
        candidates.remove(correct);
        List<String> selected = new ArrayList<>(DISTRACTOR_COUNT);
        String partner = RadicalLookalikes.partnerOf(correct);
        if (candidates.remove(partner)) selected.add(partner);
        while (selected.size() < DISTRACTOR_COUNT) {
            selected.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return selected;
    }

    private static Pool loadPool(GameContext context) {
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        Set<Long> ids = new HashSet<>();
        var cards = context.contentLookup().pool(new GameContentQuery(Set.of("CHAR"), levels, false, true, false,
                        context.userId(), 200)).stream()
                .filter(card -> "CHAR".equals(card.kind()) && !radicalOf(card).isEmpty() && ids.add(card.id()))
                .toList();
        return new Pool(cards, cards.stream().map(RadicalMatchGame::radicalOf).distinct().toList());
    }

    private static String radicalOf(LexemeCard card) {
        return card.radical() == null ? "" : EDGE_WHITESPACE.matcher(card.radical()).replaceAll("");
    }

    private record Pool(List<LexemeCard> cards, List<String> radicals) {
        boolean playable() {
            return cards.size() >= MIN_CHARACTERS && radicals.size() > DISTRACTOR_COUNT;
        }
    }
}
