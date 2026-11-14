package com.cnhsk.community.game;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.api.GameCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GameRegistry {
    private static final Logger log = LoggerFactory.getLogger(GameRegistry.class);
    private final Map<GameCode, GameDefinition> definitions;
    private final List<GameDefinition> ordered;

    public GameRegistry(List<GameDefinition> definitions) {
        if (definitions == null) throw new IllegalStateException("Game definitions must not be null.");
        EnumMap<GameCode, GameDefinition> byCode = new EnumMap<>(GameCode.class);
        for (GameDefinition definition : definitions) {
            if (definition == null || definition.code() == null) {
                throw new IllegalStateException("Game definition and its code must not be null.");
            }
            if (byCode.putIfAbsent(definition.code(), definition) != null) {
                throw new IllegalStateException("Game definitions must have unique codes.");
            }
        }
        if (byCode.isEmpty()) {
            // Temporary until spec 008's final task: no production placeholders are registered.
            log.warn("chưa có GameDefinition nào");
        } else if (!byCode.keySet().equals(EnumSet.allOf(GameCode.class))) {
            throw new IllegalStateException("Game definitions must cover all six game codes.");
        }
        this.definitions = Map.copyOf(byCode);
        this.ordered = byCode.values().stream().toList();
    }

    public List<GameDefinition> all() { return ordered; }

    public Optional<GameDefinition> find(GameCode code) {
        if (code == null) throw new IllegalArgumentException("Game code is required.");
        return Optional.ofNullable(definitions.get(code));
    }
}
