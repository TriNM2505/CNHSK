package com.cnhsk.community.game.common;

import com.cnhsk.learning.api.ContentLookup.LexemeCard;
import com.cnhsk.learning.api.PracticeApi.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;

public final class GameItemBuilders {
    public enum DistractorMode { MEANING, SOUND }

    /** The correct key stays in the server snapshot. Only options are served to the client. */
    public record Choice(List<Option> options, String answer) {
        public Choice { options = List.copyOf(options); }
    }

    private GameItemBuilders() { }

    public static List<LexemeCard> selectQuestions(List<LexemeCard> pool, int limit, RandomGenerator random) {
        if (limit < 0) throw new IllegalArgumentException("Question limit must be nonnegative.");
        Set<Long> seen = new HashSet<>();
        return shuffled(pool, random).stream().filter(card -> seen.add(card.id())).limit(limit).toList();
    }

    /** Returns empty when the pool cannot supply enough unambiguous choices for this item. */
    public static Optional<Choice> chooseOptions(LexemeCard correct, List<LexemeCard> pool,
            DistractorMode mode, Function<LexemeCard, String> display, int optionCount, RandomGenerator random) {
        if (optionCount < 2 || optionCount > 6) {
            throw new IllegalArgumentException("A game choice must have two to six options.");
        }
        String correctText = display.apply(correct);
        if (isBlank(correct.text()) || isBlank(correctText) || !hasMatchKey(correct, mode)) return Optional.empty();

        Set<Long> ids = new HashSet<>(Set.of(correct.id()));
        Set<String> lexemes = new HashSet<>(Set.of(correct.text()));
        Set<String> labels = new HashSet<>(Set.of(correctText));
        Set<String> usedMatchKeys = new HashSet<>(matchKeys(correct, mode));
        List<String> distractors = new ArrayList<>();
        for (LexemeCard candidate : shuffled(pool, random)) {
            String label = display.apply(candidate);
            if (isBlank(candidate.text()) || isBlank(label) || !hasMatchKey(candidate, mode)
                    || ids.contains(candidate.id()) || lexemes.contains(candidate.text()) || labels.contains(label)) {
                continue;
            }
            Set<String> keys = matchKeys(candidate, mode);
            if (!Collections.disjoint(usedMatchKeys, keys)) continue;
            ids.add(candidate.id());
            lexemes.add(candidate.text());
            labels.add(label);
            usedMatchKeys.addAll(keys);
            distractors.add(label);
            if (distractors.size() == optionCount - 1) {
                return Optional.of(shuffleOptions(correctText, distractors, random));
            }
        }
        return Optional.empty();
    }

    /** Assigns keys after shuffling an already validated set of display values. */
    public static Choice shuffleOptions(String correct, List<String> distractors, RandomGenerator random) {
        if (distractors.isEmpty() || distractors.size() > 5 || isBlank(correct)) {
            throw new IllegalArgumentException("A game choice must have two to six nonblank options.");
        }
        List<String> labels = new ArrayList<>();
        labels.add(correct);
        for (String label : distractors) {
            if (isBlank(label) || labels.contains(label)) throw new IllegalArgumentException("Options must be distinct and nonblank.");
            labels.add(label);
        }
        List<String> order = shuffled(labels, random);
        List<Option> options = new ArrayList<>(order.size());
        String answer = null;
        for (int i = 0; i < order.size(); i++) {
            String key = String.valueOf((char) ('A' + i));
            options.add(new Option(key, order.get(i)));
            if (order.get(i).equals(correct)) answer = key;
        }
        return new Choice(options, answer);
    }

    private static boolean hasMatchKey(LexemeCard card, DistractorMode mode) {
        return switch (mode) {
            case MEANING -> !card.meaningKeys().isEmpty();
            case SOUND -> !isBlank(card.pinyinKey());
        };
    }

    private static Set<String> matchKeys(LexemeCard card, DistractorMode mode) {
        return switch (mode) {
            case MEANING -> card.meaningKeys();
            case SOUND -> Set.of(card.pinyinKey());
        };
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static <T> List<T> shuffled(List<T> input, RandomGenerator random) {
        List<T> copy = new ArrayList<>(input);
        for (int i = copy.size() - 1; i > 0; i--) Collections.swap(copy, i, random.nextInt(i + 1));
        return copy;
    }
}
