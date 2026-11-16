package com.cnhsk.learning.api;

import java.util.Optional;

import com.cnhsk.learning.content.text.PinyinUtils;

/** Parses stored pinyin for game snapshots without exposing the learning implementation. */
public final class GamePinyin {
    private GamePinyin() { }

    public static Optional<Pronunciation> parse(String pinyin) {
        if (pinyin == null || pinyin.isBlank()) return Optional.empty();
        try {
            String numbered = PinyinUtils.toNumbered(pinyin);
            String withoutTones = PinyinUtils.normalize(pinyin);
            if (numbered.isBlank() || withoutTones.isBlank()) return Optional.empty();
            return Optional.of(new Pronunciation(withoutTones, numbered.split(" ").length));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record Pronunciation(String withoutTones, int syllableCount) { }
}
