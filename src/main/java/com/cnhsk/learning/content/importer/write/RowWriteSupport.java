package com.cnhsk.learning.content.importer.write;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.text.TextNormalizer;

final class RowWriteSupport {
    private RowWriteSupport() { }

    static boolean present(ValidatedRow row, String column) {
        String value = row.values().get(column);
        return value != null && !value.isBlank();
    }

    static String value(ValidatedRow row, String column, String previous) {
        return present(row, column) ? row.values().get(column) : previous;
    }

    static List<String> tokens(ValidatedRow row, String column, List<String> previous) {
        return present(row, column) ? Arrays.asList(row.values().get(column).split("\\|")) : previous;
    }

    static LexemeExtra extra(LexemeExtra previous) {
        return previous == null ? LexemeExtra.empty() : previous;
    }

    static List<LexemeExtra.Example> examples(ValidatedRow row, List<LexemeExtra.Example> previous, boolean grammar) {
        List<LexemeExtra.Example> merged = new ArrayList<>(previous == null ? List.of() : previous);
        for (int index = 0; index < (grammar ? 3 : 1); index++) {
            String prefix = "example" + (grammar ? index + 1 : "");
            if (!present(row, prefix + "_cn") && !present(row, prefix + "_pinyin") && !present(row, prefix + "_vi")) {
                continue;
            }
            // Keep positional examples: example3 must remain at index 2 even when example2 is absent.
            while (merged.size() <= index) {
                merged.add(new LexemeExtra.Example(null, null, null));
            }
            LexemeExtra.Example old = merged.get(index);
            merged.set(index, new LexemeExtra.Example(value(row, prefix + "_cn", old.cn()),
                    value(row, prefix + "_pinyin", old.pinyin()), value(row, prefix + "_vi", old.vi())));
        }
        return previous == null && merged.isEmpty() ? null : List.copyOf(merged);
    }

    static <T> List<T> options(ValidatedRow row, List<T> previous, Function<T, String> key,
            BiFunction<String, String, T> option) {
        List<T> merged = new ArrayList<>(previous == null ? List.of() : previous);
        for (String letter : List.of("A", "B", "C", "D")) {
            String column = "option_" + letter.toLowerCase(Locale.ROOT);
            if (!present(row, column)) {
                continue;
            }
            T replacement = option.apply(letter, row.values().get(column));
            int index = -1;
            for (int current = 0; current < merged.size(); current++) {
                if (letter.equals(key.apply(merged.get(current)))) {
                    index = current;
                    break;
                }
            }
            if (index < 0) {
                merged.add(replacement);
            } else {
                merged.set(index, replacement);
            }
        }
        return previous == null && merged.isEmpty() ? null : List.copyOf(merged);
    }

    static <T> void validateAnswer(List<T> options, String answer, Function<T, String> key,
            Function<T, String> text) {
        if (options == null || answer == null || !Set.of("A", "B", "C", "D").contains(answer)) {
            throw new InvalidAnswerException();
        }
        Set<String> distinct = new HashSet<>();
        boolean answerPresent = false;
        for (T option : options) {
            String value = TextNormalizer.normalizeCell("option", text.apply(option));
            if (value == null || value.isEmpty()) continue;
            if (!distinct.add(value)) throw new InvalidAnswerException();
            if (answer.equals(key.apply(option))) answerPresent = true;
        }
        if (!answerPresent) throw new InvalidAnswerException();
    }

    /** Only actual imported-value changes count; optional blank cells never invoke a setter. */
    static final class Changes {
        private boolean changed;

        boolean changed() {
            return changed;
        }

        <T> void set(T previous, T next, Consumer<T> setter) {
            if (!Objects.equals(previous, next)) {
                setter.accept(next);
                changed = true;
            }
        }

        void text(ValidatedRow row, String column, String previous, Consumer<String> setter) {
            if (present(row, column)) {
                set(previous, row.values().get(column), setter);
            }
        }

        void integer(ValidatedRow row, String column, int previous, Consumer<Integer> setter) {
            if (present(row, column)) {
                set(previous, Integer.valueOf(row.values().get(column)), setter);
            }
        }

        void shortValue(ValidatedRow row, String column, Short previous, Consumer<Short> setter) {
            if (present(row, column)) {
                set(previous, Short.valueOf(row.values().get(column)), setter);
            }
        }

        void decimal(ValidatedRow row, String column, BigDecimal previous, Consumer<BigDecimal> setter) {
            if (present(row, column)) {
                BigDecimal next = new BigDecimal(row.values().get(column));
                if (previous == null || previous.compareTo(next) != 0) {
                    setter.accept(next);
                    changed = true;
                }
            }
        }
    }
}
