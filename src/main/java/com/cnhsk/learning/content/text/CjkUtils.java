package com.cnhsk.learning.content.text;

public final class CjkUtils {

    private CjkUtils() {
    }

    public static boolean isCjk(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    public static boolean isCjk(String value) {
        return value != null && !value.isEmpty() && value.codePoints().allMatch(CjkUtils::isCjk);
    }

    public static boolean isBroken(String value, boolean requiresCjk) {
        return value != null && (value.indexOf('\uFFFD') >= 0 || (requiresCjk && value.indexOf('?') >= 0));
    }
}
