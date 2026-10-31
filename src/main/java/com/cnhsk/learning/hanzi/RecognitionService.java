package com.cnhsk.learning.hanzi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecognitionService {
    private static final int DEFAULT_COUNT = 10;
    private final CharacterQueryRepository characters;
    private final ChoiceItemFactory choices;
    private final PracticeService practice;

    public RecognitionService(CharacterQueryRepository characters, ChoiceItemFactory choices, PracticeService practice) {
        this.characters = characters;
        this.choices = choices;
        this.practice = practice;
    }

    @Transactional
    public SessionView start(long userId, String modeValue, Integer hskLevel, Integer requestedCount) {
        RecognitionMode mode = parseMode(modeValue);
        if (hskLevel == null || hskLevel < 1 || hskLevel > 9) {
            throw validation("hskLevel");
        }
        int count = requestedCount == null ? DEFAULT_COUNT : requestedCount;
        if (count < 5 || count > 20) {
            throw validation("count");
        }

        List<Lexeme> candidates = characters.recognitionCandidates(hskLevel.shortValue(),
                mode == RecognitionMode.AUDIO_TO_CHAR);
        Map<Long, LatestAnswer> latestByLexeme = latestAnswers(userId, candidates);
        List<Lexeme> wrongMostRecently = new ArrayList<>();
        List<Lexeme> unseen = new ArrayList<>();
        List<Lexeme> remaining = new ArrayList<>();
        for (Lexeme candidate : candidates) {
            LatestAnswer latest = latestByLexeme.get(candidate.getId());
            if (latest == null) {
                unseen.add(candidate);
            } else if (Boolean.FALSE.equals(latest.correct())) {
                wrongMostRecently.add(candidate);
            } else {
                remaining.add(candidate);
            }
        }
        wrongMostRecently.sort(Comparator
                .comparing((Lexeme candidate) -> latestByLexeme.get(candidate.getId()).answeredAt()).reversed()
                .thenComparing(candidate -> latestByLexeme.get(candidate.getId()).itemId(), Comparator.reverseOrder()));
        Collections.shuffle(unseen);
        Collections.shuffle(remaining);
        List<Lexeme> prioritized = new ArrayList<>(candidates.size());
        prioritized.addAll(wrongMostRecently);
        prioritized.addAll(unseen);
        prioritized.addAll(remaining);

        ItemType type = mode == RecognitionMode.CHAR_TO_MEANING
                ? ItemType.CHAR_MEANING_CHOICE : ItemType.AUDIO_CHAR_CHOICE;
        Skill skill = mode == RecognitionMode.CHAR_TO_MEANING ? Skill.CHARACTER : Skill.LISTENING;
        List<ServedItem> served = new ArrayList<>();
        for (Lexeme target : prioritized) {
            var generated = choices.create(target, null, type, 4);
            if (generated.isEmpty()) {
                continue;
            }
            ServedItem.Prompt prompt = mode == RecognitionMode.CHAR_TO_MEANING
                    ? new ServedItem.Prompt(target.getText(), null, null, null, null, null,
                            null, null, "CHARACTER")
                    : new ServedItem.Prompt(null, null, null, null, target.getAudioUrl(), null,
                            null, null, "CHARACTER");
            String explanation = target.getText()
                    + (target.getPinyin() == null || target.getPinyin().isBlank() ? "" : " (" + target.getPinyin() + ")")
                    + ": " + target.getMeaningVi();
            var options = generated.orElseThrow();
            served.add(new ServedItem(served.size() + 1, type, skill, target.getId(), prompt,
                    options.options(), options.correctAnswer(), null, explanation,
                    new ServedItem.Rules(800, 3, mode == RecognitionMode.AUDIO_TO_CHAR)));
            if (served.size() == count) {
                break;
            }
        }
        if (served.isEmpty()) {
            return null;
        }
        return practice.start(new StartPracticeCommand(userId, PracticeSource.CHAR_RECOGNITION, null,
                null, served, List.of(), null, Map.of()));
    }

    private Map<Long, LatestAnswer> latestAnswers(long userId, List<Lexeme> candidates) {
        Map<Long, LatestAnswer> latest = new HashMap<>();
        for (Object[] row : characters.recognitionHistory(userId,
                candidates.stream().map(Lexeme::getId).toList())) {
            latest.putIfAbsent((Long) row[0], new LatestAnswer((Boolean) row[1], (Instant) row[2], (Long) row[3]));
        }
        return latest;
    }

    private RecognitionMode parseMode(String value) {
        if (value == null) {
            throw validation("mode");
        }
        try {
            return RecognitionMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw validation("mode");
        }
    }

    private ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field,
                        "message", "Giá trị không hợp lệ."))));
    }

    private enum RecognitionMode {
        CHAR_TO_MEANING,
        AUDIO_TO_CHAR
    }

    private record LatestAnswer(Boolean correct, Instant answeredAt, Long itemId) {
    }
}
