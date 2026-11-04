package com.cnhsk.learning.exam;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.exam.PlacementAlgorithm.BlockResult;
import com.cnhsk.learning.exam.PlacementAlgorithm.Confidence;
import com.cnhsk.learning.exam.PlacementAlgorithm.Conclusion;
import com.cnhsk.learning.exam.PlacementAlgorithm.Next;
import com.cnhsk.learning.exam.PlacementAlgorithm.PlacementLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementAlgorithmTest {

    private final PlacementAlgorithm algorithm = new PlacementAlgorithm();
    private final PlacementConfig config = new PlacementConfig();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(PlacementConfig.class);

    @Test
    void FR017_AT16_fullBlockMovesUp() {
        assertThat(algorithm.decide(List.of(block(2, 5)), config, false)).isEqualTo(new Next(3));
    }

    @Test
    void FR017_AT16_lowBlockMovesDown() {
        assertThat(algorithm.decide(List.of(block(2, 2)), config, false)).isEqualTo(new Next(1));
    }

    @Test
    void FR017_AT16_middleBlockStaysAtSameLevel() {
        assertThat(algorithm.decide(List.of(block(2, 3)), config, false)).isEqualTo(new Next(2));
    }

    @Test
    void FR017_AT16_fivePerfectBlocksConcludeAboveHsk3Sure() {
        List<BlockResult> blocks = List.of(block(2, 5), block(3, 5), block(3, 5), block(3, 5), block(3, 5));

        assertThat(algorithm.decide(blocks, config, false))
                .isEqualTo(new Conclusion(PlacementLevel.ABOVE_3, Confidence.SURE));
    }

    @Test
    void FR017_AT16_fiveFailedBlocksConcludeBelowHsk1Sure() {
        List<BlockResult> blocks = List.of(block(2, 0), block(1, 0), block(1, 0), block(1, 0), block(1, 0));

        assertThat(algorithm.decide(blocks, config, false))
                .isEqualTo(new Conclusion(PlacementLevel.BELOW_1, Confidence.SURE));
    }

    @Test
    void FR017_AT16_oscillatingBlocksStopAtMaximumItems() {
        List<BlockResult> blocks = List.of(
                block(2, 4), block(3, 4), block(3, 3), block(3, 4), block(3, 4), block(3, 2), block(2, 4));
        assertThat(algorithm.decide(blocks.subList(0, 5), config, false)).isInstanceOf(Next.class);
        assertThat(algorithm.decide(blocks.subList(0, 6), config, false)).isInstanceOf(Next.class);

        assertThat(algorithm.decide(blocks, config, false))
                .isEqualTo(new Conclusion(PlacementLevel.HSK2, Confidence.SURE));
    }

    @Test
    void FR017_AT16_exhaustedBankConcludesTentatively() {
        assertThat(algorithm.decide(List.of(block(2, 4)), config, true))
                .isEqualTo(new Conclusion(PlacementLevel.HSK2, Confidence.TENTATIVE));
    }

    @Test
    void FR017_defaultsBindAtStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PlacementConfig.class);
            PlacementConfig properties = context.getBean(PlacementConfig.class);
            assertThat(properties.getBlockSize()).isEqualTo(5);
            assertThat(properties.getMinItems()).isEqualTo(25);
            assertThat(properties.getMaxItems()).isEqualTo(35);
            assertThat(properties.getUpAt()).isEqualTo(4);
            assertThat(properties.getDownAt()).isEqualTo(2);
            assertThat(properties.getThresholds()).containsExactlyInAnyOrderEntriesOf(Map.of(1, 60, 2, 65, 3, 75));
            assertThat(properties.getSureMinItems()).isEqualTo(10);
            assertThat(properties.getStartLevel()).isEqualTo(2);
            assertThat(properties.getMinBankPerLevel()).isEqualTo(10);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "block-size=0", "min-items=36", "up-at=6", "down-at=4", "start-level=4",
            "thresholds.3=101", "sure-min-items=0", "min-bank-per-level=0", "min-bank-per-level=4"
    })
    void FR017_invalidConfigurationPreventsStartup(String setting) {
        contextRunner.withPropertyValues("cnhsk.placement." + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void FR021_E10_noEvidenceAndExhaustedBankConcludesTentatively() {
        assertThat(algorithm.decide(List.of(), config, true))
                .isEqualTo(new Conclusion(PlacementLevel.BELOW_1, Confidence.TENTATIVE));
        assertThat(algorithm.decide(List.of(), config, false)).isEqualTo(new Next(config.getStartLevel()));
    }

    private BlockResult block(int level, int correct) {
        return new BlockResult(level, correct, config.getBlockSize());
    }
}
