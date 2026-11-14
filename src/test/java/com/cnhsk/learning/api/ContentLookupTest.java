package com.cnhsk.learning.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.cnhsk.learning.api.ContentLookup.GameContentQuery;
import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentLookupTest {
    @Test
    void FR020_nullAndEmptySetsUseTheAgreedDefaults() {
        for (Set<String> kinds : Arrays.asList(null, Set.<String>of())) {
            for (Set<Integer> levels : Arrays.asList(null, Set.<Integer>of())) {
                var query = new GameContentQuery(kinds, levels, false, false, false, null, 200);
                assertThat(query.kinds()).containsExactlyInAnyOrder("CHAR", "WORD");
                assertThat(query.hskLevels()).isEmpty();
                assertThat(query.preferLearnedBy()).isNull();
            }
        }
    }

    @Test
    void FR020_queryAndCardCollectionsAreDefensiveAndImmutable() {
        Set<String> kinds = new HashSet<>(Set.of("CHAR"));
        Set<Integer> levels = new HashSet<>(Set.of(1, 9));
        var query = new GameContentQuery(kinds, levels, true, true, true, 71L, 1);
        kinds.add("WORD"); levels.clear();
        assertThat(query.kinds()).containsExactly("CHAR");
        assertThat(query.hskLevels()).containsExactlyInAnyOrder(1, 9);
        assertThatThrownBy(() -> query.kinds().add("WORD")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> query.hskLevels().clear()).isInstanceOf(UnsupportedOperationException.class);
        Set<String> keys = new HashSet<>(Set.of("chu"));
        var card = new LexemeCard(1, "CHAR", "字", "zì", "zi4", "chữ", keys, "宀", 6, null, 1);
        keys.add("tu");
        assertThat(card.meaningKeys()).containsExactly("chu");
        assertThatThrownBy(() -> card.meaningKeys().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new LexemeCard(1, "CHAR", "字", null, null, "chữ", null, null, null, null, null).meaningKeys()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 201, Integer.MAX_VALUE})
    void FR020_invalidLimitsAreRejected(int limit) {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContentQuery(null, null, false, false, false, null, limit));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void FR020_invalidLearnerIdsAreRejected(long id) {
        assertThatIllegalArgumentException().isThrownBy(() -> new GameContentQuery(null, null, false, false, false, id, 1));
    }

    @Test
    void FR020_invalidKindsAndNullSetMembersAreRejectedAsIllegalArguments() {
        for (String kind : Arrays.asList(null, "GRAMMAR", "char", "", "WORD' OR true --")) {
            Set<String> values = new HashSet<>(); values.add(kind);
            assertThatIllegalArgumentException().isThrownBy(() -> new GameContentQuery(values, null, false, false, false, null, 1));
        }
        for (Integer level : Arrays.asList(null, 0, 10)) {
            Set<Integer> values = new HashSet<>(); values.add(level);
            assertThatIllegalArgumentException().isThrownBy(() -> new GameContentQuery(null, values, false, false, false, null, 1));
        }
    }

    @Test
    void FR021_AC03_cardHasExactlyTheElevenContractFieldsAndNoEntityTypes() {
        assertThat(LexemeCard.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "kind", "text", "pinyin", "pinyinKey", "meaningShort", "meaningKeys",
                        "radical", "strokeCount", "audioUrl", "hskLevel");
        assertThat(ContentLookup.class.getDeclaredMethods()).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("pool");
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".entity.", "learning.content");
        });
    }
}
