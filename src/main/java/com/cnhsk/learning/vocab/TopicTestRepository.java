package com.cnhsk.learning.vocab;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.learning.vocab.TopicQueryRepository.TopicRow;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
public class TopicTestRepository {

    private static final Logger log = LoggerFactory.getLogger(TopicTestRepository.class);
    private final TopicQueryRepository topics;
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final ChoiceItemFactory choices;

    public TopicTestRepository(TopicQueryRepository topics, EntityManager entityManager, NamedParameterJdbcTemplate jdbc,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.topics = topics;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        // The start service already owns the transaction. Use the stateless helper on that same EntityManager,
        // so a recoverable pinyin exception does not cross a nested transactional proxy and mark it rollback-only.
        this.choices = new ChoiceItemFactory(entityManager, random);
    }

    public Optional<ChoiceItemFactory.ChoiceOptions> choices(Lexeme word, long topicId, ItemType type) {
        try {
            return choices.create(word, topicId, type, 4);
        } catch (IllegalArgumentException exception) {
            log.warn("Cannot normalize topic-test choice pinyin lexemeId={}", word.getId());
            return Optional.empty();
        }
    }

    public void lockStart(long userId, long topicId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "TOPIC_TEST:" + userId + ":" + topicId), result -> null);
    }

    public Optional<TopicRow> topic(long userId, long topicId) {
        return topics.summaries(userId, null, topicId).stream().findFirst();
    }

    public List<Lexeme> words(long topicId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT x.* FROM learning.topic_items ti JOIN learning.lexemes x ON x.id = ti.lexeme_id
                WHERE ti.topic_id = :topicId ORDER BY ti.order_index, ti.id
                """, Lexeme.class).setParameter("topicId", topicId).getResultList();
        return rows.stream().map(Lexeme.class::cast).toList();
    }

    /** Current scores come from FinishContext; history excludes that row even if JPA already flushed it. */
    public PastResult past(long userId, Long topicId, long sessionId) {
        return jdbc.queryForObject("""
                SELECT coalesce(bool_or(max_score > 0 AND score * 100 >= 90 * max_score), false) AS completed,
                       max(score * 100 / nullif(max_score, 0)) AS best_percent
                FROM learning.attempts
                WHERE user_id = :userId AND ref_type = 'TOPIC_TEST' AND ref_id = cast(:topicId AS bigint)
                  AND status = 'SUBMITTED' AND id <> :sessionId
                """, new MapSqlParameterSource().addValue("userId", userId).addValue("topicId", topicId)
                        .addValue("sessionId", sessionId), (row, no) -> {
                    Number best = (Number) row.getObject("best_percent");
                    return new PastResult(row.getBoolean("completed"), best == null ? null : best.intValue());
                });
    }

    public List<Lexeme> wordsByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("select l from Lexeme l where l.id in :ids", Lexeme.class)
                .setParameter("ids", ids).getResultList();
    }

    public record PastResult(boolean completed, Integer bestPercent) {
    }
}
