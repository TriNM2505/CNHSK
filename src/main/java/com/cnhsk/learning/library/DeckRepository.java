package com.cnhsk.learning.library;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.library.DeckService.Card;
import com.cnhsk.learning.library.DeckService.DeckDetail;
import com.cnhsk.learning.library.DeckService.DeckSummary;
import com.cnhsk.learning.library.web.DeckWrite;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;

/** Fixed SQL projections stay in learning; caller values are always bound parameters. */
@Repository
public class DeckRepository {
    private static final String OWNED = "c.user_id=:userId AND c.kind='FLASHCARD_DECK' AND c.deleted_at IS NULL";
    private static final String SUMMARIES = """
            SELECT c.id, c.name, c.description, c.created_at, count(i.id) AS card_count,
                   count(i.id) FILTER (WHERE p.target_id IS NULL OR p.next_review_at <= :now) AS due_count
            FROM learning.collections c
            LEFT JOIN learning.collection_items i ON i.collection_id=c.id AND i.deleted_at IS NULL
            LEFT JOIN learning.user_progress p ON p.user_id=c.user_id AND p.target_type='FLASHCARD' AND p.target_id=i.id
            WHERE
            """ + OWNED;
    private final EntityManager entityManager;

    public DeckRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public void lock(long userId) {
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key", "library:decks:" + userId).getSingleResult();
    }

    public boolean lockOwned(long userId, long id) {
        return !entityManager.createNativeQuery("SELECT c.id FROM learning.collections c WHERE " + OWNED + " AND c.id=:id FOR UPDATE")
                .setParameter("userId", userId).setParameter("id", id).getResultList().isEmpty();
    }

    public long count(long userId) {
        return ((Number) entityManager.createNativeQuery("SELECT count(*) FROM learning.collections c WHERE " + OWNED)
                .setParameter("userId", userId).getSingleResult()).longValue();
    }

    public boolean topicExists(long id) {
        return !entityManager.createNativeQuery("SELECT id FROM learning.topics WHERE id=:id")
                .setParameter("id", id).getResultList().isEmpty();
    }

    public long insert(long userId, DeckWrite request, Instant now) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO learning.collections(user_id,kind,name,description,is_public,created_at,updated_at)
                VALUES (:userId,'FLASHCARD_DECK',:name,cast(:description AS text),false,:now,:now) RETURNING id
                """).setParameter("userId", userId).setParameter("name", request.name())
                .setParameter("description", request.description()).setParameter("now", now).getSingleResult()).longValue();
    }

    public void copyTopic(long deckId, long topicId, int limit, Instant now) {
        entityManager.createNativeQuery("""
                INSERT INTO learning.collection_items(collection_id,lexeme_id,title,content,order_index,created_at,updated_at)
                SELECT :deckId,l.id,l.text,
                    CASE WHEN l.pinyin IS NULL OR l.pinyin='' THEN l.meaning_vi ELSE l.pinyin || ' — ' || l.meaning_vi END,
                    cast(row_number() OVER (ORDER BY ti.order_index,ti.id)-1 AS integer),:now,:now
                FROM learning.topic_items ti JOIN learning.lexemes l ON l.id=ti.lexeme_id
                WHERE ti.topic_id=:topicId ORDER BY ti.order_index,ti.id LIMIT :limit
                """).setParameter("deckId", deckId).setParameter("topicId", topicId)
                .setParameter("limit", limit).setParameter("now", now).executeUpdate();
    }

    public void update(long userId, long id, DeckWrite request, Instant now) {
        entityManager.createNativeQuery("""
                UPDATE learning.collections c SET name=:name,description=cast(:description AS text),updated_at=:now
                WHERE
                """ + OWNED + " AND c.id=:id").setParameter("userId", userId).setParameter("id", id)
                .setParameter("name", request.name()).setParameter("description", request.description()).setParameter("now", now).executeUpdate();
    }

    public void softDelete(long userId, long id, Instant now) {
        entityManager.createNativeQuery("UPDATE learning.collections c SET deleted_at=:now WHERE " + OWNED + " AND c.id=:id")
                .setParameter("userId", userId).setParameter("id", id).setParameter("now", now).executeUpdate();
    }

    public List<DeckSummary> list(long userId, Instant now) {
        return summaries("", Map.of("userId", userId, "now", now));
    }

    public Optional<DeckSummary> summary(long userId, long id, Instant now) {
        return summaries(" AND c.id=:id", Map.of("userId", userId, "id", id, "now", now)).stream().findFirst();
    }

    public Optional<DeckDetail> detail(long userId, long id, Instant now) {
        return summary(userId, id, now).map(header -> new DeckDetail(header.id(), header.name(), header.description(),
                header.cardCount(), header.dueCount(), header.createdAt(), cards(userId, id)));
    }

    private List<DeckSummary> summaries(String filter, Map<String, ?> parameters) {
        Query query = entityManager.createNativeQuery(SUMMARIES + filter + " GROUP BY c.id ORDER BY c.created_at DESC,c.id DESC", Tuple.class);
        parameters.forEach(query::setParameter);
        List<?> rows = query.getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            return new DeckSummary(number(row, "id").longValue(), row.get("name", String.class), row.get("description", String.class),
                    number(row, "card_count").longValue(), number(row, "due_count").longValue(), instant(row, "created_at"));
        }).toList();
    }

    private List<Card> cards(long userId, long id) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT i.id,i.lexeme_id,i.title,i.content,p.next_review_at,p.review_count,p.lapse_count
                FROM learning.collections c JOIN learning.collection_items i ON i.collection_id=c.id AND i.deleted_at IS NULL
                LEFT JOIN learning.user_progress p ON p.user_id=c.user_id AND p.target_type='FLASHCARD' AND p.target_id=i.id
                WHERE
                """ + OWNED + " AND c.id=:id ORDER BY i.order_index,i.id", Tuple.class)
                .setParameter("userId", userId).setParameter("id", id).getResultList();
        return rows.stream().map(value -> {
            Tuple row = (Tuple) value;
            Number lexemeId = (Number) row.get("lexeme_id");
            Number reviews = (Number) row.get("review_count");
            Number lapses = (Number) row.get("lapse_count");
            return new Card(number(row, "id").longValue(), lexemeId == null ? null : lexemeId.longValue(),
                    row.get("title", String.class), row.get("content", String.class), instant(row, "next_review_at"),
                    reviews == null ? 0 : reviews.intValue(), lapses == null ? 0 : lapses.intValue());
        }).toList();
    }

    private static Number number(Tuple row, String field) { return (Number) row.get(field); }

    private static Instant instant(Tuple row, String field) {
        Object value = row.get(field);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime date) return date.toInstant();
        return ((Timestamp) value).toInstant();
    }
}
