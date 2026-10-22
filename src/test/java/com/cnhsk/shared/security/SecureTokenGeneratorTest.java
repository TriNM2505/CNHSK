package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SecureTokenGeneratorTest {

    private static final Pattern BASE64_URL_WITHOUT_PADDING = Pattern.compile("[A-Za-z0-9_-]{43}");

    @Test
    void generatesDistinct32ByteBase64UrlTokensWithoutPadding() {
        SecureTokenGenerator generator = new SecureTokenGenerator();
        Set<String> tokens = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            String token = generator.generate();
            assertTrue(BASE64_URL_WITHOUT_PADDING.matcher(token).matches());
            assertFalse(token.contains("="));
            tokens.add(token);
        }

        assertTrue(tokens.size() > 1);
    }
}
