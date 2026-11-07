package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CardCodesTest {

    private static final String TEST_PEPPER = "test-only-billing-pepper-32-bytes-minimum";
    private static final String CANONICAL = "ABCDEFGHJKMNPQRS";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-06T17:30:00Z"), ZoneOffset.UTC);
    private final CardCodes codes = codes(TEST_PEPPER, FIXED_CLOCK);

    @Test
    void generatesTenThousandUniqueCanonicalCodes() {
        var unique = new HashSet<String>();
        for (int index = 0; index < 10_000; index++) {
            String generated = codes.generate();
            assertThat(generated != null && generated.length() == 16).isTrue();
            assertThat(generated.matches("[0-9A-HJKMNP-TV-Z]{16}")).isTrue();
            unique.add(generated);
        }
        assertThat(unique.size()).isEqualTo(10_000);
    }

    @Test
    void formatsCanonicalCodesAndRoundTripsGeneratedCodes() {
        assertThat(codes.format(CANONICAL).equals("ABCD-EFGH-JKMN-PQRS")).isTrue();
        for (int index = 0; index < 100; index++) {
            String generated = codes.generate();
            String formatted = codes.format(generated);
            assertThat(formatted.matches("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}")).isTrue();
            assertThat(codes.normalize(formatted).equals(Optional.of(generated))).isTrue();
        }
    }

    @Test
    void normalizesAsciiWhitespaceAndAmbiguousLetters() {
        assertThat(codes.normalize(" \tabcd-efgh\r\njkmn-pqrs ").equals(Optional.of(CANONICAL))).isTrue();
        assertThat(codes.normalize("abcd efgh-ijkl-mnop").equals(Optional.of("ABCDEFGH1JK1MN0P"))).isTrue();
        assertThat(codes.normalize("oOiIlL0123456789").equals(Optional.of("0011110123456789"))).isTrue();
        assertThat(codes.normalize("0123456789abcdef").equals(Optional.of("0123456789ABCDEF"))).isTrue();
    }

    @Test
    void rejectsInvalidInputsAndAllNonAsciiBeforeCaseConversion() {
        assertThat(codes.normalize(null).isEmpty()).isTrue();
        for (String invalid : List.of("", " ", "A".repeat(15), "A".repeat(17), "U".repeat(16),
                "A".repeat(15) + "!", "A".repeat(15) + "\u0131", "A".repeat(14) + "\u00df",
                "ABCD\u00a0EFGHJKMNPQRS", "ABCD\u2003EFGHJKMNPQRS", "ABCD\u200bEFGHJKMNPQRS",
                "ABCD\u2013EFGHJKMNPQRS", "\uff21BCDEFGHJKMNPQRS", "A".repeat(15) + "\ud83d\ude00",
                "ABCD\u000bEFGHJKMNPQRS", "ABCD\fEFGHJKMNPQRS", "ABCD\u0000EFGHJKMNPQRS")) {
            assertThat(codes.normalize(invalid).isEmpty()).isTrue();
        }
    }

    @Test
    void normalizationDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(codes.normalize("iiii-llll-oooo-abcd").equals(Optional.of("111111110000ABCD"))).isTrue();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void strictApisRejectInvalidInputsWithoutEchoingThem() {
        List<String> invalid = new ArrayList<>(List.of("", "A".repeat(15), "A".repeat(17),
                "abcdefghijklmnop", "ABCD-EFGH-JKMN-PQRS", " ABCDEFGHJKMNPQRS", "U".repeat(16),
                "O".repeat(16), "I".repeat(16), "L".repeat(16), "A".repeat(15) + "\u0131"));
        invalid.add(null);
        for (String input : invalid) {
            assertInvalid(() -> codes.format(input));
            assertInvalid(() -> codes.hash(input));
        }
    }

    @Test
    void hashMatchesIndependentHmacVectorAndChangesWithPepper() throws Exception {
        String expected = independentHmac(TEST_PEPPER, CANONICAL);
        String actual = codes.hash(CANONICAL);
        assertThat(actual.matches("[0-9a-f]{64}")).isTrue();
        assertThat(actual.equals(expected)).isTrue();
        assertThat(codes.hash(CANONICAL).equals(actual)).isTrue();
        assertThat(codes("other-test-only-pepper-32-bytes-minimum", FIXED_CLOCK)
                .hash(CANONICAL).equals(actual)).isFalse();
        assertThat(codes.hash("0123456789ABCDEF").equals(actual)).isFalse();
    }

    @Test
    void hashUsesLiteralUtf8PepperBytesWithoutTrimmingOrBase64Decoding() throws Exception {
        for (String pepper : List.of("\u00e1".repeat(16), " dGVzdC1vbmx5LXBlcHBlci1ub3QtYS1yZWFsLXNlY3JldA== ",
                "dGVzdC1vbmx5LXBlcHBlci1ub3QtYS1yZWFsLXNlY3JldA==")) {
            String actual = codes(pepper, FIXED_CLOCK).hash(CANONICAL);
            assertThat(actual.equals(independentHmac(pepper, CANONICAL))).isTrue();
            assertThat(actual.matches("[0-9a-f]{64}")).isTrue();
        }
        String spaced = " " + TEST_PEPPER + " ";
        assertThat(codes(spaced, FIXED_CLOCK).hash(CANONICAL).equals(codes.hash(CANONICAL))).isFalse();
    }

    @Test
    void hashIsConsistentAcrossThreads() throws Exception {
        List<String> inputs = List.of(CANONICAL, "0123456789ABCDEF", "ZYXWVTSRQPNMKJHG", "0000000000000000");
        List<String> expected = new ArrayList<>();
        for (String input : inputs) {
            expected.add(independentHmac(TEST_PEPPER, input));
        }
        var executor = Executors.newFixedThreadPool(12);
        var start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 12; worker++) {
                final int offset = worker;
                tasks.add(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return false;
                    }
                    for (int index = 0; index < 500; index++) {
                        int fixture = (offset + index) % inputs.size();
                        if (!codes.hash(inputs.get(fixture)).equals(expected.get(fixture))) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void batchIdUsesVietnamDateFromInjectedClockAtMidnightBoundaries() {
        assertThat(codes.newBatchId().matches("^B20261107-[0-9A-HJKMNP-TV-Z]{6}$")).isTrue();
        for (String zone : List.of("UTC", "America/Los_Angeles", "Asia/Tokyo")) {
            CardCodes before = codes(TEST_PEPPER, Clock.fixed(Instant.parse("2026-11-06T16:59:59.999999999Z"),
                    ZoneId.of(zone)));
            CardCodes after = codes(TEST_PEPPER, Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"),
                    ZoneId.of(zone)));
            assertThat(before.newBatchId().matches("^B20261106-[0-9A-HJKMNP-TV-Z]{6}$")).isTrue();
            assertThat(after.newBatchId().matches("^B20261107-[0-9A-HJKMNP-TV-Z]{6}$")).isTrue();
        }
    }

    @Test
    void operationsDoNotLogCodesHashesOrPepper(CapturedOutput output) {
        String generated = codes.generate();
        String formatted = codes.format(generated);
        String hash = codes.hash(generated);
        codes.normalize(formatted);
        codes.newBatchId();
        String captured = output.getAll();
        assertThat(captured.contains(generated)).isFalse();
        assertThat(captured.contains(formatted)).isFalse();
        assertThat(captured.contains(hash)).isFalse();
        assertThat(captured.contains(TEST_PEPPER)).isFalse();
    }

    private static CardCodes codes(String pepper, Clock clock) {
        BillingConfig config = new BillingConfig();
        config.setCardCodePepper(pepper);
        return new CardCodes(config, clock);
    }

    private static String independentHmac(String pepper, String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, action);
        assertThat("Invalid card code".equals(failure.getMessage())).isTrue();
        assertThat(failure.getCause() == null).isTrue();
    }
}
