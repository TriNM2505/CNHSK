package com.cnhsk.community.contest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cnhsk.community.contest.Contest.ScheduleState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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

    public ContestRepository(EntityManager entities) {
        this.entities = entities;
    }

    public Optional<Contest> findPublished(long id) {
        return entities.createQuery("select c from Contest c where c.id = :id and c.status = 'PUBLISHED'", Contest.class)
                .setParameter("id", id).getResultStream().findFirst();
    }

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
