package com.cnhsk.learning.credit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public final class CardCodes {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter
            .ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final SecretKeySpec pepperKey;
    private final Clock clock;

    public CardCodes(BillingConfig config, Clock clock) {
        if (config == null || !config.isCardCodePepperValid()) {
            throw new IllegalArgumentException("cnhsk.billing.card-code-pepper >= 32 byte");
        }
        this.pepperKey = new SecretKeySpec(config.getCardCodePepper().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public String generate() {
        return randomCharacters(16);
    }

    public String format(String raw16) {
        requireCanonical(raw16);
        return raw16.substring(0, 4) + "-" + raw16.substring(4, 8) + "-"
                + raw16.substring(8, 12) + "-" + raw16.substring(12, 16);
    }

    public Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        StringBuilder compact = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (character > 0x7f) {
                return Optional.empty();
            }
            if (character != ' ' && character != '\t' && character != '\r'
                    && character != '\n' && character != '-') {
                compact.append(character);
            }
        }
        String normalized = compact.toString().toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('I', '1').replace('L', '1');
        return isCanonical(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    /**
     * Computes a keyed digest; caller must pass the result of normalize().
     */
    public String hash(String normalized) {
        requireCanonical(normalized);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepperKey);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Card code hashing is unavailable");
        }
    }

    public String newBatchId() {
        return "B" + BATCH_DATE.format(clock.instant()) + "-" + randomCharacters(6);
    }

    private static String randomCharacters(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private static void requireCanonical(String code) {
        if (!isCanonical(code)) {
            throw new IllegalArgumentException("Invalid card code");
        }
    }

    private static boolean isCanonical(String code) {
        if (code == null || code.length() != 16) {
            return false;
        }
        for (int index = 0; index < code.length(); index++) {
            if (ALPHABET.indexOf(code.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }
}
