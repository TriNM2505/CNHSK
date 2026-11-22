package com.cnhsk.learning.api;

import java.util.List;
import java.util.Set;

/** Read-only content boundary for games; no learning entity types cross this interface. */
public interface ContentLookup {
    List<LexemeCard> pool(GameContentQuery query);

    record GameContentQuery(Set<String> kinds, Set<Integer> hskLevels, boolean requireAudio,
            boolean requireRadical, boolean requireStrokeCount, Long preferLearnedBy, int limit) {
        public GameContentQuery {
            if (limit < 1 || limit > 200) throw new IllegalArgumentException("Game content limit must be between 1 and 200.");
            if (preferLearnedBy != null && preferLearnedBy <= 0) {
                throw new IllegalArgumentException("Preferred learner id must be positive.");
            }
            if (kinds == null || kinds.isEmpty()) {
                kinds = Set.of("CHAR", "WORD");
            } else {
                for (String kind : kinds) {
                    if (!"CHAR".equals(kind) && !"WORD".equals(kind)) {
                        throw new IllegalArgumentException("Game content kinds must be CHAR or WORD.");
                    }
                }
                kinds = Set.copyOf(kinds);
            }
            if (hskLevels == null) {
                hskLevels = Set.of();
            } else {
                for (Integer level : hskLevels) {
                    if (level == null || level < 1 || level > 9) {
                        throw new IllegalArgumentException("Game content levels must be between 1 and 9.");
                    }
                }
                hskLevels = Set.copyOf(hskLevels);
            }
        }
    }

    /** {@code sinoViet} holds the Sino-Vietnamese readings, comma separated, or null when none is recorded. */
    record LexemeCard(long id, String kind, String text, String pinyin, String pinyinKey, String meaningShort,
            Set<String> meaningKeys, String radical, Integer strokeCount, String audioUrl, Integer hskLevel,
            String sinoViet) {
        public LexemeCard {
            meaningKeys = meaningKeys == null ? Set.of() : Set.copyOf(meaningKeys);
        }

        /** Cards for games that do not use the Sino-Vietnamese reading. */
        public LexemeCard(long id, String kind, String text, String pinyin, String pinyinKey, String meaningShort,
                Set<String> meaningKeys, String radical, Integer strokeCount, String audioUrl, Integer hskLevel) {
            this(id, kind, text, pinyin, pinyinKey, meaningShort, meaningKeys, radical, strokeCount, audioUrl, hskLevel, null);
        }
    }
}
