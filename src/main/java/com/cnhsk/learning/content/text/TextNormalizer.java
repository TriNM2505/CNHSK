package com.cnhsk.learning.content.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("[\\u0300-\\u036F]");

    private TextNormalizer() {
    }

    public static String normalizeCell(String columnName, String value) {
        if (value == null) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        int start = 0;
        int end = normalized.length();
        while (start < end && isWhitespace(normalized.codePointAt(start))) {
            start += Character.charCount(normalized.codePointAt(start));
        }
        while (end > start && isWhitespace(normalized.codePointBefore(end))) {
            end -= Character.charCount(normalized.codePointBefore(end));
        }

        String trimmed = normalized.substring(start, end);
        if (columnName != null && (columnName.endsWith("_vi") || columnName.startsWith("explanation"))) {
            return trimmed;
        }

        StringBuilder result = new StringBuilder(trimmed.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isWhitespace(codePoint)) {
                pendingSpace = true;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }

    public static String normalizeMeaning(String meaningVi) {
        String normalized = normalizeCell("meaning_vi", meaningVi);
        if (normalized == null) {
            return null;
        }

        String decomposed = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
