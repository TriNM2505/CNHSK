package com.cnhsk.community.game;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.PracticeApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Six test-only definitions; these are never components or production game implementations. */
final class GameDefinitionFixtures {
    private GameDefinitionFixtures() { }

    static List<GameDefinition> all() { return Arrays.stream(GameCode.values()).map(code -> (GameDefinition) new Definition(code)).toList(); }

    record Definition(GameCode code) implements GameDefinition {
        @Override public String description() { return "Mô tả tự soạn cho test registry."; }
        @Override public PracticeApi.Skill skill() { return PracticeApi.Skill.VOCAB; }
        @Override public String howToPlay() { return "Chọn nghĩa của mục mẫu."; }
        @Override public int timeLimitSeconds() { return 30; }
        @Override public int pointsPerCorrect() { return 10; }
        @Override public boolean isAvailable(GameContext context) { return !pool(context).isEmpty(); }
        @Override public List<PracticeApi.ServedItem> buildRound(GameContext context) {
            var cards = pool(context);
            if (cards.isEmpty()) return List.of();
            var card = cards.get(0);
            return List.of(new PracticeApi.ServedItem(1, PracticeApi.ItemType.MEANING_CHOICE, skill(), card.id(),
                    new PracticeApi.Prompt(card.text(), null, null, null, null, null, null, null, null),
                    List.of(new PracticeApi.Option("A", "mục mẫu"), new PracticeApi.Option("B", "mục khác")),
                    "A", null, "Giải thích tự soạn.", new PracticeApi.Rules(300, 0, false)));
        }
        @Override public void validateAnswer(PracticeApi.ServedItem item, String answer, int elapsedMs) {
            if (answer == null || elapsedMs < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Dữ liệu lượt chơi không hợp lệ.", Map.of());
            }
        }
        @Override public Map<String, Object> uiConfig() { return Map.of("fixture", true); }

        private List<ContentLookup.LexemeCard> pool(GameContext context) {
            return context.contentLookup().pool(new ContentLookup.GameContentQuery(Set.of("WORD"),
                    context.hskLevel() == null ? Set.of() : Set.of(context.hskLevel()),
                    false, false, false, context.userId(), 1));
        }
    }
}
