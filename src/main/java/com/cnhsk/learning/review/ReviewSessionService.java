package com.cnhsk.learning.review;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory.ChoiceOptions;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds due-review sessions from the caller's own lexeme schedules. */
@Service
public class ReviewSessionService {

    private static final Logger log = LoggerFactory.getLogger(ReviewSessionService.class);
    private static final String LOCK_START = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";
    private static final String SUMMARY = "SELECT count(*) FILTER (WHERE p.next_review_at <= ?) AS due_count, "
            + "min(p.next_review_at) FILTER (WHERE p.next_review_at > ?) AS next_due_at "
            + "FROM learning.user_progress p JOIN learning.lexemes l ON l.id = p.target_id "
            + "WHERE p.user_id = ? AND p.target_type = 'LEXEME' AND p.next_review_at IS NOT NULL";
    private static final String CANDIDATES = "SELECT p.target_id, p.next_review_at, (l.id IS NOT NULL) AS lexeme_exists "
            + "FROM learning.user_progress p LEFT JOIN learning.lexemes l ON l.id = p.target_id "
            + "WHERE p.user_id = ? AND p.target_type = 'LEXEME' AND p.next_review_at IS NOT NULL "
            + "AND (? OR p.next_review_at <= ?) ORDER BY p.next_review_at ASC, p.target_id ASC";

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;
    private final LexemeRepository lexemes;
    private final PracticeService practice;
    private final ChoiceItemFactory choices;
    private final FsrsConfig config;
    private final Clock clock;

    public ReviewSessionService(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc,
            LexemeRepository lexemes, PracticeService practice, ChoiceItemFactory choices,
            FsrsConfig config, Clock clock) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
        this.lexemes = lexemes;
        this.practice = practice;
        this.choices = choices;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewSummary summary(long userId) {
        Instant now = clock.instant();
        return plainJdbc.queryForObject(SUMMARY, (row, index) -> {
            Timestamp next = row.getTimestamp("next_due_at");
            return new ReviewSummary(row.getInt("due_count"), next == null ? null : next.toInstant());
        }, Timestamp.from(now), Timestamp.from(now), userId);
    }

    @Transactional
    public StartResult start(long userId, boolean ahead) {
        jdbc.query(LOCK_START, Map.of("key", "review:start:" + userId), row -> null);
        SessionView active = practice.findActive(userId, PracticeSource.DUE_REVIEW, null);
        if (active != null) {
            return new StartResult(active, false);
        }

        Instant now = clock.instant();
        List<ScheduleCandidate> candidates = plainJdbc.query(CANDIDATES, (row, index) ->
                new ScheduleCandidate(row.getLong("target_id"), row.getTimestamp("next_review_at").toInstant(),
                        row.getBoolean("lexeme_exists")),
                userId, ahead, Timestamp.from(now));
        if (candidates.isEmpty()) {
            return new StartResult(null, false);
        }

        List<Long> ids = candidates.stream().filter(ScheduleCandidate::lexemeExists)
                .map(ScheduleCandidate::targetId).toList();
        Map<Long, Lexeme> byId = new HashMap<>();
        lexemes.findAllById(ids).forEach(lexeme -> byId.put(lexeme.getId(), lexeme));
        List<ServedItem> served = new ArrayList<>();
        int limit = config.getSessionMaxItems();
        for (ScheduleCandidate candidate : candidates) {
            if (!candidate.lexemeExists()) {
                log.warn("Skipping orphaned review schedule userId={} targetId={}", userId, candidate.targetId());
                continue;
            }
            Lexeme lexeme = byId.get(candidate.targetId());
            if (lexeme == null) {
                log.warn("Skipping orphaned review schedule userId={} targetId={}", userId, candidate.targetId());
                continue;
            }
            ServedItem item = item(served.size() + 1, lexeme);
            if (item != null) {
                served.add(item);
                if (served.size() == limit) {
                    break;
                }
            }
        }
        if (served.isEmpty()) {
            return new StartResult(null, false);
        }
        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.DUE_REVIEW,
                null, null, served, List.of(), null, Map.of()));
        return new StartResult(session, true);
    }

    private ServedItem item(int no, Lexeme lexeme) {
        LexemeKind kind = lexeme.getKind();
        ItemType type;
        Skill skill;
        if (kind == LexemeKind.WORD) {
            type = ItemType.MEANING_CHOICE;
            skill = Skill.VOCAB;
        } else if (kind == LexemeKind.CHAR) {
            type = ItemType.CHAR_MEANING_CHOICE;
            skill = Skill.CHARACTER;
        } else {
            type = ItemType.FLASHCARD_RATING;
            skill = Skill.GRAMMAR;
        }
        List<ServedItem.Example> examples = examples(lexeme.getExtra());
        var prompt = new ServedItem.Prompt(lexeme.getText(), lexeme.getPinyin(), lexeme.getSinoViet(),
                lexeme.getMeaningVi(), lexeme.getAudioUrl(), null, null, examples, kind.name());
        var rules = new ServedItem.Rules(0, 0, lexeme.getAudioUrl() != null);
        if (kind == LexemeKind.GRAMMAR) {
            return new ServedItem(no, type, skill, lexeme.getId(), prompt, null, null, null, null, rules);
        }
        ChoiceOptions options = choices.create(lexeme, null, type, 4).orElse(null);
        if (options == null) {
            return new ServedItem(no, ItemType.FLASHCARD_RATING, skill, lexeme.getId(), prompt,
                    null, null, null, null, rules);
        }
        return new ServedItem(no, type, skill, lexeme.getId(), prompt, options.options(),
                options.correctAnswer(), null, null, rules);
    }

    private static List<ServedItem.Example> examples(LexemeExtra extra) {
        if (extra == null || extra.examples() == null) {
            return List.of();
        }
        return extra.examples().stream()
                .map(example -> new ServedItem.Example(example.cn(), example.pinyin(), example.vi())).toList();
    }

    public record ReviewSummary(int dueCount, Instant nextDueAt) { }

    public record StartResult(SessionView session, boolean created) { }

    public record NothingDue(Object session, String reason) { }

    private record ScheduleCandidate(long targetId, Instant nextReviewAt, boolean lexemeExists) { }
}
