package com.cnhsk.community.game.radical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RadicalLookalikes {
    private static final Map<String, String> LOOKALIKES = createLookup();

    private RadicalLookalikes() { }

    static String partnerOf(String radical) {
        return LOOKALIKES.get(radical);
    }

    private static Map<String, String> createLookup() {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (var pair : List.of(List.of("氵", "冫"), List.of("扌", "才"), List.of("亻", "彳"),
                List.of("日", "曰"), List.of("木", "禾"), List.of("礻", "衤"), List.of("宀", "冖"),
                List.of("艹", "竹"), List.of("讠", "言"), List.of("钅", "金"))) {
            lookup.put(pair.get(0), pair.get(1));
            lookup.put(pair.get(1), pair.get(0));
        }
        return Map.copyOf(lookup);
    }
}
