package com.cnhsk.learning.knowledge;

/**
 * Pictures of mock-paper questions may be emoji instead of uploaded images: a text made only of emoji stands for
 * the picture of a word. Ranges cover pictographs, symbols and their joiners, variation selectors and skin tones.
 */
public final class EmojiText {
    private EmojiText() {
    }

    public static boolean isEmojiOnly(String text) {
        if (text == null || text.isBlank()) return false;
        boolean pictograph = false;
        for (int point : text.strip().codePoints().toArray()) {
            if (isPictograph(point)) pictograph = true;
            else if (!isJoiner(point) && !Character.isWhitespace(point)) return false;
        }
        return pictograph;
    }

    /**
     * Length (in chars) of the emoji picture that opens {@code line}, when it is followed by a space or ends the
     * line; 0 when the line does not open with one. Lets a one-line stem such as "🐱 猫" carry its picture.
     */
    public static int leadingPictureLength(String line) {
        int offset = 0;
        boolean pictograph = false;
        while (offset < line.length()) {
            int point = line.codePointAt(offset);
            if (isPictograph(point)) pictograph = true;
            else if (!isJoiner(point)) break;
            offset += Character.charCount(point);
        }
        if (!pictograph) return 0;
        return offset == line.length() || Character.isWhitespace(line.codePointAt(offset)) ? offset : 0;
    }

    private static boolean isPictograph(int point) {
        // ★ ☆ mark the statement of a question, never a picture.
        if (point == 0x2605 || point == 0x2606) return false;
        return (point >= 0x1F000 && point <= 0x1FAFF) || (point >= 0x2600 && point <= 0x27BF)
                || (point >= 0x2300 && point <= 0x23FF) || (point >= 0x2B00 && point <= 0x2BFF);
    }

    private static boolean isJoiner(int point) {
        return point == 0x200D || point == 0xFE0F || point == 0x20E3 || (point >= 0x1F3FB && point <= 0x1F3FF);
    }
}
