package com.cnhsk.community.game.pinyin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import com.cnhsk.community.game.GameContext;
import com.cnhsk.community.game.GameDefinition;
import com.cnhsk.community.game.common.GameItemBuilders;
import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;

public final class PinyinMatchGame implements GameDefinition {
    private static final int BOARD_SIZE = 6;
    private static final int MAX_BOARDS = 5;

    @Override public GameCode code() { return GameCode.PINYIN_MATCH; }
    @Override public String description() { return "Ghép từng từ tiếng Trung với thẻ pinyin của từ đó."; }
    @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.VOCAB; }
    @Override public String howToPlay() { return "Chọn một từ, rồi chọn thẻ pinyin tương ứng. Xong năm bảng càng nhanh càng cao điểm; ghép sai ba lần là dừng."; }
    @Override public int timeLimitSeconds() { return 600; }
    @Override public int maxLives() { return 3; }
    @Override public int pointsPerCorrect() { return 10; }

    @Override
    public boolean isAvailable(GameContext context) {
        return groupByPinyinKey(pool(context)).size() >= BOARD_SIZE;
    }

    @Override
    public List<PracticeApi.ServedItem> buildRound(GameContext context) {
        return buildRound(context, RandomGenerator.getDefault());
    }

    List<PracticeApi.ServedItem> buildRound(GameContext context, RandomGenerator random) {
        var cards = pool(context);
        var groups = groupByPinyinKey(GameItemBuilders.selectQuestions(cards, cards.size(), random));
        int boardCount = maxFullBoards(groups);
        if (boardCount == 0) return List.of();

        List<List<LexemeCard>> boards = new ArrayList<>(boardCount);
        for (int index = 0; index < boardCount; index++) {
            boards.add(new ArrayList<>(BOARD_SIZE));
        }
        int assigned = 0;
        int required = BOARD_SIZE * boardCount;
        for (List<LexemeCard> group : groups.values()) {
            // Consecutive cards from a group go to distinct boards; every board fills evenly.
            int take = Math.min(Math.min(group.size(), boardCount), required - assigned);
            for (int index = 0; index < take; index++) {
                boards.get(assigned % boardCount).add(group.get(index));
                assigned++;
            }
            if (assigned == required) break;
        }

        List<PracticeApi.ServedItem> items = new ArrayList<>(required);
        for (List<LexemeCard> board : boards) {
            var options = GameItemBuilders.shuffleOptions(board.get(0).pinyin(),
                    board.stream().skip(1).map(LexemeCard::pinyin).toList(), random).options();
            Map<String, String> keysByLabel = new LinkedHashMap<>();
            options.forEach(option -> keysByLabel.put(option.text(), option.key()));
            for (LexemeCard card : board) {
                var prompt = new PracticeApi.Prompt(card.text(), null, null, null, null, null, null, null, null);
                items.add(new PracticeApi.ServedItem(items.size() + 1, PracticeApi.ItemType.PINYIN_PAIR,
                        skill(), card.id(), prompt, options, keysByLabel.get(card.pinyin()), null, null,
                        new PracticeApi.Rules(600, 0, false)));
            }
        }
        return List.copyOf(items);
    }

    @Override
    public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
        // The practice engine enforces the single answer per item and grades from the snapshot.
    }

    @Override public Map<String, Object> uiConfig() { return Map.of("boards", List.of()); }

    @Override
    public Map<String, Object> uiConfig(List<PracticeApi.ServedItem> items) {
        var boards = IntStream.range(0, items.size() / BOARD_SIZE)
                .mapToObj(index -> items.subList(index * BOARD_SIZE, (index + 1) * BOARD_SIZE)
                        .stream().map(PracticeApi.ServedItem::no).toList())
                .toList();
        return Map.of("boards", boards);
    }

    private static int maxFullBoards(Map<String, List<LexemeCard>> groups) {
        for (int count = MAX_BOARDS; count > 0; count--) {
            int boards = count;
            int capacity = groups.values().stream().mapToInt(group -> Math.min(group.size(), boards)).sum();
            if (capacity >= BOARD_SIZE * boards) return boards;
        }
        return 0;
    }

    private static Map<String, List<LexemeCard>> groupByPinyinKey(List<LexemeCard> cards) {
        Map<String, List<LexemeCard>> groups = new LinkedHashMap<>();
        for (LexemeCard card : cards) {
            groups.computeIfAbsent(card.pinyinKey(), key -> new ArrayList<>()).add(card);
        }
        return groups;
    }

    private static List<LexemeCard> pool(GameContext context) {
        Set<Integer> levels = context.levels();
        var cards = context.contentLookup().pool(
                new GameContentQuery(Set.of("WORD"), levels, false, false, false, context.userId(), 200));
        Set<Long> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        List<LexemeCard> eligible = new ArrayList<>();
        for (LexemeCard card : cards) {
            if (isPlayableWord(card) && !ids.contains(card.id()) && texts.add(card.text())) {
                ids.add(card.id());
                eligible.add(card);
            }
        }
        return eligible;
    }

    private static boolean isPlayableWord(LexemeCard card) {
        return "WORD".equals(card.kind()) && card.text() != null && !card.text().isBlank()
                && card.text().codePointCount(0, card.text().length()) >= 2
                && card.pinyin() != null && !card.pinyin().isBlank()
                && card.pinyinKey() != null && !card.pinyinKey().isBlank();
    }
}
