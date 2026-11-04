package com.cnhsk.learning.library;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;

/** Query-only numbered Pinyin adapter; imported dictionary values retain their tone marks. */
public record PinyinQuery(String normalized, String preferredPinyin) {

    private static final Pattern NUMBERED_SYLLABLE = Pattern.compile("([a-züv]+)([1-5])");
    private static final String MARKED_VOWELS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ";
    private static final String BASE_VOWELS = "aeiouü";

    public static PinyinQuery parse(String value) {
        String query = TextNormalizer.normalizeCell("pinyin", value).toLowerCase(Locale.ROOT);
        if (query.chars().anyMatch(Character::isDigit)) {
            String converted = numberedToMarked(query);
            if (converted == null) return new PinyinQuery(PinyinUtils.normalize(query), null);
            return new PinyinQuery(PinyinUtils.normalize(converted), compact(converted));
        }
        String preferred = null;
        if (query.chars().anyMatch(letter -> MARKED_VOWELS.indexOf(letter) >= 0)) {
            try {
                PinyinUtils.toNumbered(query);
                preferred = compact(query);
            } catch (IllegalArgumentException exception) {
                // An ambiguous spelling can still match the untoned index; do not invent a tone preference.
            }
        }
        return new PinyinQuery(PinyinUtils.normalize(query), preferred);
    }

    private static String numberedToMarked(String value) {
        Matcher matcher = NUMBERED_SYLLABLE.matcher(value);
        StringBuilder result = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            if (!separators(value.substring(end, matcher.start()))) return null;
            String base = matcher.group(1).replace('v', 'ü');
            try {
                if (PinyinUtils.toNumbered(base).contains(" ")) return null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
            String marked = addTone(base, matcher.group(2).charAt(0) - '0');
            if (marked == null) return null;
            if (!result.isEmpty()) result.append(' ');
            result.append(marked);
            end = matcher.end();
        }
        return end == 0 || !separators(value.substring(end)) ? null : result.toString();
    }

    private static String addTone(String base, int tone) {
        if (tone == 5) return base;
        int vowel = base.indexOf('a');
        if (vowel < 0) vowel = base.indexOf('e');
        if (vowel < 0) vowel = base.indexOf("ou");
        if (vowel < 0) {
            for (int index = base.length() - 1; index >= 0; index--) {
                if (BASE_VOWELS.indexOf(base.charAt(index)) >= 0) {
                    vowel = index;
                    break;
                }
            }
        }
        if (vowel < 0) return null;
        char marked = MARKED_VOWELS.charAt(BASE_VOWELS.indexOf(base.charAt(vowel)) * 4 + tone - 1);
        return base.substring(0, vowel) + marked + base.substring(vowel + 1);
    }

    private static boolean separators(String value) {
        return value.chars().allMatch(letter -> letter == ' ' || letter == '\'' || letter == '-');
    }

    private static String compact(String value) {
        return value.replace(" ", "").replace("'", "").replace("-", "");
    }
}
