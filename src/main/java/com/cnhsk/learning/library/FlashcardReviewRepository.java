package com.cnhsk.learning.library;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.cnhsk.learning.library.DeckService.Card;
import com.cnhsk.learning.review.FsrsScheduler.Memory;
import com.cnhsk.learning.review.FsrsScheduler.Result;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

@Repository
public class FlashcardReviewRepository {
    private final EntityManager entityManager;

    public FlashcardReviewRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean ownsDeck(long userId, long deckId) {
        return !entityManager.createNativeQuery("""
                SELECT id FROM learning.collections WHERE id=:deckId AND user_id=:userId
                  AND kind='FLASHCARD_DECK' AND deleted_at IS NULL
                """).setParameter("deckId", deckId).setParameter("userId", userId).getResultList().isEmpty();
    }

    public List<Card> due(long userId, long deckId, Instant now, boolean ahead) {
        List<?> rows = entityManager.createNativeQuery("""
                WITH active AS MATERIALIZED (
                    SELECT i.id,i.lexeme_id,i.title,i.content,i.order_index,p.next_review_at,
                           coalesce(p.review_count,0) AS reviews,coalesce(p.lapse_count,0) AS lapses,
                           p.target_id IS NULL AS is_new
                    FROM learning.collections c JOIN learning.collection_items i
                      ON i.collection_id=c.id AND i.deleted_at IS NULL
                    LEFT JOIN learning.user_progress p
                      ON p.user_id=:userId AND p.target_type='FLASHCARD' AND p.target_id=i.id
                    WHERE c.id=:deckId AND c.user_id=:userId AND c.kind='FLASHCARD_DECK' AND c.deleted_at IS NULL
                ), eligible AS (
                    SELECT 1 FROM active WHERE is_new OR next_review_at <= :now LIMIT 1
                )
                SELECT id,lexeme_id,title,content,next_review_at,reviews,lapses FROM active
                WHERE is_new OR next_review_at <= :now
                   OR (:ahead AND NOT EXISTS (SELECT 1 FROM eligible) AND next_review_at > :now)
                ORDER BY CASE WHEN next_review_at <= :now THEN 0 WHEN is_new THEN 1 ELSE 2 END,
                         CASE WHEN NOT is_new THEN next_review_at END ASC NULLS LAST,order_index ASC,id ASC
                LIMIT 100
                """, Tuple.class).setParameter("userId", userId).setParameter("deckId", deckId)
                .setParameter("now", now).setParameter("ahead", ahead).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number lexemeId = (Number) row.get("lexeme_id");
            return new Card(((Number) row.get("id")).longValue(), lexemeId == null ? null : lexemeId.longValue(),
                    row.get("title", String.class), row.get("content", String.class), instant(row.get("next_review_at")),
                    ((Number) row.get("reviews")).intValue(), ((Number) row.get("lapses")).intValue());
        }).toList();
    }

    public boolean lockCard(long userId, long cardId) {
        return !entityManager.createNativeQuery("""
                SELECT i.id FROM learning.collections c JOIN learning.collection_items i ON i.collection_id=c.id
                WHERE c.user_id=:userId AND c.kind='FLASHCARD_DECK' AND c.deleted_at IS NULL
                  AND i.id=:cardId AND i.deleted_at IS NULL FOR UPDATE OF c,i
                """).setParameter("userId", userId).setParameter("cardId", cardId).getResultList().isEmpty();
    }

    public boolean insertFirst(long userId, long cardId, Instant now) {
        return !entityManager.createNativeQuery("""
                INSERT INTO learning.user_progress(user_id,target_type,target_id,last_seen_at,updated_at)
                VALUES (:userId,'FLASHCARD',:cardId,:now,:now)
                ON CONFLICT (user_id,target_type,target_id) DO NOTHING RETURNING target_id
                """).setParameter("userId", userId).setParameter("cardId", cardId).setParameter("now", now).getResultList().isEmpty();
    }

    public Memory lockMemory(long userId, long cardId) {
        Tuple row = (Tuple) entityManager.createNativeQuery("""
                SELECT stability,difficulty,last_seen_at,lapse_count FROM learning.user_progress
                WHERE user_id=:userId AND target_type='FLASHCARD' AND target_id=:cardId FOR UPDATE
                """, Tuple.class).setParameter("userId", userId).setParameter("cardId", cardId).getSingleResult();
        return new Memory(row.get("stability", BigDecimal.class), row.get("difficulty", BigDecimal.class),
                instant(row.get("last_seen_at")), ((Number) row.get("lapse_count")).intValue());
    }

    public Instant update(long userId, long cardId, Instant now, Result result) {
        Object persisted = entityManager.createNativeQuery("""
                UPDATE learning.user_progress SET stability=:stability,difficulty=:difficulty,next_review_at=:nextReview,
                    review_count=review_count+1,lapse_count=:lapses,last_seen_at=:now,updated_at=:now
                WHERE user_id=:userId AND target_type='FLASHCARD' AND target_id=:cardId RETURNING next_review_at
                """).setParameter("userId", userId).setParameter("cardId", cardId).setParameter("now", now)
                .setParameter("stability", result.stability()).setParameter("difficulty", result.difficulty())
                .setParameter("nextReview", result.nextReviewAt()).setParameter("lapses", result.lapseCount()).getSingleResult();
        return instant(persisted);
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime date) return date.toInstant();
        return ((Timestamp) value).toInstant();
    }
}
