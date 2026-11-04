package com.cnhsk.learning.exam;

import java.util.List;
import java.util.Objects;

public final class PlacementAlgorithm {

    public Decision decide(List<BlockResult> blocks, PlacementConfig config, boolean bankExhausted) {
        Objects.requireNonNull(blocks, "blocks must not be null");
        Objects.requireNonNull(config, "config must not be null");

        if (blocks.isEmpty()) {
            return new Next(config.getStartLevel());
        }

        PlacementLevel estimate = estimate(blocks, config);
        int totalItems = blocks.stream().mapToInt(BlockResult::total).sum();

        if (bankExhausted || totalItems >= config.getMaxItems()
                || totalItems >= config.getMinItems() && estimate.equals(estimateWithoutLast(blocks, config))) {
            return new Conclusion(estimate, confidence(estimate, blocks, config, bankExhausted));
        }

        return new Next(nextLevel(blocks.get(blocks.size() - 1), config));
    }

    private PlacementLevel estimateWithoutLast(List<BlockResult> blocks, PlacementConfig config) {
        if (blocks.size() == 1) {
            return PlacementLevel.BELOW_1;
        }
        return estimate(blocks.subList(0, blocks.size() - 1), config);
    }

    private PlacementLevel estimate(List<BlockResult> blocks, PlacementConfig config) {
        if (blocks.size() >= 2) {
            BlockResult previous = blocks.get(blocks.size() - 2);
            BlockResult latest = blocks.get(blocks.size() - 1);
            if (previous.level() == 3 && latest.level() == 3 && isUp(previous, config) && isUp(latest, config)) {
                return PlacementLevel.ABOVE_3;
            }
        }

        for (int level = 3; level >= 1; level--) {
            int candidateLevel = level;
            int total = blocks.stream().filter(block -> block.level() == candidateLevel)
                    .mapToInt(BlockResult::total).sum();
            int correct = blocks.stream().filter(block -> block.level() == candidateLevel)
                    .mapToInt(BlockResult::correct).sum();
            if (total >= config.getBlockSize() && correct * 100 >= total * config.getThresholds().get(level)) {
                return PlacementLevel.forLevel(level);
            }
        }
        return PlacementLevel.BELOW_1;
    }

    private Confidence confidence(PlacementLevel estimate, List<BlockResult> blocks,
            PlacementConfig config, boolean bankExhausted) {
        if (bankExhausted) {
            return Confidence.TENTATIVE;
        }
        int evidenceLevel = switch (estimate) {
            case BELOW_1 -> 1;
            case HSK1 -> 1;
            case HSK2 -> 2;
            case HSK3, ABOVE_3 -> 3;
        };
        int evidence = blocks.stream().filter(block -> block.level() == evidenceLevel)
                .mapToInt(BlockResult::total).sum();
        return evidence >= config.getSureMinItems() ? Confidence.SURE : Confidence.TENTATIVE;
    }

    private int nextLevel(BlockResult latest, PlacementConfig config) {
        int adjustment = isUp(latest, config) ? 1 : isDown(latest, config) ? -1 : 0;
        return Math.max(1, Math.min(3, latest.level() + adjustment));
    }

    private boolean isUp(BlockResult block, PlacementConfig config) {
        return block.correct() >= config.getUpAt();
    }

    private boolean isDown(BlockResult block, PlacementConfig config) {
        return block.correct() <= config.getDownAt();
    }

    public record BlockResult(int level, int correct, int total) {
        public BlockResult {
            if (level < 1 || level > 3 || total < 1 || correct < 0 || correct > total) {
                throw new IllegalArgumentException("Placement block result is invalid");
            }
        }
    }

    public sealed interface Decision permits Next, Conclusion {
    }

    public record Next(int level) implements Decision {
        public Next {
            if (level < 1 || level > 3) {
                throw new IllegalArgumentException("Placement level must be between 1 and 3");
            }
        }
    }

    public record Conclusion(PlacementLevel level, Confidence confidence) implements Decision {
        public Conclusion {
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(confidence, "confidence must not be null");
        }
    }

    public enum PlacementLevel {
        BELOW_1,
        HSK1,
        HSK2,
        HSK3,
        ABOVE_3;

        private static PlacementLevel forLevel(int level) {
            return switch (level) {
                case 1 -> HSK1;
                case 2 -> HSK2;
                case 3 -> HSK3;
                default -> throw new IllegalArgumentException("Unsupported placement level: " + level);
            };
        }
    }

    public enum Confidence {
        SURE,
        TENTATIVE
    }
}
