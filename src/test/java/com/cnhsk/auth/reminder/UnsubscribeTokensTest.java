package com.cnhsk.auth.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class UnsubscribeTokensTest {

    private static final Instant NOW = Instant.parse("2031-11-15T13:00:00Z");
    private final CnhskProperties properties = new CnhskProperties();
    private final Clock clock = mock(Clock.class);

    private UnsubscribeTokens tokens() {
        properties.getJwt().setSecret(UUID.randomUUID().toString() + UUID.randomUUID());
        when(clock.instant()).thenReturn(NOW);
        return new UnsubscribeTokens(properties, clock);
    }

    @Test
    void FR016_issueUsesTheExactWireContractAndThirtyDayClockExpiry() throws Exception {
        UnsubscribeTokens tokens = tokens();
        String issued = tokens.issue(42);
        String[] parts = issued.split("\\.");

        assertThat(parts).hasSize(2);
        assertThat(issued).matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}").doesNotContain("=", "@");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(payload).isEqualTo("42." + NOW.plus(Duration.ofDays(30)).getEpochSecond()
                + ".REMINDER_UNSUBSCRIBE");
        assertThat(issued).isEqualTo(signed(payload));
        assertThat(tokens.verify(issued)).isEqualTo(42);
        assertThat(tokens.verify(tokens.issue(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void FR016_AT16_expiryIsExclusiveAndUsesTheInjectedClock() {
        UnsubscribeTokens tokens = tokens();
        String issued = tokens.issue(42);
        Instant expiry = NOW.plus(Duration.ofDays(30));
        when(clock.instant()).thenReturn(expiry.minusMillis(1));
        assertThat(tokens.verify(issued)).isEqualTo(42);
        when(clock.instant()).thenReturn(expiry);
        assertInvalid(tokens, issued);
        when(clock.instant()).thenReturn(expiry.plusSeconds(1));
        assertInvalid(tokens, issued);
    }

    @Test
    void FR016_AT16_rejectsTamperingAndADifferentSecretBeforeTrustingPayload() {
        UnsubscribeTokens tokens = tokens();
        String issued = tokens.issue(42);
        Clock untrustedClock = mock(Clock.class);
        UnsubscribeTokens verifier = new UnsubscribeTokens(properties, untrustedClock);
        int separator = issued.indexOf('.');
        String changedSignature = issued.substring(0, separator + 1)
                + (issued.charAt(separator + 1) == 'A' ? 'B' : 'A') + issued.substring(separator + 2);
        assertInvalid(verifier, changedSignature);
        assertInvalid(verifier, (issued.charAt(0) == 'A' ? "B" : "A") + issued.substring(1));
        verifyNoInteractions(untrustedClock);

        properties.getJwt().setSecret(UUID.randomUUID().toString() + UUID.randomUUID());
        assertInvalid(new UnsubscribeTokens(properties, clock), issued);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "9223372036854775808", "01", "+1", "user"})
    void FR016_rejectsNonPositiveNonCanonicalOrOverflowingUserIds(String id) throws Exception {
        UnsubscribeTokens tokens = tokens();
        assertInvalid(tokens, signed(id + ".2000000000.REMINDER_UNSUBSCRIBE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "42.2000000000.RESET_PASSWORD", "42.2000000000.reminder_unsubscribe",
            "42.2000000000.REMINDER_UNSUBSCRIBE.extra", "42.2000000000", "42..REMINDER_UNSUBSCRIBE",
            "42.0.REMINDER_UNSUBSCRIBE", "42.-1.REMINDER_UNSUBSCRIBE",
            "42.9223372036854775808.REMINDER_UNSUBSCRIBE", "42.exp.REMINDER_UNSUBSCRIBE"})
    void FR016_AT16_rejectsCorrectlySignedPayloadsWithInvalidFields(String payload) throws Exception {
        UnsubscribeTokens tokens = tokens();
        assertInvalid(tokens, signed(payload));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", "a.b.c", ".", "a.", ".a", "a=.b", "a/b.c", "a+b.c"})
    void FR016_rejectsMalformedWireValues(String value) {
        assertInvalid(tokens(), value);
    }

    @Test
    void FR016_rejectsOversizedPaddedAndNonCanonicalBase64() {
        UnsubscribeTokens tokens = tokens();
        assertInvalid(tokens, "a".repeat(513));
        String issued = tokens.issue(42);
        assertInvalid(tokens, issued + "=");
        // A 32-byte signature has two unused low bits in its final base64url character.
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int last = alphabet.indexOf(issued.charAt(issued.length() - 1));
        assertInvalid(tokens, issued.substring(0, issued.length() - 1) + alphabet.charAt(last + 1));
        assertThatThrownBy(() -> tokens.issue(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private String signed(String payload) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encoded = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return encoded + "." + encoder.encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII)));
    }

    private void assertInvalid(UnsubscribeTokens tokens, String token) {
        assertThatThrownBy(() -> tokens.verify(token)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_UNSUBSCRIBE_TOKEN);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.getMessage()).isEqualTo("Liên kết tắt nhắc không hợp lệ hoặc đã hết hạn.");
            assertThat(error.getExtra()).isEmpty();
        });
    }
}
