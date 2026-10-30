package com.cnhsk.learning.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Service;

/** Java boundary for other modules. Caller identities come from AuthApi, never request bodies. */
@Service
public class PracticeApi {

    private final PracticeService practice;

    PracticeApi(PracticeService practice) {
        this.practice = practice;
    }

    /** The caller's userId is part of the server-created command, as specified in §0.11. */
    public SessionView start(StartPracticeCommand command) {
        return viewOf(practice.start(new PracticeService.StartPracticeCommand(command.userId(),
                com.cnhsk.learning.practice.PracticeSource.valueOf(command.source().name()),
                command.refId(), command.gameCode(), command.items().stream().map(PracticeApi::itemOf).toList(),
                command.preRecorded().stream().map(PracticeApi::answerOf).toList(),
                command.ttlOverride(), command.sessionMeta())));
    }

    public AnswerResult answer(long userId, long sessionId, Answer answer) {
        var result = practice.answer(userId, sessionId, answerOf(answer));
        return new AnswerResult(result.no(), result.correct(), result.correctAnswer(), result.accept(),
                result.explanation(), result.counted(), result.accepted(), result.recorded());
    }

    public FinishResult finish(long userId, long sessionId, List<Answer> answers) {
        var result = practice.finish(userId, sessionId,
                answers == null ? List.of() : answers.stream().map(PracticeApi::answerOf).toList());
        return new FinishResult(result.sessionId(), result.status(), result.score(), result.maxScore(),
                result.percent(), result.suspicious(), result.items().stream().map(item ->
                        new FinishItem(item.no(), item.correct(), item.correctAnswer(), item.yourAnswer())).toList(),
                result.extra());
    }

    public SessionView view(long userId, long sessionId) {
        return viewOf(practice.view(userId, sessionId));
    }

    private static PracticeSourceHandler.Answer answerOf(Answer answer) {
        return new PracticeSourceHandler.Answer(answer.no(), answer.answer(), answer.durationMs(),
                answer.replayCount(), answer.skipped(), answer.audioFailed(), answer.clientMeta());
    }

    private static com.cnhsk.learning.practice.ServedItem itemOf(ServedItem item) {
        Prompt prompt = item.prompt();
        return new com.cnhsk.learning.practice.ServedItem(item.no(),
                com.cnhsk.learning.practice.ItemType.valueOf(item.type().name()),
                com.cnhsk.learning.practice.Skill.valueOf(item.skill().name()), item.lexemeId(),
                new com.cnhsk.learning.practice.ServedItem.Prompt(prompt.text(), prompt.pinyin(),
                        prompt.sinoViet(), prompt.meaning(), prompt.audioUrl(), prompt.sentence(), prompt.hint(),
                        prompt.examples() == null ? null : prompt.examples().stream().map(example ->
                                new com.cnhsk.learning.practice.ServedItem.Example(
                                        example.cn(), example.pinyin(), example.vi())).toList(), prompt.kind()),
                item.options() == null ? null : item.options().stream().map(option ->
                        new com.cnhsk.learning.practice.ServedItem.Option(option.key(), option.text())).toList(),
                item.answer(), item.accept(), item.explanation(),
                new com.cnhsk.learning.practice.ServedItem.Rules(item.rules().minDurationMs(),
                        item.rules().maxReplays(), item.rules().hasAudio()));
    }

