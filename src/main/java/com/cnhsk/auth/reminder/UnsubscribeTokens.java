package com.cnhsk.auth.reminder;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Purpose-bound email opt-out tokens; the payload never contains an email address. */
@Component
public class UnsubscribeTokens {

    private static final String PURPOSE = "REMINDER_UNSUBSCRIBE";
    private static final Duration LIFETIME = Duration.ofDays(30);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final SecretKeySpec signingKey;
    private final Clock clock;

    public UnsubscribeTokens(CnhskProperties properties, Clock clock) {
        this.signingKey = new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        this.clock = clock;
    }

    public String issue(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive.");
        }
        String payload = userId + "." + clock.instant().plus(LIFETIME).getEpochSecond() + "." + PURPOSE;
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + ENCODER.encodeToString(sign(encoded));
    }

    public long verify(String token) {
        if (token == null || token.isBlank() || token.length() > 512) {
            throw invalid();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]+")
                || !parts[1].matches("[A-Za-z0-9_-]{43}")) {
            throw invalid();
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), signature)
                    || !ENCODER.encodeToString(signature).equals(parts[1])) {
                throw invalid();
            }
            // Authenticate the opaque encoded payload before decoding or trusting any of its fields.
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            if (!ENCODER.encodeToString(decoded).equals(parts[0])) {
                throw invalid();
            }
            String[] fields = new String(decoded, StandardCharsets.UTF_8).split("\\.", -1);
            if (fields.length != 3 || !fields[0].matches("[1-9][0-9]*")
                    || !fields[1].matches("[1-9][0-9]*") || !PURPOSE.equals(fields[2])) {
                throw invalid();
            }
            long userId = Long.parseLong(fields[0]);
            long expiresAt = Long.parseLong(fields[1]);
            if (expiresAt <= clock.instant().getEpochSecond()) {
                throw invalid();
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String encoded) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize unsubscribe signing.", exception);
        }
    }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_UNSUBSCRIBE_TOKEN, HttpStatus.BAD_REQUEST,
                "Liên kết tắt nhắc không hợp lệ hoặc đã hết hạn.", Map.of());
    }
}
