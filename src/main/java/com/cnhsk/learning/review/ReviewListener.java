package com.cnhsk.learning.review;

import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Updates LEXEME FSRS rows in the same transaction that finishes a practice session. */
@Component
public class ReviewListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewListener.class);

    private static final String APPROVED_QUESTIONS = "SELECT id FROM learning.questions "
            + "WHERE status='APPROVED' AND id IN (%s)";
    private static final String INSERT_PREFIX = "INSERT INTO learning.user_progress "
            + "(user_id, target_type, target_id, stability, difficulty, review_count, lapse_count, last_seen_at) VALUES ";
    private static final String INSERT_SUFFIX = " ON CONFLICT (user_id, target_type, target_id) DO NOTHING "
            + "RETURNING target_id";
    private static final String LOCK_PREFIX = "SELECT target_id, stability, difficulty, last_seen_at, lapse_count, review_count "
            + "FROM learning.user_progress WHERE user_id=? AND target_type='LEXEME' AND target_id IN (%s) "
            + "ORDER BY target_id FOR UPDATE";
    private static final String UPDATE = "UPDATE learning.user_progress SET stability=?, difficulty=?, "
            + "next_review_at=?, review_count=?, lapse_count=?, last_seen_at=?, updated_at=? "
            + "WHERE user_id=? AND target_type='LEXEME' AND target_id=?";

    private final AttemptRepository attempts;
    private final AttemptItemRepository items;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final FsrsConfig config;
    private final ObjectProvider<FsrsScheduler> schedulers;

    public ReviewListener(AttemptRepository attempts, AttemptItemRepository items, JdbcTemplate jdbc,
            Clock clock, FsrsConfig config, ObjectProvider<FsrsScheduler> schedulers) {
        this.attempts = attempts;
        this.items = items;
        this.jdbc = jdbc;
        this.clock = clock;
        this.config = config;
        this.schedulers = schedulers;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional(propagation = Propagation.MANDATORY)
    public void onFinished(SessionFinishedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Review listener requires the finish transaction");
        }
        Instant now = clock.instant();
        Attempt attempt = attempts.findById(event.sessionId())
                .orElseThrow(() -> new IllegalStateException("Finished attempt does not exist"));
        if (attempt.getUserId() != event.userId() || !event.source().name().equals(attempt.getRefType())) {
            throw new IllegalStateException("Finished attempt does not match the event owner or source");
        }
        if (event.suspicious() || attempt.isSuspicious()) {
            return;
        }
        if (!supports(event.source())) {
            log.warn("No FSRS review handling for practice source {}", event.source());
            return;
        }

        List<AttemptItem> attemptItems = items.findByAttemptIdOrderByOrderIndexAsc(event.sessionId());
        Map<Long, Candidate> candidates = collectCandidates(event.source(), attemptItems, approvedQuestionIds(attemptItems));
        if (candidates.isEmpty()) {
            return;
        }

        List<Long> lexemeIds = candidates.keySet().stream().sorted().toList();
        Set<Long> insertedIds = insertMissingRows(event.userId(), lexemeIds, now);
        Map<Long, ScheduleRow> schedules = lockRows(event.userId(), lexemeIds);
        if (schedules.size() != lexemeIds.size()) {
            throw new IllegalStateException("A locked lexeme schedule row is missing");
        }

        FsrsScheduler scheduler = schedulers.getIfAvailable(() -> new FsrsScheduler(config.settings()));
        List<Object[]> updates = new ArrayList<>(lexemeIds.size());
        for (Long lexemeId : lexemeIds) {
            ScheduleRow previous = schedules.get(lexemeId);
            Candidate candidate = candidates.get(lexemeId);
            FsrsScheduler.Result result;
            int reviewCount;
            if (insertedIds.contains(lexemeId)) {
                result = scheduler.first(candidate.firstRating(event.source()), now);
                reviewCount = 0;
            } else {
                FsrsScheduler.Rating rating = candidate.reviewRating(event.source());
                result = scheduler.review(new FsrsScheduler.Memory(previous.stability(), previous.difficulty(),
                        previous.lastSeenAt(), previous.lapseCount()), rating, now);
                reviewCount = Math.incrementExact(previous.reviewCount());
            }
            updates.add(new Object[] {
                    result.stability().setScale(3, RoundingMode.HALF_UP),
                    result.difficulty().setScale(2, RoundingMode.HALF_UP),
                    Timestamp.from(result.nextReviewAt()), reviewCount, result.lapseCount(), Timestamp.from(now),
                    Timestamp.from(now), event.userId(), lexemeId
            });
        }
        int[] changed = jdbc.batchUpdate(UPDATE, updates);
        if (changed.length != lexemeIds.size()) {
            throw new IllegalStateException("Not all lexeme schedules were updated");
        }
    }

    private Set<Long> approvedQuestionIds(List<AttemptItem> attemptItems) {
        List<Long> questionIds = attemptItems.stream().filter(item -> item.getLexemeId() != null)
                .filter(item -> !excludedMeta(item.getMeta()))
                .filter(item -> item.getQuestionId() != null && item.getCorrect() != null)
                .map(AttemptItem::getQuestionId).distinct().sorted().toList();
        if (questionIds.isEmpty()) {
            return Set.of();
        }
        String sql = APPROVED_QUESTIONS.formatted(placeholders(questionIds.size()));
        return new LinkedHashSet<>(jdbc.query(sql, (result, row) -> result.getLong(1), questionIds.toArray()));
    }

    private Map<Long, Candidate> collectCandidates(PracticeSource source, List<AttemptItem> attemptItems,
            Set<Long> approvedQuestions) {
        Map<Long, Candidate> result = new LinkedHashMap<>();
        for (AttemptItem item : attemptItems) {
            if (item.getLexemeId() == null || excludedMeta(item.getMeta())) {
                continue;
            }
            if (source == PracticeSource.TOPIC_STUDY) {
                if ("LEARNED".equals(item.getAnswer())) {
                    result.computeIfAbsent(item.getLexemeId(), ignored -> new Candidate()).learned = true;
                } else if ("KNOWN".equals(item.getAnswer())) {
                    result.computeIfAbsent(item.getLexemeId(), ignored -> new Candidate()).known = true;
                }
                continue;
            }
            if (item.getCorrect() == null
                    || item.getQuestionId() != null && !approvedQuestions.contains(item.getQuestionId())) {
                continue;
            }
            Candidate candidate = result.computeIfAbsent(item.getLexemeId(), ignored -> new Candidate());
            if (item.getCorrect()) {
                candidate.correct = true;
            } else {
                candidate.incorrect = true;
            }
        }
        return result;
    }

    private Set<Long> insertMissingRows(long userId, List<Long> lexemeIds, Instant now) {
        StringBuilder sql = new StringBuilder(INSERT_PREFIX);
        List<Object> parameters = new ArrayList<>(lexemeIds.size() * 3);
        for (int index = 0; index < lexemeIds.size(); index++) {
            if (index > 0) sql.append(',');
            sql.append("(?, 'LEXEME', ?, 0.1, 5, 0, 0, ?)");
            parameters.add(userId);
            parameters.add(lexemeIds.get(index));
            parameters.add(Timestamp.from(now));
        }
        sql.append(INSERT_SUFFIX);
        return new LinkedHashSet<>(jdbc.query(sql.toString(), (result, row) -> result.getLong(1),
                parameters.toArray()));
    }

    private Map<Long, ScheduleRow> lockRows(long userId, List<Long> lexemeIds) {
        String sql = LOCK_PREFIX.formatted(placeholders(lexemeIds.size()));
        Object[] parameters = new Object[lexemeIds.size() + 1];
        parameters[0] = userId;
        for (int index = 0; index < lexemeIds.size(); index++) parameters[index + 1] = lexemeIds.get(index);
        Map<Long, ScheduleRow> result = new LinkedHashMap<>();
        jdbc.query(sql, (ResultSet row) -> {
            long id = row.getLong("target_id");
            result.put(id, new ScheduleRow(id, row.getBigDecimal("stability"), row.getBigDecimal("difficulty"),
                    row.getTimestamp("last_seen_at").toInstant(), row.getInt("lapse_count"),
                    row.getInt("review_count")));
        }, parameters);
        return result;
    }

    static boolean supports(PracticeSource source) {
        return switch (source) {
            case TOPIC_STUDY, TOPIC_PRACTICE, TOPIC_TEST, GRAMMAR_LESSON, CHAR_RECOGNITION,
                    GAME_ROUND, MOCK_EXAM, PLACEMENT, TARGETED_PRACTICE -> true;
            case PRON_STAGE, CHAR_WRITING, WRITING_CHALLENGE -> false;
            default -> false;
        };
    }

    private static boolean excludedMeta(Map<String, Object> meta) {
        return meta != null && (meta.containsKey("skipped") || meta.containsKey("missing")
                || meta.containsKey("audioFailed") || meta.containsKey("suspicious"));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    static FsrsScheduler.Rating firstRating(PracticeSource source, boolean learned, boolean known,
            boolean incorrect) {
        if (source == PracticeSource.TOPIC_STUDY) {
            return learned ? FsrsScheduler.Rating.GOOD : FsrsScheduler.Rating.EASY;
        }
        return incorrect ? FsrsScheduler.Rating.AGAIN : FsrsScheduler.Rating.GOOD;
    }

    static FsrsScheduler.Rating reviewRating(PracticeSource source, boolean incorrect) {
        if (source == PracticeSource.TOPIC_STUDY) return FsrsScheduler.Rating.GOOD;
        return incorrect ? FsrsScheduler.Rating.AGAIN : FsrsScheduler.Rating.GOOD;
    }

    private static final class Candidate {
        private boolean correct;
        private boolean incorrect;
        private boolean learned;
        private boolean known;

        private FsrsScheduler.Rating firstRating(PracticeSource source) {
            return ReviewListener.firstRating(source, learned, known, incorrect);
        }

        private FsrsScheduler.Rating reviewRating(PracticeSource source) {
            return ReviewListener.reviewRating(source, incorrect);
        }
    }

    private record ScheduleRow(long lexemeId, java.math.BigDecimal stability, java.math.BigDecimal difficulty,
            Instant lastSeenAt, int lapseCount, int reviewCount) { }
}
