package com.cnhsk.learning.practice.generate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.ServedItem.Option;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds server-side choice options; callers copy only options into ServedItemView. */
@Component
public class ChoiceItemFactory {

    static final int PAGE_SIZE = 64;
    private static final Logger log = LoggerFactory.getLogger(ChoiceItemFactory.class);
    private static final String BASE = "from Lexeme l where l.id <> :targetId";
    private static final String IN_TOPIC = "exists (select ti.id from TopicItem ti "
            + "where ti.lexemeId = l.id and ti.topicId = :topicId)";
    private final EntityManager entityManager;
    private final RandomGenerator random;

    public ChoiceItemFactory(EntityManager entityManager,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Transactional(readOnly = true)
    public Optional<ChoiceOptions> create(Lexeme target, Long topicId, ItemType type, int optionCount) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(target.getId(), "target.id");
        Objects.requireNonNull(target.getKind(), "target.kind");
        if (type != ItemType.MEANING_CHOICE && type != ItemType.AUDIO_WORD_CHOICE && type != ItemType.CLOZE_CHOICE
                && type != ItemType.CHAR_MEANING_CHOICE && type != ItemType.AUDIO_CHAR_CHOICE) {
            throw new IllegalArgumentException("Choice generation requires a choice item type.");
        }
        if (optionCount < 2 || optionCount > 6) {
            throw new IllegalArgumentException("Choice option count must be between 2 and 6.");
        }
        List<Facts> selected = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        Set<String> meanings = new HashSet<>();
        Set<String> pronunciations = new HashSet<>();
        Facts correct = facts(target, type);
        add(correct, selected, ids, texts, meanings, pronunciations);
        boolean mixed = false;
        for (Tier tier : tiers(target, topicId, type)) {
            var countQuery = entityManager.createQuery("select count(l) " + tier.clause(), Long.class);
            bind(countQuery, tier.parameters());
            int total = Math.toIntExact(countQuery.setMaxResults(1).getSingleResult());
            if (total == 0) {
                continue;
            }
            int offset = random.nextInt(total);
            int visited = 0;
            while (visited < total && selected.size() < optionCount) {
                int limit = Math.min(PAGE_SIZE, Math.min(total - visited, total - offset));
                var query = entityManager.createQuery("select l " + tier.clause() + " order by l.id", Lexeme.class);
                bind(query, tier.parameters());
                List<Lexeme> page = new ArrayList<>(query.setFirstResult(offset).setMaxResults(limit).getResultList());
                shuffle(page);
                for (Lexeme candidate : page) {
                    Facts next = facts(candidate, type);
                    if (ids.contains(next.id()) || texts.contains(next.text())
                            || !Collections.disjoint(meanings, next.meanings())
                            || ((type == ItemType.AUDIO_WORD_CHOICE || type == ItemType.AUDIO_CHAR_CHOICE)
                                    && pronunciations.contains(next.pronunciation()))) {
                        continue;
                    }
                    add(next, selected, ids, texts, meanings, pronunciations);
                    mixed |= tier.mixed();
                    if (selected.size() == optionCount) {
                        break;
                    }
                }
                visited += limit;
                offset = (offset + limit) % total;
            }
            if (selected.size() == optionCount) {
                break;
            }
        }
        if (selected.size() < optionCount) {
            log.warn("Not enough choice distractors for lexemeId={}", target.getId());
            return Optional.empty();
        }
        shuffle(selected);
        List<Option> options = new ArrayList<>();
        String answer = null;
        for (int index = 0; index < selected.size(); index++) {
            Facts item = selected.get(index);
            String key = Character.toString((char) ('A' + index));
            options.add(new Option(key, item.display()));
            if (item.id().equals(target.getId())) {
                answer = key;
            }
        }
        return Optional.of(new ChoiceOptions(options, Objects.requireNonNull(answer), mixed));
    }

