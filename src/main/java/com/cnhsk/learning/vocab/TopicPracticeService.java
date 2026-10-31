package com.cnhsk.learning.vocab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.generate.ChoiceItemFactory;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicPracticeService {

    private final TopicPracticeRepository repository;
    private final ChoiceItemFactory choices;
    private final PracticeService practice;

    public TopicPracticeService(TopicPracticeRepository repository, ChoiceItemFactory choices, PracticeService practice) {
        this.repository = repository;
        this.choices = choices;
        this.practice = practice;
    }

    @Transactional
    public SessionView start(long userId, long topicId, Mode mode, List<Long> focusLexemeIds) {
        if (!repository.hasWords(topicId)) {
            throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy chủ đề hoặc chủ đề chưa có từ.", Map.of());
        }
        List<Lexeme> candidates = repository.candidates(userId, topicId);
        if (candidates.isEmpty()) throw rejected(ErrorCode.NO_LEARNED_WORDS, "Bạn chưa học từ nào của chủ đề.");
        if (focusLexemeIds != null) {
            Set<Long> focus = new HashSet<>(focusLexemeIds);
            Set<Long> learned = candidates.stream().map(Lexeme::getId).collect(Collectors.toSet());
            if (!learned.containsAll(focus)) {
                throw rejected(ErrorCode.INVALID_FOCUS_WORDS, "Danh sách từ luyện lại không hợp lệ.");
            }
            candidates = candidates.stream().filter(word -> focus.contains(word.getId())).toList();
        }
        boolean listening = mode == Mode.LISTENING;
        if (listening) {
            candidates = candidates.stream().filter(word -> word.getAudioUrl() != null).toList();
            if (candidates.isEmpty()) throw rejected(ErrorCode.NO_AUDIO_WORDS, "Các từ đã chọn chưa có âm thanh.");
        }
        ItemType type = listening ? ItemType.AUDIO_WORD_CHOICE : ItemType.MEANING_CHOICE;
        var items = new ArrayList<ServedItem>();
        boolean mixed = false;
        for (Lexeme word : candidates) {
            var generated = choices.create(word, topicId, type, 4);
            if (generated.isEmpty()) continue;
            var options = generated.orElseThrow();
            var prompt = new ServedItem.Prompt(listening ? null : word.getText(), null, null, null,
                    listening ? word.getAudioUrl() : null, null, null, null, null);
            items.add(new ServedItem(items.size() + 1, type, listening ? Skill.LISTENING : Skill.VOCAB,
                    word.getId(), prompt, options.options(), options.correctAnswer(), null, null,
                    new ServedItem.Rules(800, 3, listening)));
            mixed |= options.mixedDistractors();
            if (items.size() == 10) break;
        }
        if (items.isEmpty()) throw rejected(ErrorCode.NOT_ENOUGH_WORDS, "Không đủ từ để tạo phương án luyện tập.");
        return practice.start(new StartPracticeCommand(userId, PracticeSource.TOPIC_PRACTICE, topicId,
                null, items, List.of(), null, mixed ? Map.of("mixedDistractors", true) : Map.of()));
    }

    private static ApiException rejected(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message, Map.of());
    }

    public enum Mode {
        RECOGNITION, LISTENING
    }
}
