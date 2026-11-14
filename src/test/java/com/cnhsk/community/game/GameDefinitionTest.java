package com.cnhsk.community.game;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GameDefinitionTest {
    @ParameterizedTest
    @EnumSource(GameCode.class)
    void FR001_sixTestDefinitionsUseTheSharedCodeAndServerSnapshot(GameCode code) {
        var definition = new GameDefinitionFixtures.Definition(code);
        var calls = new java.util.ArrayList<ContentLookup.GameContentQuery>();
        ContentLookup lookup = query -> {
            calls.add(query);
            return List.of(new ContentLookup.LexemeCard(7, "WORD", "学习", "xuéxí", "xue2xi2", "học tập",
                    Set.of("hoc tap"), null, null, null, 1));
        };
        GameContext context = new GameContext(42L, 1, lookup);
        assertThat(definition.name()).isEqualTo(code.displayName());
        assertThat(definition.description()).isNotBlank(); assertThat(definition.howToPlay()).isNotBlank();
        assertThat(definition.isAvailable(context)).isTrue();
        var items = definition.buildRound(context);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).answer()).isEqualTo("A");
        assertThat(items.get(0).rules().minDurationMs()).isEqualTo(300);
        assertThatCode(() -> definition.validateAnswer(items.get(0), "A", 1)).doesNotThrowAnyException();
        assertThat(definition.uiConfig()).containsEntry("fixture", true);
        assertThat(calls).hasSize(2).allSatisfy(query -> {
            assertThat(query.preferLearnedBy()).isEqualTo(42L); assertThat(query.hskLevels()).containsExactly(1);
        });
    }

    @Test void Q_T03_02_validationRejectsExtraRulesWithoutReturningClientPoints() throws Exception {
        var definition = new GameDefinitionFixtures.Definition(GameCode.PINYIN_MATCH);
        assertThat(GameDefinition.class.getMethod("validateAnswer", com.cnhsk.learning.api.PracticeApi.ServedItem.class, String.class, int.class)
                .getReturnType()).isEqualTo(void.class);
        assertThatThrownBy(() -> definition.validateAnswer(null, "A", -1)).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(exception.getStatus().value()).isEqualTo(422);
        });
        assertThatThrownBy(() -> definition.validateAnswer(null, null, 1)).isInstanceOf(ApiException.class);
        for (Method method : GameDefinition.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                if (parameter.getPackageName().startsWith("com.cnhsk.learning")) {
                    assertThat(parameter.getPackageName()).isEqualTo("com.cnhsk.learning.api");
                }
            }
        }
    }

    @Test void FR008_contextSupportsGuestsAndEmptyPoolsWithoutAProductionPlaceholder() {
        ContentLookup empty = query -> List.of();
        var context = new GameContext(null, null, empty);
        var definition = new GameDefinitionFixtures.Definition(GameCode.CHAR_RAIN);
        assertThat(definition.isAvailable(context)).isFalse(); assertThat(definition.buildRound(context)).isEmpty();
        assertThat(context.userId()).isNull(); assertThat(context.hskLevel()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContext(0L, null, empty));
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContext(-1L, null, empty));
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContext(null, null, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1, 3, 9})
    void FR008_contextKeepsTheApiLevelRange(Integer level) {
        assertThat(new GameContext(null, level, query -> List.of()).hskLevel()).isEqualTo(level);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10})
    void FR008_invalidLevelIsAnInternalArgumentError(int level) {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContext(null, level, query -> List.of()));
    }
}
