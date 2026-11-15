package com.cnhsk.community.contest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cnhsk.community.contest.Contest.ScheduleState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("communityContestRepository")
public class ContestRepository {
    private static final String ORDER = """
            order by case when c.startsAt <= :now and c.endsAt > :now then 0
                          when c.startsAt > :now then 1 else 2 end,
                     case when c.startsAt <= :now and c.endsAt > :now then c.endsAt end asc,
                     case when c.startsAt > :now then c.startsAt end asc,
                     case when c.endsAt <= :now then c.endsAt end desc,
                     c.id asc
            """;

    private final EntityManager entities;
    private final NamedParameterJdbcTemplate jdbc;

    public ContestRepository(EntityManager entities, NamedParameterJdbcTemplate jdbc) {
        this.entities = entities;
        this.jdbc = jdbc;
    }

    public void lockStart(long contestId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                java.util.Map.of("key", "contest:start:" + contestId), result -> null);
    }

    public Optional<Contest> findPublished(long id) {
        return entities.createQuery("select c from Contest c where c.id = :id and c.status = 'PUBLISHED'", Contest.class)
                .setParameter("id", id).getResultStream().findFirst();
    }

    public void lockExamUsage(long examId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('exam:usage:' || CAST(:id AS text), 0))",
                java.util.Map.of("id", examId), result -> null);
    }

    public Optional<Reference> reference(long id) {
        return jdbc.query("SELECT exam_id FROM community.contests WHERE id = :id",
                java.util.Map.of("id", id), (row, number) -> new Reference(row.getObject("exam_id", Long.class)))
                .stream().findFirst();
    }

    public Optional<Contest> find(long id) {
        return Optional.ofNullable(entities.find(Contest.class, id));
    }

    public Optional<Contest> findForUpdate(long id) {
        return Optional.ofNullable(entities.find(Contest.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public void create(Contest contest) {
        entities.persist(contest);
    }

    public long countAll(String status) {
        TypedQuery<Long> query = entities.createQuery("select count(c) from Contest c"
                + (status == null ? "" : " where c.status = :status"), Long.class);
        if (status != null) query.setParameter("status", status);
        return query.getSingleResult();
    }

    public List<Contest> findAll(String status, int page, int size) {
        TypedQuery<Contest> query = entities.createQuery("select c from Contest c"
                + (status == null ? "" : " where c.status = :status")
                + " order by c.startsAt desc, c.id desc", Contest.class);
        if (status != null) query.setParameter("status", status);
        return query.setFirstResult(Math.toIntExact((long) page * size)).setMaxResults(size).getResultList();
    }

    public boolean examInUse(long examId, Instant now) {
        return entities.createQuery("select count(c) from Contest c where c.examId = :examId"
                        + " and c.status = 'PUBLISHED' and c.endsAt > :now", Long.class)
                .setParameter("examId", examId).setParameter("now", now).getSingleResult() > 0;
    }

    public record Reference(Long examId) { }

    public long countPublished(ScheduleState state, Instant now) {
        TypedQuery<Long> query = entities.createQuery("select count(c) " + filter(state), Long.class);
        if (state != null) query.setParameter("now", now);
        return query.getSingleResult();
    }

    public List<Contest> findPublished(ScheduleState state, Instant now, int page, int size) {
        return entities.createQuery("select c " + filter(state) + ORDER, Contest.class)
                .setParameter("now", now).setFirstResult(Math.toIntExact((long) page * size))
                .setMaxResults(size).getResultList();
    }

    private static String filter(ScheduleState state) {
        String schedule = state == null ? "" : switch (state) {
            case UPCOMING -> "and c.startsAt > :now ";
            case ONGOING -> "and c.startsAt <= :now and c.endsAt > :now ";
            case ENDED -> "and c.endsAt <= :now ";
        };
        return "from Contest c where c.status = 'PUBLISHED' " + schedule;
    }
}
