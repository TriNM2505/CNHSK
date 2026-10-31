package com.cnhsk.community.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.TargetType;
import jakarta.persistence.Embeddable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReactionIdTest {

    @Test
    void exposesAllFourKeyComponents() {
        ReactionId id = new ReactionId(11L, TargetType.COMMENT, 42L, ReactionKind.REPORT);

        assertEquals(11L, id.getUserId());
        assertEquals(TargetType.COMMENT, id.getTargetType());
        assertEquals(42L, id.getTargetId());
        assertEquals(ReactionKind.REPORT, id.getKind());
    }

    @Test
    void equalKeysAreReflexiveSymmetricAndTransitive() {
        ReactionId first = postLike();
        ReactionId second = postLike();
        ReactionId third = postLike();

        assertTrue(first.equals(first));
        assertTrue(first.equals(second));
        assertTrue(second.equals(first));
        assertTrue(second.equals(third));
        assertTrue(first.equals(third));
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(second.hashCode(), third.hashCode());
    }

    @ParameterizedTest
    @MethodSource("keysDifferingInOneComponent")
    void eachKeyComponentParticipatesInEquality(ReactionId different) {
        assertNotEquals(postLike(), different);
        assertNotEquals(different, postLike());
    }

    @Test
    void neverEqualsNullOrAnUnrelatedType() {
        ReactionId id = postLike();

        assertFalse(id.equals(null));
        assertFalse(id.equals("11:POST:42:LIKE"));
        assertFalse(id.equals(new Object()));
    }

    @Test
    void equivalentKeysWorkInHashCollectionsAndDistinctTuplesRemainSeparate() {
        Set<ReactionId> ids = new HashSet<>();
        ids.add(postLike());
        keysDifferingInOneComponent().forEach(ids::add);

        assertEquals(5, ids.size());
        assertTrue(ids.contains(postLike()));
        assertFalse(ids.add(postLike()));
        Map<ReactionId, String> values = new HashMap<>();
        values.put(postLike(), "saved reaction");
        assertEquals("saved reaction", values.get(postLike()));
    }

    @Test
    void supportsEveryReactionTargetAndKindStoredByFlyway() {
        assertEquals(Set.of("POST", "COMMENT", "USER", "COLLECTION"),
                Stream.of(TargetType.values()).map(Enum::name).collect(Collectors.toSet()));
        assertEquals(Set.of("LIKE", "FOLLOW", "REPORT"),
                Stream.of(ReactionKind.values()).map(Enum::name).collect(Collectors.toSet()));

        Set<ReactionId> ids = new HashSet<>();
        for (TargetType targetType : TargetType.values()) {
            for (ReactionKind kind : ReactionKind.values()) {
                ids.add(new ReactionId(11L, targetType, 42L, kind));
                assertTrue(ids.contains(new ReactionId(11L, targetType, 42L, kind)));
            }
        }
        assertEquals(12, ids.size());
    }

    @Test
    void serializationPreservesCompositeKeyIdentity() throws Exception {
        ReactionId original = new ReactionId(11L, TargetType.COLLECTION, 42L, ReactionKind.FOLLOW);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        ReactionId restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ReactionId) input.readObject();
        }

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals(original.getUserId(), restored.getUserId());
        assertEquals(original.getTargetType(), restored.getTargetType());
        assertEquals(original.getTargetId(), restored.getTargetId());
        assertEquals(original.getKind(), restored.getKind());
    }

    @Test
    void supportsJpaConstructionAndEqualityBeforeFieldsAreHydrated() throws Exception {
        assertTrue(ReactionId.class.isAnnotationPresent(Embeddable.class));
        assertTrue(Serializable.class.isAssignableFrom(ReactionId.class));
        assertTrue(Modifier.isProtected(ReactionId.class.getDeclaredConstructor().getModifiers()));

        ReactionId first = new ReactionId();
        ReactionId second = new ReactionId();
        assertNull(first.getUserId());
        assertNull(first.getTargetType());
        assertNull(first.getTargetId());
        assertNull(first.getKind());
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, postLike());
        assertNotEquals(postLike(), first);
    }

    private static ReactionId postLike() {
        return new ReactionId(11L, TargetType.POST, 42L, ReactionKind.LIKE);
    }

    private static Stream<ReactionId> keysDifferingInOneComponent() {
        return Stream.of(
                new ReactionId(12L, TargetType.POST, 42L, ReactionKind.LIKE),
                new ReactionId(11L, TargetType.COMMENT, 42L, ReactionKind.LIKE),
                new ReactionId(11L, TargetType.POST, 43L, ReactionKind.LIKE),
                new ReactionId(11L, TargetType.POST, 42L, ReactionKind.REPORT));
    }
}