    private List<Tier> tiers(Lexeme target, Long topicId, ItemType type) {
        List<Tier> tiers = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("targetId", target.getId());
        if (type == ItemType.CHAR_MEANING_CHOICE && target.getRadical() != null && !target.getRadical().isBlank()) {
            parameters.put("kind", target.getKind());
            parameters.put("radical", target.getRadical());
            String sameLevel;
            if (target.getHskLevel() == null) {
                sameLevel = " and l.hskLevel is null";
            } else {
                parameters.put("level", target.getHskLevel());
                sameLevel = " and l.hskLevel = :level";
            }
            tiers.add(new Tier(BASE + " and l.kind = :kind" + sameLevel + " and l.radical = :radical",
                    Map.copyOf(parameters), false));
            parameters.remove("kind");
            parameters.remove("level");
            parameters.remove("radical");
        }
        if (topicId != null) {
            parameters.put("topicId", topicId);
            tiers.add(new Tier(BASE + " and " + IN_TOPIC, Map.copyOf(parameters), false));
        }
        parameters.put("kind", target.getKind());
        String otherTopics = topicId == null ? "" : " and not " + IN_TOPIC;
        String sameLevel = " and l.hskLevel is null";
        String otherLevels = " and l.hskLevel is not null";
        if (target.getHskLevel() != null) {
            parameters.put("level", target.getHskLevel());
            sameLevel = " and l.hskLevel = :level";
            otherLevels = " and (l.hskLevel <> :level or l.hskLevel is null)";
        }
        String kind = BASE + " and l.kind = :kind" + otherTopics;
        tiers.add(new Tier(kind + sameLevel, Map.copyOf(parameters), topicId != null));
        tiers.add(new Tier(kind + otherLevels, Map.copyOf(parameters), topicId != null));
        return tiers;
    }

    private static void bind(TypedQuery<?> query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private Facts facts(Lexeme lexeme, ItemType type) {
        String normalizedMeaning = lexeme.getMeaningNorm() == null
                ? TextNormalizer.normalizeMeaning(lexeme.getMeaningVi()) : lexeme.getMeaningNorm();
        Set<String> meanings = Arrays.stream(normalizedMeaning.split("[;,]"))
                .map(part -> TextNormalizer.normalizeCell("meaning_vi", part))
                .filter(part -> !part.isEmpty()).collect(Collectors.toSet());
        String display = type == ItemType.MEANING_CHOICE || type == ItemType.CHAR_MEANING_CHOICE
                ? TextNormalizer.normalizeCell("meaning_vi", lexeme.getMeaningVi().split(";", 2)[0]) : lexeme.getText();
        String pronunciation = type == ItemType.AUDIO_WORD_CHOICE || type == ItemType.AUDIO_CHAR_CHOICE
                ? PinyinUtils.toNumbered(lexeme.getPinyin()) : null;
        return new Facts(lexeme.getId(), lexeme.getText(), display, meanings,
                pronunciation == null ? null : pronunciation.replace(" ", ""));
    }

    private static void add(Facts item, List<Facts> selected, Set<Long> ids, Set<String> texts,
            Set<String> meanings, Set<String> pronunciations) {
        selected.add(item);
        ids.add(item.id());
        texts.add(item.text());
        meanings.addAll(item.meanings());
        pronunciations.add(item.pronunciation());
    }

    private <T> void shuffle(List<T> items) {
        for (int index = items.size() - 1; index > 0; index--) {
            Collections.swap(items, index, random.nextInt(index + 1));
        }
    }

    private record Facts(Long id, String text, String display, Set<String> meanings, String pronunciation) {
    }

    private record Tier(String clause, Map<String, Object> parameters, boolean mixed) {
    }

    /** correctAnswer is consumed through its Java accessor when building the server-only snapshot. */
    public record ChoiceOptions(List<Option> options, @JsonIgnore String correctAnswer, boolean mixedDistractors) {
        public ChoiceOptions {
            options = List.copyOf(options);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class RandomConfiguration {
        @Bean("practiceChoiceRandom")
        RandomGenerator practiceChoiceRandom() {
            return ThreadLocalRandom.current();
        }
    }
}
