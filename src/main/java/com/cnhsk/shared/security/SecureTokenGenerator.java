package com.cnhsk.shared.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import org.springframework.stereotype.Component;

/** Generates cryptographically random 32-byte tokens encoded as unpadded Base64URL. */
@Component
public final class SecureTokenGenerator {

    public static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom;

    public SecureTokenGenerator() {
        this(new SecureRandom());
    }

    SecureTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null.");
    }

    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
