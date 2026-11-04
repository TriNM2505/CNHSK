package com.cnhsk.learning.library;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.library.DeckService.Card;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

@Repository
public class CardRepository {
    private final EntityManager entityManager;

    public CardRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean lockOwned(long deckId, long cardId) {
        return !entityManager.createNativeQuery("""
                SELECT id FROM learning.collection_items
                WHERE collection_id=:deckId AND id=:cardId AND deleted_at IS NULL FOR UPDATE
                """).setParameter("deckId", deckId).setParameter("cardId", cardId).getResultList().isEmpty();
    }

    public Optional<Lexeme> lexeme(long lexemeId) {
        List<?> rows = entityManager.createNativeQuery("SELECT text,pinyin,meaning_vi FROM learning.lexemes WHERE id=:id", Tuple.class)
                .setParameter("id", lexemeId).getResultList();
        return rows.stream().findFirst().map(value -> {
            Tuple row = (Tuple) value;
            String pinyin = row.get("pinyin", String.class);
            String meaning = row.get("meaning_vi", String.class);
            return new Lexeme(row.get("text", String.class), pinyin == null || pinyin.isEmpty() ? meaning : pinyin + " — " + meaning);
        });
    }

    /** One bounded scan checks both duplicate rules and the active-card quota under the user's lock. */
    public List<ActiveCard> active(long deckId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT id,lexeme_id,title FROM learning.collection_items
                WHERE collection_id=:deckId AND deleted_at IS NULL
                """, Tuple.class).setParameter("deckId", deckId).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number lexemeId = (Number) row.get("lexeme_id");
            return new ActiveCard(((Number) row.get("id")).longValue(), lexemeId == null ? null : lexemeId.longValue(), row.get("title", String.class));
        }).toList();
    }

    public Optional<Long> insert(long deckId, Long lexemeId, String front, String back, Instant now) {
        List<?> rows = entityManager.createNativeQuery("""
                INSERT INTO learning.collection_items(collection_id,lexeme_id,title,content,order_index,created_at,updated_at)
                VALUES (:deckId,cast(:lexemeId AS bigint),:front,:back,
                    (SELECT coalesce(max(order_index),-1)+1 FROM learning.collection_items WHERE collection_id=:deckId),:now,:now)
                ON CONFLICT (collection_id,lexeme_id) WHERE lexeme_id IS NOT NULL AND deleted_at IS NULL
                DO NOTHING RETURNING id
                """).setParameter("deckId", deckId).setParameter("lexemeId", lexemeId)
                .setParameter("front", front).setParameter("back", back).setParameter("now", now).getResultList();
        return rows.stream().findFirst().map(value -> ((Number) value).longValue());
    }

    public void update(long deckId, long cardId, String front, String back, Instant now) {
        entityManager.createNativeQuery("""
                UPDATE learning.collection_items SET title=:front,content=:back,updated_at=:now
                WHERE collection_id=:deckId AND id=:cardId AND deleted_at IS NULL
                """).setParameter("deckId", deckId).setParameter("cardId", cardId)
                .setParameter("front", front).setParameter("back", back).setParameter("now", now).executeUpdate();
    }

    public void delete(long deckId, long cardId, Instant now) {
        entityManager.createNativeQuery("""
                UPDATE learning.collection_items SET deleted_at=:now
                WHERE collection_id=:deckId AND id=:cardId AND deleted_at IS NULL
                """).setParameter("deckId", deckId).setParameter("cardId", cardId).setParameter("now", now).executeUpdate();
    }

    public Card get(long userId, long deckId, long cardId) {
        Tuple row = (Tuple) entityManager.createNativeQuery("""
                SELECT i.id,i.lexeme_id,i.title,i.content,p.next_review_at,
                    coalesce(p.review_count,0) AS reviews,coalesce(p.lapse_count,0) AS lapses
                FROM learning.collection_items i LEFT JOIN learning.user_progress p
                    ON p.user_id=:userId AND p.target_type='FLASHCARD' AND p.target_id=i.id
                WHERE i.collection_id=:deckId AND i.id=:cardId AND i.deleted_at IS NULL
                """, Tuple.class).setParameter("userId", userId).setParameter("deckId", deckId).setParameter("cardId", cardId).getSingleResult();
        Number lexemeId = (Number) row.get("lexeme_id");
        return new Card(((Number) row.get("id")).longValue(), lexemeId == null ? null : lexemeId.longValue(),
                row.get("title", String.class), row.get("content", String.class), instant(row.get("next_review_at")),
                ((Number) row.get("reviews")).intValue(), ((Number) row.get("lapses")).intValue());
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime date) return date.toInstant();
        return ((Timestamp) value).toInstant();
    }

    public record Lexeme(String front, String back) { }
    public record ActiveCard(long id, Long lexemeId, String front) { }
}
