package com.cnhsk.learning.practice.grading;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Grades a typed Sino-Vietnamese reading. Learners may type with or without tone marks, so both sides
 * are compared without diacritics; any of the character's readings is accepted.
 */
public final class SinoVietGrader {

    private SinoVietGrader() {
    }

    public static boolean matches(String answer, List<String> accept) {
        if (answer == null || accept == null || accept.isEmpty()) return false;
        String typed = plain(answer);
        return !typed.isEmpty() && accept.stream().map(SinoVietGrader::plain).anyMatch(typed::equals);
    }

    private static String plain(String value) {
        String lower = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        String withoutMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutMarks.replaceAll("[\\s\\p{Z}]+", " ").strip();
    }
}
