package com.cnhsk.community.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cnhsk.learning.api.GameCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.*;

class GameRegistryTest {
    @Test void AT01_sixBeansStartInEnumOrderAndTheRegistryIsImmutable() {
        var definitions = new ArrayList<>(GameDefinitionFixtures.all()); Collections.reverse(definitions);
        context(definitions).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(GameRegistry.class);
            var registry = context.getBean(GameRegistry.class);
            assertThat(registry.all()).extracting(GameDefinition::code).containsExactly(GameCode.values());
            for (GameCode code : GameCode.values()) assertThat(registry.find(code)).isPresent();
            assertThatThrownBy(() -> registry.all().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatIllegalArgumentException().isThrownBy(() -> registry.find(null));
        });
        var registry = new GameRegistry(definitions); definitions.clear();
        assertThat(registry.all()).hasSize(6);
    }

    @ParameterizedTest
    @EnumSource(GameCode.class)
    void AT01_missingAnyOneOfTheSixDefinitionsPreventsStartup(GameCode missing) {
        var definitions = GameDefinitionFixtures.all().stream().filter(definition -> definition.code() != missing).toList();
        context(definitions).run(context -> assertThat(context).hasFailed());
    }

    @Test void AT01_partialAndDuplicateRegistrationsPreventStartup() {
        var definitions = new ArrayList<>(GameDefinitionFixtures.all()); definitions.add(definitions.get(0));
        context(definitions).run(context -> assertThat(context).hasFailed());
        context(List.of(GameDefinitionFixtures.all().get(0))).run(context -> assertThat(context).hasFailed());
        context(List.of(GameDefinitionFixtures.all().get(0), GameDefinitionFixtures.all().get(0)))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void AT01_nullDefinitionsAndNullCodesAreRejected() {
        assertThatIllegalStateException().isThrownBy(() -> new GameRegistry(null));
        var definitions = new ArrayList<>(GameDefinitionFixtures.all()); definitions.set(0, null);
        assertThatIllegalStateException().isThrownBy(() -> new GameRegistry(definitions));
        assertThatIllegalStateException().isThrownBy(() -> new GameRegistry(List.of(new GameDefinitionFixtures.Definition(null))));
    }

    @Test void zeroDefinitionsPreventStartup() {
        context(List.of()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Game definitions must cover all six game codes.");
        });
    }

    @Test void FR001_openApiUsesExactlyTheSharedSixCodes() throws Exception {
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("specs/007-game-platform/contracts/openapi-games.yaml"))) {
            java.util.Map<?, ?> document = new Yaml().load(input);
            var components = (java.util.Map<?, ?>) document.get("components");
            var schemas = (java.util.Map<?, ?>) components.get("schemas");
            var code = (java.util.Map<?, ?>) schemas.get("GameCode");
            assertThat(code.get("enum")).isEqualTo(java.util.Arrays.stream(GameCode.values()).map(Enum::name).toList());
        }
    }

    private ApplicationContextRunner context(List<GameDefinition> definitions) {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GameRegistry.class);
        int index = 0;
        for (GameDefinition definition : definitions) {
            GameDefinition bean = definition;
            runner = runner.withBean("gameFixture" + index++, GameDefinition.class, () -> bean);
        }
        return runner;
    }
}
