package com.cnhsk.learning.vocab;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicTestService {

    private static final Logger log = LoggerFactory.getLogger(TopicTestService.class);
    private static final ItemType[] TYPES = {ItemType.MEANING_CHOICE, ItemType.AUDIO_WORD_CHOICE,
            ItemType.PINYIN_INPUT, ItemType.CLOZE_CHOICE};
    private final TopicTestRepository repository;
    private final PracticeService practice;
    private final Clock clock;
    private final RandomGenerator random;

    public TopicTestService(TopicTestRepository repository, PracticeService practice, Clock clock,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.repository = repository;
        this.practice = practice;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public TestStart start(long userId, long topicId) {
        repository.lockStart(userId, topicId);
        var topic = repository.topic(userId, topicId).orElseThrow(() -> new ApiException(ErrorCode.TOPIC_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Không tìm thấy chủ đề hoặc chủ đề chưa có từ.", Map.of()));
        SessionView active = practice.findActive(userId, PracticeSource.TOPIC_TEST, topicId);
        if (active != null) return new TestStart(active, false);
        if (topic.totalWords() < 4) {
            throw rejected(ErrorCode.NOT_ENOUGH_WORDS, "Chủ đề cần ít nhất 4 từ để kiểm tra.", Map.of("requiredWords", 4));
        }
        if (topic.learnedWords() * 100 < 80 * topic.totalWords()) {
            throw rejected(ErrorCode.NOT_ENOUGH_LEARNED, "Bạn cần học thêm từ trước khi kiểm tra.",
                    Map.of("learnedWords", topic.learnedWords(), "requiredWords", (topic.totalWords() * 80 + 99) / 100));
        }
        // Same latest-test projection and boundary as TopicQueryService.actions (T-09).
        if (topic.failedAt() != null && clock.instant().isBefore(topic.failedAt().plus(Duration.ofMinutes(10)))) {
            long retry = Math.max(1, (Duration.between(clock.instant(), topic.failedAt().plus(Duration.ofMinutes(10)))
                    .toMillis() + 999) / 1000);
            throw new ApiException(ErrorCode.RETRY_TOO_SOON, HttpStatus.TOO_MANY_REQUESTS,
                    "Vui lòng chờ trước khi kiểm tra lại.", Map.of("retryAfterSeconds", retry));
        }
        var words = new ArrayList<>(repository.words(topicId));
        for (int i = words.size() - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            Lexeme previous = words.set(i, words.get(index));
            words.set(index, previous);
        }
        var items = new ArrayList<ServedItem>();
        boolean mixed = false;
        for (Lexeme word : words) {
            var generated = generate(word, topicId, items.size() + 1, TYPES[items.size() % TYPES.length]);
            if (generated.isEmpty()) {
                log.warn("Skipping topic-test question lexemeId={}", word.getId());
                continue;
            }
            items.add(generated.orElseThrow().item());
            mixed |= generated.orElseThrow().mixed();
            if (items.size() == 20) break;
        }
        if (items.isEmpty()) throw rejected(ErrorCode.NOT_ENOUGH_WORDS, "Không đủ dữ liệu để tạo bài kiểm tra.", Map.of());
        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.TOPIC_TEST, topicId,
                null, items, List.of(), null, mixed ? Map.of("mixedDistractors", true) : Map.of()));
        return new TestStart(session, true);
    }

    private Optional<Generated> generate(Lexeme word, long topicId, int no, ItemType type) {
        if ((type == ItemType.AUDIO_WORD_CHOICE && word.getAudioUrl() == null)
                || (type == ItemType.CLOZE_CHOICE && example(word) == null)) type = ItemType.MEANING_CHOICE;
        if (type != ItemType.PINYIN_INPUT) {
            var choice = choice(word, topicId, no, type);
            if (choice.isPresent()) return choice;
        }
        var pinyin = pinyin(word, no);
        return pinyin.isPresent() ? pinyin : choice(word, topicId, no, ItemType.MEANING_CHOICE);
    }

    private Optional<Generated> choice(Lexeme word, long topicId, int no, ItemType type) {
        var options = repository.choices(word, topicId, type);
        if (options.isEmpty()) return Optional.empty();
        LexemeExtra.Example example = type == ItemType.CLOZE_CHOICE ? example(word) : null;
        String sentence = null;
        if (example != null) {
            int index = example.cn().indexOf(word.getText());
            sentence = example.cn().substring(0, index) + "＿＿" + example.cn().substring(index + word.getText().length());
        }
        var prompt = new ServedItem.Prompt(type == ItemType.MEANING_CHOICE ? word.getText() : null,
                null, null, null, type == ItemType.AUDIO_WORD_CHOICE ? word.getAudioUrl() : null,
                sentence, example == null ? null : example.vi(), null, null);
        Skill skill = type == ItemType.AUDIO_WORD_CHOICE ? Skill.LISTENING
                : type == ItemType.CLOZE_CHOICE ? Skill.READING : Skill.VOCAB;
        var generated = options.orElseThrow();
        return Optional.of(new Generated(new ServedItem(no, type, skill, word.getId(), prompt, generated.options(),
                generated.correctAnswer(), null, null, new ServedItem.Rules(800, 3, type == ItemType.AUDIO_WORD_CHOICE)),
                generated.mixedDistractors()));
    }

    private Optional<Generated> pinyin(Lexeme word, int no) {
        String accept;
        try {
            accept = PinyinUtils.toNumbered(word.getPinyin());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (accept == null || accept.isBlank()) return Optional.empty();
        var prompt = new ServedItem.Prompt(word.getText(), null, null, null, null, null, word.getMeaningVi(), null, null);
        return Optional.of(new Generated(new ServedItem(no, ItemType.PINYIN_INPUT, Skill.VOCAB, word.getId(), prompt,
                null, null, List.of(accept.replace(" ", "")), null, new ServedItem.Rules(0, 3, false)), false));
    }

    private static LexemeExtra.Example example(Lexeme word) {
        if (word.getExtra().examples() == null || word.getExtra().examples().isEmpty()) return null;
        LexemeExtra.Example first = word.getExtra().examples().get(0);
        return first.cn() != null && first.cn().contains(word.getText()) ? first : null;
    }

    private static ApiException rejected(ErrorCode code, String message, Map<String, Object> extra) {
        return new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message, extra);
    }

    private record Generated(ServedItem item, boolean mixed) {
    }

    public record TestStart(SessionView session, boolean created) {
    }
}
