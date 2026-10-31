package com.cnhsk.learning.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeRateLimiter;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.FinishResult;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts GRAMMAR_LESSON sessions; exercise answers stay in the server-only snapshot. */
@Service
public class GrammarLessonService {

    private static final int MAX_READ_DURATION_MS = 3_600_000;
    private static final int MAX_EXERCISES = 5;
    private static final int CHOICE_MIN_DURATION_MS = 800;

    private final LexemeRepository lexemes;
    private final PracticeService practice;
    private final PracticeRateLimiter rateLimiter;
    private final NamedParameterJdbcTemplate jdbc;
    private final RandomGenerator random;

    public GrammarLessonService(LexemeRepository lexemes, PracticeService practice, PracticeRateLimiter rateLimiter,
            NamedParameterJdbcTemplate jdbc, @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.lexemes = lexemes;
        this.practice = practice;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
        this.random = random;
    }

    @Transactional
    public LessonStart start(long userId, long grammarId, int readDurationMs) {
        if (readDurationMs < 0 || readDurationMs > MAX_READ_DURATION_MS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", "readDurationMs",
                            "message", "Giá trị không hợp lệ."))));
        }
        Lexeme grammar = lexemes.findById(grammarId)
                .filter(lexeme -> lexeme.getKind() == LexemeKind.GRAMMAR)
                .orElseThrow(() -> new ApiException(ErrorCode.GRAMMAR_POINT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy điểm ngữ pháp.", Map.of()));
        lock(userId, grammarId);
        SessionView active = practice.findActive(userId, PracticeSource.GRAMMAR_LESSON, grammarId);
        if (active != null) return new LessonStart(active, null, false);

        LexemeExtra extra = grammar.getExtra() == null ? LexemeExtra.empty() : grammar.getExtra();
        var items = new ArrayList<ServedItem>();
        items.add(lessonRead(grammar));
        for (LexemeExtra.Exercise exercise : pickExercises(extra.exercises())) {
            items.add(choice(items.size() + 1, grammar.getId(), exercise));
        }
        // GrammarLessonHandler derives readTooFast for this pre-recorded item; the session meta stays empty.
        var understood = new Answer(1, "UNDERSTOOD", readDurationMs, 0, false, false, null);
        var command = new StartPracticeCommand(userId, PracticeSource.GRAMMAR_LESSON, grammarId, null,
                items, List.of(understood), null, null);
        if (items.size() > 1) return new LessonStart(practice.start(command), null, true);

        // recordAndFinish leaves rate limiting to the caller; grammar lessons still use the start quota.
        rateLimiter.checkStart(userId);
        FinishResult finish = practice.recordAndFinish(command);
        return new LessonStart(practice.view(userId, finish.sessionId()), finish, true);
    }

    private void lock(long userId, long grammarId) {
        // Values form a bound key, never SQL. The lock releases at transaction completion.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "GRAMMAR_LESSON:" + userId + ":" + grammarId), result -> null);
    }

    private List<LexemeExtra.Exercise> pickExercises(List<LexemeExtra.Exercise> exercises) {
        if (exercises == null || exercises.isEmpty()) return List.of();
        List<LexemeExtra.Exercise> shuffled = new ArrayList<>(exercises);
        shuffle(shuffled);
        return shuffled.subList(0, Math.min(MAX_EXERCISES, shuffled.size()));
    }

    private static ServedItem lessonRead(Lexeme grammar) {
        var prompt = new ServedItem.Prompt(grammar.getText(), null, null, grammar.getMeaningVi(), null,
                null, null, null, LexemeKind.GRAMMAR.name());
        return new ServedItem(1, ItemType.LESSON_READ, Skill.GRAMMAR, grammar.getId(), prompt,
                null, null, null, null, new ServedItem.Rules(0, 0, false));
    }

    private ServedItem choice(int no, long grammarId, LexemeExtra.Exercise exercise) {
        // Spec 003 G-6: shuffle imported options, then assign fresh keys so stored keys never leak position.
        List<LexemeExtra.Option> shuffled = new ArrayList<>(exercise.options());
        shuffle(shuffled);
        var options = new ArrayList<ServedItem.Option>();
        String answer = null;
        for (int index = 0; index < shuffled.size(); index++) {
            String key = Character.toString((char) ('A' + index));
            options.add(new ServedItem.Option(key, shuffled.get(index).text()));
            if (shuffled.get(index).key().equals(exercise.answerKey())) answer = key;
        }
        var prompt = new ServedItem.Prompt(null, null, null, null, null, exercise.stem(), null, null, null);
        return new ServedItem(no, ItemType.GRAMMAR_CHOICE, Skill.GRAMMAR, grammarId, prompt, options,
                Objects.requireNonNull(answer, "answerKey"), null, exercise.explanation(),
                new ServedItem.Rules(CHOICE_MIN_DURATION_MS, 0, false));
    }

    private <T> void shuffle(List<T> items) {
        for (int index = items.size() - 1; index > 0; index--) {
            Collections.swap(items, index, random.nextInt(index + 1));
        }
    }

    /** finish is present only when a lesson without exercises was recorded and submitted immediately. */
    public record LessonStart(SessionView session, FinishResult finish, boolean created) {
    }
}
