package com.cnhsk.learning.grading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Pure essay rules; essay text is never included in an exception or diagnostic string. */
public final class EssayRules {
    private EssayRules() { }

    public static Essay validate(String raw, GradingConfig config) {
        if (config == null) throw new IllegalArgumentException("Grading configuration is required.");
        String essay = trim(raw);
        int hanzi = countHanzi(essay);
        if (essay.codePointCount(0, essay.length()) > config.getMaxChars()
                || hanzi < config.getMinHanzi() || hanzi > config.getMaxHanzi()) {
            throw new ApiException(ErrorCode.GRADING_INVALID_ESSAY, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Bài viết không đúng độ dài quy định.", Map.of("details", Map.of("hanziCount", hanzi)));
        }
        return new Essay(essay, hanzi, essayHash(essay));
    }

    public static int countHanzi(String essay) {
        return essay == null ? 0 : (int) essay.codePoints().filter(code ->
                Character.UnicodeBlock.of(code) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS).count();
    }

    public static String essayHash(String raw) {
        String essay = trim(raw);
        StringBuilder normalized = new StringBuilder(essay.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < essay.length();) {
            int code = essay.codePointAt(offset);
            offset += Character.charCount(code);
            if (whitespace(code)) {
                pendingSpace = true;
            } else {
                if (pendingSpace && !normalized.isEmpty()) normalized.append(' ');
                normalized.appendCodePoint(code);
                pendingSpace = false;
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private static String trim(String raw) {
        if (raw == null) return "";
        int start = 0;
        int end = raw.length();
        while (start < end && whitespace(raw.codePointAt(start))) start += Character.charCount(raw.codePointAt(start));
        while (start < end && whitespace(raw.codePointBefore(end))) end -= Character.charCount(raw.codePointBefore(end));
        return raw.substring(start, end);
    }

    private static boolean whitespace(int code) {
        return Character.isWhitespace(code) || Character.isSpaceChar(code);
    }

    public record Essay(String essay, int hanziCount, String essayHash) {
        @Override
        public String toString() { return "Essay[hanziCount=" + hanziCount + "]"; }
    }
}
