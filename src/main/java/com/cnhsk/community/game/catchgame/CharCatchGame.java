package com.cnhsk.community.game.catchgame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.common.GameItemBuilders;
import com.cnhsk.community.game.common.GameItemBuilders.DistractorMode;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;

public final class CharCatchGame implements GameDefinition {
    private static final int MIN_ITEMS = 8;
    /** More than a quick learner can answer in 60 seconds, so the round never caps the score. */
    private static final int MAX_ITEMS = 100;
    private static final int OPTIONS = 6;
    private static final List<Mode> MODES = List.of(Mode.MEANING, Mode.PINYIN, Mode.AUDIO);

    @Override public GameCode code() { return GameCode.CHAR_CATCH; }
    @Override public String description() { return "Chọn chữ hoặc từ khớp với nghĩa, pinyin hoặc âm thanh."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.LISTENING; }
    @Override public String howToPlay() { return "Đọc hoặc nghe gợi ý rồi chọn một trong sáu phương án. Đúng càng nhiều câu trong 60 giây càng cao điểm."; }
    @Override public int timeLimitSeconds() { return 60; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return isAvailable(context, RandomGenerator.getDefault());
    }

    boolean isAvailable(GameContext context, RandomGenerator random) {
        return assemble(context, MIN_ITEMS, random).size() == MIN_ITEMS;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        return buildRound(context, RandomGenerator.getDefault());
    }

    List<PracticeApi.ServedItem> buildRound(GameContext context, RandomGenerator random) {
        var items = assemble(context, MAX_ITEMS, random);
        return items.size() < MIN_ITEMS ? List.of() : items;
    }

    private List<PracticeApi.ServedItem> assemble(GameContext context, int limit, RandomGenerator random) {
        Set<Integer> levels = context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel());
        var pool = context.contentLookup().pool(
                new GameContentQuery(Set.of("CHAR", "WORD"), levels, false, false, false, context.userId(), 200));
        var remaining = new ArrayList<>(GameItemBuilders.selectQuestions(pool, pool.size(), random));
        List<PracticeApi.ServedItem> items = new ArrayList<>(limit);
        while (items.size() < limit) {
            int index = items.size();
            Mode mode = MODES.get(index % MODES.size());
            var next = nextItem(index + 1, mode, remaining, pool, random);
            if (next.isEmpty()) break;
            items.add(next.get());
        }
        return List.copyOf(items);
    }

    private Optional<PracticeApi.ServedItem> nextItem(int no, Mode requested, List<LexemeCard> remaining,
            List<LexemeCard> pool, RandomGenerator random) {
        var candidates = remaining.iterator();
        while (candidates.hasNext()) {
            var card = candidates.next();
            Mode mode = requested == Mode.AUDIO && isBlank(card.audioUrl()) ? Mode.MEANING : requested;
            if (isBlank(mode.clue(card))) continue;

            var choice = GameItemBuilders.chooseOptions(card, pool,
                    mode == Mode.MEANING ? DistractorMode.MEANING : DistractorMode.SOUND,
                    LexemeCard::text, OPTIONS, random);
            if (choice.isEmpty()) continue;

            candidates.remove();
            boolean audio = mode == Mode.AUDIO;
            var prompt = new PracticeApi.Prompt(null, mode == Mode.PINYIN ? card.pinyin() : null, null, null,
                    audio ? card.audioUrl() : null, null, mode == Mode.MEANING ? card.meaningShort() : null, null, null);
            var options = choice.get();
            return Optional.of(new PracticeApi.ServedItem(no, PracticeApi.ItemType.CATCH_CHOICE, skill(),
                    card.id(), prompt, options.options(), options.answer(), null, null,
                    new PracticeApi.Rules(300, audio ? 1 : 0, audio)));
        }
        return Optional.empty();
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // Grading and duration checks use the rules and answer saved in the snapshot.
    }

    @Override public Map<String, Object> uiConfig() { return Map.of("gridColumns", 3); }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum Mode {
        MEANING, PINYIN, AUDIO;

        String clue(LexemeCard card) {
            return switch (this) {
                case MEANING -> card.meaningShort();
                case PINYIN -> card.pinyin();
                case AUDIO -> card.audioUrl();
            };
        }
    }
}