    private static SessionView viewOf(PracticeService.SessionView view) {
        return new SessionView(view.sessionId(), PracticeSource.valueOf(view.source().name()), view.refId(),
                FeedbackMode.valueOf(view.feedback().name()), view.status(), view.servedAt(), view.expiresAt(),
                view.items().stream().map(item -> {
                    var prompt = item.prompt();
                    return new ServedItemView(item.no(), ItemType.valueOf(item.type().name()),
                            Skill.valueOf(item.skill().name()), item.lexemeId(),
                            new Prompt(prompt.text(), prompt.pinyin(), prompt.sinoViet(), prompt.meaning(),
                                    prompt.audioUrl(), prompt.sentence(), prompt.hint(),
                                    prompt.examples() == null ? null : prompt.examples().stream().map(example ->
                                            new Example(example.cn(), example.pinyin(), example.vi())).toList(),
                                    prompt.kind()),
                            item.options() == null ? null : item.options().stream().map(option ->
                                    new Option(option.key(), option.text())).toList(),
                            new Rules(item.rules().minDurationMs(), item.rules().maxReplays(), item.rules().hasAudio()));
                }).toList(),
                view.answered().stream().map(item -> new ItemState(item.no(), item.answer(), item.correct(),
                        item.correctAnswer(), item.counted())).toList(), view.meta());
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static Map<String, Object> copy(Map<String, Object> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum PracticeSource {
        TOPIC_STUDY, TOPIC_PRACTICE, TOPIC_TEST, GRAMMAR_LESSON, PRON_STAGE,
        CHAR_RECOGNITION, CHAR_WRITING, WRITING_CHALLENGE, GAME_ROUND
    }

    public enum FeedbackMode { IMMEDIATE, DEFERRED, RECORD }

    public enum ItemType { STUDY_CARD, MEANING_CHOICE, AUDIO_WORD_CHOICE, CLOZE_CHOICE, PINYIN_INPUT, PRON_CHOICE }

    public enum Skill { VOCAB, LISTENING, READING, CHARACTER, WRITING, GRAMMAR, PRONUNCIATION }

    /** Server-only input: the source supplies answer keys, not the learner's client. */
    public record StartPracticeCommand(long userId, PracticeSource source, Long refId, String gameCode,
            List<ServedItem> items, List<Answer> preRecorded, Duration ttlOverride, Map<String, Object> sessionMeta) {
        public StartPracticeCommand {
            items = List.copyOf(items);
            preRecorded = preRecorded == null ? List.of() : List.copyOf(preRecorded);
            sessionMeta = copy(sessionMeta);
        }
    }

    /** Server-only snapshot input. Session responses use ServedItemView instead. */
    public record ServedItem(int no, ItemType type, Skill skill, Long lexemeId, Prompt prompt,
            List<Option> options, String answer, List<String> accept, String explanation, Rules rules) {
        public ServedItem {
            options = copy(options);
            accept = copy(accept);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Prompt(String text, String pinyin, String sinoViet, String meaning, String audioUrl,
            String sentence, String hint, List<Example> examples, String kind) {
        public Prompt {
            examples = copy(examples);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Example(String cn, String pinyin, String vi) { }

    public record Option(String key, String text) { }

    public record Rules(int minDurationMs, int maxReplays, boolean hasAudio) { }

    public record Answer(int no, String answer, int durationMs, int replayCount,
            boolean skipped, boolean audioFailed, Map<String, Object> clientMeta) {
        public Answer {
            clientMeta = copy(clientMeta);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServedItemView(int no, ItemType type, Skill skill, Long lexemeId, Prompt prompt,
            List<Option> options, Rules rules) {
        public ServedItemView {
            options = copy(options);
        }
    }

    public record SessionView(long sessionId, PracticeSource source, Long refId, FeedbackMode feedback,
            String status, Instant servedAt, Instant expiresAt, List<ServedItemView> items,
            List<ItemState> answered, Map<String, Object> meta) {
        public SessionView {
            items = List.copyOf(items);
            answered = List.copyOf(answered);
            meta = copy(meta);
        }
    }

    public record ItemState(int no, String answer, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean correct,
            @JsonInclude(JsonInclude.Include.NON_NULL) String correctAnswer, boolean counted) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerResult(int no, Boolean correct, String correctAnswer, List<String> accept,
            String explanation, Boolean counted, Boolean accepted, Boolean recorded) {
        public AnswerResult {
            accept = copy(accept);
        }
    }

    public record FinishResult(long sessionId, String status, Long score, Long maxScore, Integer percent,
            boolean suspicious, List<FinishItem> items, Map<String, Object> extra) {
        public FinishResult {
            items = List.copyOf(items);
            extra = copy(extra);
        }
    }

    public record FinishItem(int no, Boolean correct, String correctAnswer, String yourAnswer) { }
}
