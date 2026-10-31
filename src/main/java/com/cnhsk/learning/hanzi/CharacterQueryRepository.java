package com.cnhsk.learning.hanzi;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** Character search and writing evidence queries owned by the Hanzi feature. */
@Repository
public class CharacterQueryRepository {
    private static final String SEARCH = "select x from Lexeme x where x.kind = :kind "
            + "and (:level is null or x.hskLevel = :level) "
            + "and (:text is null or x.text = :text or (:hasPrefix = true "
            + "and x.pinyinNorm like concat(:prefix, '%') escape '!')) "
            + "order by x.hskLevel asc, x.frequency desc, x.id asc";
    private static final String COUNT = "select count(x) from Lexeme x where x.kind = :kind "
            + "and (:level is null or x.hskLevel = :level) "
            + "and (:text is null or x.text = :text or (:hasPrefix = true "
            + "and x.pinyinNorm like concat(:prefix, '%') escape '!'))";
    private final EntityManager entityManager;

    public CharacterQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Lexeme> recognitionCandidates(short hskLevel, boolean requireAudio) {
        return entityManager.createQuery("select x from Lexeme x where x.kind = :kind "
                        + "and x.hskLevel = :level and trim(x.meaningVi) <> '' "
                        + "and (:requireAudio = false or (x.audioUrl is not null and trim(x.audioUrl) <> '')) "
                        + "order by x.id", Lexeme.class)
                .setParameter("kind", LexemeKind.CHAR).setParameter("level", hskLevel)
                .setParameter("requireAudio", requireAudio).getResultList();
    }

    /** Returns every answered recognition item, regardless of attempt status or mode. */
    public List<Object[]> recognitionHistory(long userId, Collection<Long> lexemeIds) {
        if (lexemeIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("select i.lexemeId, i.correct, i.answeredAt, i.id "
                        + "from AttemptItem i, Attempt a where a.id = i.attemptId "
                        + "and a.userId = :userId and a.refType = 'CHAR_RECOGNITION' "
                        + "and i.lexemeId in :lexemeIds order by i.answeredAt desc, i.id desc", Object[].class)
                .setParameter("userId", userId).setParameter("lexemeIds", lexemeIds).getResultList();
    }

    public Page<Lexeme> search(LexemeKind kind, Short hskLevel, String text, String pinyinPrefix, Pageable pageable) {
        String escapedPrefix = pinyinPrefix == null ? "" : escapeLikeLiteral(pinyinPrefix);
        var query = entityManager.createQuery(SEARCH, Lexeme.class)
                .setParameter("kind", kind).setParameter("level", hskLevel)
                .setParameter("text", text).setParameter("hasPrefix", pinyinPrefix != null)
                .setParameter("prefix", escapedPrefix);
        var count = entityManager.createQuery(COUNT, Long.class)
                .setParameter("kind", kind).setParameter("level", hskLevel)
                .setParameter("text", text).setParameter("hasPrefix", pinyinPrefix != null)
                .setParameter("prefix", escapedPrefix);
        return new PageImpl<>(query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList(), pageable, count.getSingleResult());
    }

    private String escapeLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public Lexeme findCharacter(long id) {
        return entityManager.createQuery("select x from Lexeme x where x.id = :id and x.kind = :kind", Lexeme.class)
                .setParameter("id", id).setParameter("kind", LexemeKind.CHAR)
                .getResultStream().findFirst().orElse(null);
    }

    /** Reads only the approved mode key after all required relational filters are applied. */
    public boolean recallUnlocked(long userId, long lexemeId) {
        Object value = entityManager.createNativeQuery("select exists ("
                + "select 1 from learning.attempt_items ai "
                + "join learning.attempts a on a.id = ai.attempt_id "
                + "where a.user_id = :userId and a.ref_type = 'CHAR_WRITING' and a.status = 'SUBMITTED' "
                + "and a.ref_id = :lexemeId and ai.lexeme_id = :lexemeId "
                + "and ai.item_type = 'WRITING_RESULT' and ai.answer = 'COMPLETED' "
                + "and ai.meta->>'mode' = 'STROKE')")
                .setParameter("userId", userId).setParameter("lexemeId", lexemeId).getSingleResult();
        return Boolean.TRUE.equals(value);
    }

    /** Checks a completed writing of the same mode in the strict ten-minute window. */
    public boolean repeatWithin10Min(long userId, long lexemeId, String mode, Instant now) {
        Object value = entityManager.createNativeQuery("select exists ("
                + "select 1 from learning.attempt_items ai "
                + "join learning.attempts a on a.id = ai.attempt_id "
                + "where a.user_id = :userId and a.ref_type = 'CHAR_WRITING' and a.status = 'SUBMITTED' "
                + "and a.ref_id = :lexemeId and ai.lexeme_id = :lexemeId "
                + "and ai.item_type = 'WRITING_RESULT' and ai.answer = 'COMPLETED' "
                + "and a.submitted_at > :cutoff and a.submitted_at <= :now "
                + "and ai.meta->>'mode' = :mode)")
                .setParameter("userId", userId).setParameter("lexemeId", lexemeId)
                .setParameter("cutoff", now.minusSeconds(600)).setParameter("now", now)
                .setParameter("mode", mode).getSingleResult();
        return Boolean.TRUE.equals(value);
    }

    public Instant lastPracticedAt(long userId, long lexemeId) {
        Object value = entityManager.createNativeQuery("select max(ai.answered_at) "
                + "from learning.attempt_items ai join learning.attempts a on a.id = ai.attempt_id "
                + "where a.user_id = :userId and a.ref_type = 'CHAR_WRITING' and a.status = 'SUBMITTED' "
                + "and a.ref_id = :lexemeId and ai.lexeme_id = :lexemeId and ai.item_type = 'WRITING_RESULT'")
                .setParameter("userId", userId).setParameter("lexemeId", lexemeId).getSingleResult();
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        return (Instant) value;
    }
}
