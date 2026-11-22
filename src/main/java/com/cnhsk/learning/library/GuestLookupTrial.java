package com.cnhsk.learning.library;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.shared.security.TokenHasher;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Daily dictionary trial of one guest browser. Each distinct lookup counts once, so paging, reloading or
 * repeating a lookup is free. Only hashes of the lookups are stored.
 */
@Component
public class GuestLookupTrial {

    public static final String COOKIE = "guest_trial";

    private static final Logger log = LoggerFactory.getLogger(GuestLookupTrial.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern GUEST_ID = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final TokenHasher HASHER = new TokenHasher();
    private static final DefaultRedisScript<Long> USE = new DefaultRedisScript<>(
            "local used = redis.call('SCARD', KEYS[1]) "
                    + "if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return used end "
                    + "if used >= tonumber(ARGV[2]) then return -1 end "
                    + "redis.call('SADD', KEYS[1], ARGV[1]) "
                    + "if used == 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end "
                    + "return used + 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final LibraryConfig config;
    private final SecureTokenGenerator tokens;
    private final CnhskProperties.Cookie cookies;

    public GuestLookupTrial(StringRedisTemplate redis, Clock clock, LibraryConfig config,
            SecureTokenGenerator tokens, CnhskProperties properties) {
        this.redis = redis;
        this.clock = clock;
        this.config = config;
        this.tokens = tokens;
        this.cookies = properties.getCookie();
    }

    public static boolean isGuestId(String value) {
        return value != null && GUEST_ID.matcher(value).matches();
    }

    public String newGuestId() {
        return tokens.generate();
    }

    public ResponseCookie cookie(String guestId) {
        return ResponseCookie.from(COOKIE, guestId)
                .domain(cookies.getDomain())
                .httpOnly(true)
                .secure(cookies.isSecure())
                .sameSite(cookies.getSameSite())
                .path("/api/public/dictionary")
                .maxAge(Duration.ofDays(365))
                .build();
    }

    /** Empty when the counter cannot be read. */
    public Optional<Status> status(String guestId) {
        int limit = config.getGuestTrialLookupsPerDay();
        if (!isGuestId(guestId)) return Optional.of(new Status(limit, limit));
        try {
            Long used = redis.opsForSet().size(key(guestId));
            return Optional.of(new Status(limit, (int) Math.max(0, limit - (used == null ? 0 : used))));
        } catch (RuntimeException exception) {
            warnUnavailable();
            return Optional.empty();
        }
    }

    /** Counts a new lookup, or rejects it when today's trial is used up. */
    public void use(String guestId, String type, String... terms) {
        int limit = config.getGuestTrialLookupsPerDay();
        long secondsLeft = secondsUntilMidnight();
        Long used;
        try {
            used = redis.execute(USE, List.of(key(guestId)), fingerprint(type, terms),
                    Integer.toString(limit), Long.toString(secondsLeft));
        } catch (RuntimeException exception) {
            warnUnavailable();
            return;
        }
        if (used != null && used < 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã dùng hết " + limit + " lượt tra thử hôm nay. Vui lòng đăng nhập để tra tiếp.",
                    Map.of("retryAfterSeconds", secondsLeft, "loginSuggested", true, "guestTrialExhausted", true));
        }
    }

    private String key(String guestId) {
        return "library:guest-trial:" + guestId + ":"
                + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(clock.withZone(VIETNAM)));
    }

    private long secondsUntilMidnight() {
        var now = clock.instant();
        var midnight = LocalDate.now(clock.withZone(VIETNAM)).plusDays(1).atStartOfDay(VIETNAM).toInstant();
        return Math.max(1, (Duration.between(now, midnight).toMillis() + 999) / 1000);
    }

    private static String fingerprint(String type, String... terms) {
        StringBuilder value = new StringBuilder(type);
        for (String term : terms) {
            value.append('\n').append(term == null ? ""
                    : Normalizer.normalize(term, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT));
        }
        return HASHER.hash(value.toString());
    }

    private static void warnUnavailable() {
        // Redis errors may contain connection details; never log them or the guest's identity.
        log.warn("Guest dictionary trial unavailable; allowing request");
    }

    @Schema(name = "GuestTrial", requiredProperties = {"limit", "remaining"})
    public record Status(int limit, int remaining) {
    }
}
