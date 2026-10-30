package com.cnhsk.learning.practice.grading;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import com.cnhsk.learning.content.text.PinyinUtils;

/** Spec 003 §0.7: preserve required tones, ignoring only neutral tone 5. */
public final class PinyinGrader {

    private static final String TONED_VOWELS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ";

    private PinyinGrader() {
    }

    public static boolean matches(String answer, List<String> accept) {
        if (answer == null || accept == null || accept.isEmpty()) {
            return false;
        }
        try {
            String normalized = normalize(answer);
            return !normalized.isEmpty() && accept.stream().map(PinyinGrader::normalize).anyMatch(normalized::equals);
        } catch (IllegalArgumentException exception) {
            // Unrecognizable learner input is an incorrect answer, never a server error.
            return false;
        }
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("u:", "v").replace('ü', 'v');
        if (normalized.chars().anyMatch(letter -> TONED_VOWELS.indexOf(letter) >= 0)) {
            normalized = PinyinUtils.toNumbered(normalized);
        }
        return normalized.replaceAll("[\\s\\p{Z}'-]", "").replace("5", "");
    }
}
