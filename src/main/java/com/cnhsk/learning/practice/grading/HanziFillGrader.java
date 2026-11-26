package com.cnhsk.learning.practice.grading;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Matches a typed answer against the accepted forms of a fill-in question: spacing, full-width letters and
 * trailing punctuation do not count, characters do.
 */
public final class HanziFillGrader {
    private static final Pattern IGNORED = Pattern.compile("[\\s\\p{Z}。．.，,！!？?、；;：:\"'“”‘’]+");

    private HanziFillGrader() {
    }

    public static boolean matches(String answer, List<String> accept) {
        if (answer == null || accept == null) {
            return false;
        }
        String typed = normalize(answer);
        return !typed.isEmpty() && accept.stream().map(HanziFillGrader::normalize).anyMatch(typed::equals);
    }

    static String normalize(String value) {
        return IGNORED.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
