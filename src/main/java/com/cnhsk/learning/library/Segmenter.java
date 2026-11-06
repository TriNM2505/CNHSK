package com.cnhsk.learning.library;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Segmenter {
    private static final int MAX_WORD_LENGTH = 8;
    private static final Set<Character.UnicodeBlock> CJK_BLOCKS = Set.of(
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT);
    private final SegmenterQueryRepository dictionary;

    public Segmenter(SegmenterQueryRepository dictionary) { this.dictionary = dictionary; }

    @Transactional(readOnly = true)
    public Result segment(String text) {
        Set<String> texts = candidates(text);
        if (texts.isEmpty()) return split(text, Map.of());
        Map<String, Lexeme> lookup;
        try { lookup = dictionary.lookup(texts); }
        catch (RuntimeException exception) {
            // JDBC messages and causes can contain bound input; keep this boundary free of that data.
            throw new SegmentationException();
        }
        return split(text, lookup);
    }

    /** Pure longest-prefix matching; original text and the supplied lookup are never modified. */
    public static Result split(String text, Map<String, Lexeme> lookup) {
        Input input = input(text);
        if (lookup == null) throw new IllegalArgumentException("Bảng tra từ không được null.");
        List<Segment> segments = new ArrayList<>();
        StringJoiner pinyin = new StringJoiner(" ");
        int index = 0;
        while (index < input.points().length) {
            if (!isCjk(input.points()[index])) {
                int end = index + 1;
                while (end < input.points().length && !isCjk(input.points()[end])) end++;
                segments.add(new Segment(text.substring(input.offsets()[index], input.offsets()[end]), null, null));
                index = end;
                continue;
            }
            int end = index;
            while (end < input.points().length && end - index < MAX_WORD_LENGTH && isCjk(input.points()[end])) end++;
            Lexeme match = null;
            String matchedText = null;
            for (; end > index; end--) {
                String candidate = text.substring(input.offsets()[index], input.offsets()[end]);
                match = lookup.get(candidate);
                if (match != null) { matchedText = candidate; break; }
            }
            if (match == null) {
                segments.add(new Segment(text.substring(input.offsets()[index], input.offsets()[index + 1]), null, null));
                index++;
            } else {
                String partPinyin = pinyin(match.pinyin());
                segments.add(new Segment(matchedText, partPinyin, match.id()));
                if (partPinyin != null) pinyin.add(partPinyin);
                index = end;
            }
        }
        String joined = pinyin.toString();
        return new Result(segments, joined.isEmpty() ? null : joined);
    }

    static Set<String> candidates(String text) {
        Input input = input(text);
        Set<String> result = new LinkedHashSet<>();
        for (int start = 0; start < input.points().length; start++) {
            for (int end = start; end < input.points().length && end - start < MAX_WORD_LENGTH; end++) {
                if (!isCjk(input.points()[end])) break;
                result.add(text.substring(input.offsets()[start], input.offsets()[end + 1]));
            }
        }
        return result;
    }

    private static Input input(String text) {
        if (text == null) throw new IllegalArgumentException("Đoạn cần tách không được null.");
        int[] points = text.codePoints().toArray();
        int[] offsets = new int[points.length + 1];
        for (int index = 0; index < points.length; index++) offsets[index + 1] = offsets[index] + Character.charCount(points[index]);
        return new Input(points, offsets);
    }

    private static boolean isCjk(int point) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(point);
        return block != null && CJK_BLOCKS.contains(block) && Character.isIdeographic(point);
    }

    private static String pinyin(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end && whitespace(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && whitespace(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return start == end ? null : value.substring(start, end);
    }

    private static boolean whitespace(int point) { return Character.isWhitespace(point) || Character.isSpaceChar(point); }

    private record Input(int[] points, int[] offsets) { }
    public record Lexeme(long id, String pinyin) { }
    public record Segment(String text, String pinyin, Long lexemeId) { }
    public record Result(List<Segment> segments, String pinyin) {
        public Result { segments = List.copyOf(segments); }
    }

    public static final class SegmentationException extends RuntimeException {
        private SegmentationException() { super("Không thể tách đoạn bằng từ điển."); }
    }
}
