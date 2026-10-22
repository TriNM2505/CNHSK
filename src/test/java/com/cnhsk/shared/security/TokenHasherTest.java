package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void hashesTokenAsLowercaseSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                hasher.hash("abc"));
    }

    @Test
    void rejectsNullOrBlankTokens() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("  "));
    }
}
