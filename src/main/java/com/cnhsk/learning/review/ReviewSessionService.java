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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds due-review sessions from the caller's own lexeme schedules. */
@Service
public class ReviewSessionService {

    private static final String LOCK_START = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";
    private static final String SUMMARY = "SELECT count(*) FILTER (WHERE p.next_review_at <= ?) AS due_count, "
            + "min(p.next_review_at) FILTER (WHERE p.next_review_at > ?) AS next_due_at "
            + "FROM learning.user_progress p JOIN learning.lexemes l ON l.id = p.target_id "
            + "WHERE p.user_id = ? AND p.target_type = 'LEXEME' AND p.next_review_at IS NOT NULL";
    private static final String CANDIDATES = "SELECT p.target_id "
            + "FROM learning.user_progress p JOIN learning.lexemes l ON l.id = p.target_id "
            + "WHERE p.user_id = ? AND p.target_type = 'LEXEME' AND p.next_review_at IS NOT NULL "
            + "AND (? OR p.next_review_at <= ?) ORDER BY p.next_review_at ASC, p.target_id ASC LIMIT ?";

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
        List<Long> ids = plainJdbc.query(CANDIDATES, (row, index) -> row.getLong("target_id"),
                userId, ahead, Timestamp.from(now), config.getSessionMaxItems());
        if (ids.isEmpty()) {
            return new StartResult(null, false);
        }

        Map<Long, Lexeme> byId = new HashMap<>();
        lexemes.findAllById(ids).forEach(lexeme -> byId.put(lexeme.getId(), lexeme));
        List<ServedItem> served = new ArrayList<>();
        for (Long id : ids) {
            Lexeme lexeme = byId.get(id);
            if (lexeme == null) {
                continue;
            }
            ServedItem item = item(served.size() + 1, lexeme);
            if (item != null) {
                served.add(item);
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
        var rules = new ServedItem.Rules(0, 0, lexeme.getAudioUrl() != null);
        if (kind == LexemeKind.GRAMMAR) {
            return new ServedItem(no, type, skill, lexeme.getId(), flashcardPrompt(lexeme, kind),
                    null, null, null, null, rules);
        }
        ChoiceOptions options = choices.create(lexeme, null, type, 4).orElse(null);
        if (options == null) {
            return new ServedItem(no, ItemType.FLASHCARD_RATING, skill, lexeme.getId(), flashcardPrompt(lexeme, kind),
                    null, null, null, null, rules);
        }
        return new ServedItem(no, type, skill, lexeme.getId(), choicePrompt(lexeme), options.options(),
                options.correctAnswer(), null, null, rules);
    }

    private static ServedItem.Prompt choicePrompt(Lexeme lexeme) {
        return new ServedItem.Prompt(lexeme.getText(), null, null, null, null, null, null, List.of(), null);
    }

    private static ServedItem.Prompt flashcardPrompt(Lexeme lexeme, LexemeKind kind) {
        return new ServedItem.Prompt(lexeme.getText(), lexeme.getPinyin(), lexeme.getSinoViet(),
                lexeme.getMeaningVi(), lexeme.getAudioUrl(), null, null, examples(lexeme.getExtra()), kind.name());
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

}
