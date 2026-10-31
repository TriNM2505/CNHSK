package com.cnhsk.learning.practice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.PracticeSourceHandler.FinishContext;
import com.cnhsk.learning.practice.PracticeSourceHandler.GradedItem;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.learning.practice.grading.PinyinGrader;
import com.cnhsk.learning.practice.event.ItemGradedEvent;
import com.cnhsk.learning.practice.event.SessionFinishedEvent;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Session creation, restoration and server-side answers; callers obtain user ids through AuthApi. */
@Service
public class PracticeService {

    private final AttemptRepository attempts;
    private final AttemptItemRepository attemptItems;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final PracticeRateLimiter rateLimiter;
    private final List<PracticeSourceHandler> handlers;
    private final ApplicationEventPublisher events;
    private TransactionTemplate answerTransaction;

    public PracticeService(AttemptRepository attempts, AttemptItemRepository attemptItems,
            EntityManager entityManager, ObjectMapper mapper, Clock clock,
            PracticeRateLimiter rateLimiter, List<PracticeSourceHandler> handlers, ApplicationEventPublisher events) {
        this.attempts = attempts;
        this.attemptItems = attemptItems;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.handlers = List.copyOf(handlers);
        this.events = events;
    }

    @Autowired
    void configureAnswerTransaction(PlatformTransactionManager manager) {
        answerTransaction = new TransactionTemplate(manager);
        // Expiry is committed before the API error is thrown, including when a caller has a transaction.
        answerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public SessionView start(StartPracticeCommand command) {
        return toView(createInTransaction(command, true));
    }

    /** Creates and finishes a pre-recorded non-DEFERRED attempt; the caller owns rate limiting. */
    @Transactional
    public FinishResult recordAndFinish(StartPracticeCommand command) {
        if (command == null || command.source() == null || command.source().feedback() == FeedbackMode.DEFERRED
                || command.preRecorded().isEmpty()
                || command.items().stream().anyMatch(item -> item.type().gradable())) {
            throw new IllegalArgumentException(
                    "recordAndFinish requires a non-DEFERRED source with pre-recorded non-gradable items.");
        }
        Attempt attempt = createInTransaction(command, false);
        FinishOutcome outcome = finishInTransaction(command.userId(), attempt.getId(), List.of());
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.result();
    }

    private Attempt createInTransaction(StartPracticeCommand command, boolean checkStartLimit) {
        Duration ttl = lifetime(command);
        validateItems(command);
        JsonNode snapshot = mapper.valueToTree(new ServedSnapshot(command.sessionMeta(), command.items()));
        rejectAnswerMetadata(snapshot.path("meta"));
        if (checkStartLimit) {
            rateLimiter.checkStart(command.userId());
        }
        Instant now = clock.instant();
        Attempt attempt = new Attempt(command.userId(), command.source().kind().name(), command.source().name(),
                command.refId(), now, now.plus(ttl), snapshot);
        attempt.setGameCode(command.gameCode());
        attempts.saveAndFlush(attempt);
        for (Answer answer : command.preRecorded()) {
            recordAtStart(attempt, command, answer);
        }
        return attempt;
    }

    @Transactional
    public SessionView view(long userId, long sessionId) {
        // Serialize expiry changes with answer/finish so a GET cannot overwrite a submitted status.
        Attempt attempt = attempts.findByIdForUpdate(sessionId).orElseThrow(PracticeService::notOwned);
        if (!Objects.equals(attempt.getUserId(), userId)) {
            throw notOwned();
        }
        if ("IN_PROGRESS".equals(attempt.getStatus()) && attempt.getExpiresAt().isBefore(clock.instant())) {
            attempt.setStatus("EXPIRED");
        }
        return toView(attempt);
    }

    @Transactional(readOnly = true)
    public SessionView findActive(long userId, PracticeSource source, Long refId) {
        Objects.requireNonNull(source, "source");
        String jpql = "select a from Attempt a where a.userId = :userId and a.refType = :source "
                + "and a.status = 'IN_PROGRESS' and a.expiresAt >= :now"
                + (refId == null ? "" : " and a.refId = :refId")
                + " order by a.servedAt desc, a.id desc";
        var query = entityManager.createQuery(jpql, Attempt.class)
                .setParameter("userId", userId).setParameter("source", source.name())
                .setParameter("now", clock.instant()).setMaxResults(1);
        if (refId != null) {
            query.setParameter("refId", refId);
        }
        return query.getResultList().stream().findFirst().map(this::toView).orElse(null);
    }

    public AnswerResult answer(long userId, long sessionId, Answer answer) {
        AnswerOutcome outcome = Objects.requireNonNull(answerTransaction.execute(status ->
                answerInTransaction(userId, sessionId, answer)));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.result();
    }

    private AnswerOutcome answerInTransaction(long userId, long sessionId, Answer answer) {
        Attempt attempt = attempts.findByIdForUpdate(sessionId).orElseThrow(PracticeService::notOwned);
        ApiException rejected = writableFailure(attempt, userId, clock.instant());
        if (rejected != null) {
            return new AnswerOutcome(null, rejected);
        }
        PracticeSource source = PracticeSource.valueOf(attempt.getRefType());
        ServedSnapshot snapshot = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
        validateAnswerEvidence(answer);
        ServedItem item = snapshot.items().stream().filter(candidate -> candidate.no() == answer.no()).findFirst()
                .orElseThrow(() -> failure(ErrorCode.ITEM_NOT_IN_SESSION, HttpStatus.BAD_REQUEST,
                        "Câu hỏi không thuộc lượt luyện này."));
        boolean recordOnly = source.feedback() == FeedbackMode.RECORD || !item.type().gradable();
        boolean deferred = source.feedback() == FeedbackMode.DEFERRED && !recordOnly;
        if (!deferred && attemptItems.existsByAttemptIdAndOrderIndex(sessionId, item.no())) {
            throw failure(ErrorCode.ANSWER_ALREADY_SUBMITTED, HttpStatus.CONFLICT, "Câu hỏi đã được trả lời.");
        }
        validateAnswerRules(source, item, answer);
        rateLimiter.checkAnswer(userId);
        var meta = new LinkedHashMap<String, Object>();
        handlers.stream().filter(handler -> handler.source() == source).forEach(handler -> {
            handler.validateAnswer(item, answer);
            meta.putAll(handler.enrichMeta(item, answer));
        });
        if (deferred) {
            ObjectNode draft = attempt.getDraftAnswers() == null ? mapper.createObjectNode()
                    : (ObjectNode) attempt.getDraftAnswers().deepCopy();
            draft.set(Integer.toString(item.no()), mapper.valueToTree(new DraftAnswer(answer.answer(),
                    answer.durationMs(), answer.replayCount(), answer.skipped(), answer.audioFailed())));
            attempt.setDraftAnswers(draft);
            attempt.setDraftSavedAt(clock.instant());
            return new AnswerOutcome(new AnswerResult(item.no(), null, null, null, null, null, true, null), null);
        }
        if (answer.durationMs() < item.rules().minDurationMs()) {
            meta.put("suspicious", true);
        }
        if (answer.skipped()) {
            meta.put("skipped", true);
        }
        if (answer.audioFailed()) {
            meta.put("audioFailed", true);
        }
        meta.put("replayCount", answer.replayCount());
        boolean counted = !recordOnly && !answer.skipped() && !answer.audioFailed();
        Boolean correct = counted ? (item.type() == ItemType.PINYIN_INPUT
                ? PinyinGrader.matches(answer.answer(), item.accept()) : Objects.equals(item.answer(), answer.answer()))
                : null;
        AttemptItem recorded = new AttemptItem(sessionId, item.no(), item.type().name(), item.skill().name());
        recorded.setLexemeId(item.lexemeId());
        recorded.setAnswer(answer.answer());
        recorded.setDurationMs(answer.durationMs());
        recorded.setCorrect(correct);
        recorded.setMeta(meta);
        attemptItems.saveAndFlush(recorded);
        publishItem(attempt, item, recorded);
        AnswerResult result = recordOnly
                ? new AnswerResult(item.no(), null, null, null, null, null, null, true)
                : new AnswerResult(item.no(), correct, counted ? item.answer() : null,
                        counted ? item.accept() : null, counted ? item.explanation() : null, counted, null, null);
        return new AnswerOutcome(result, null);
    }

    public FinishResult finish(long userId, long sessionId, List<Answer> answers) {
        FinishOutcome outcome = Objects.requireNonNull(answerTransaction.execute(status ->
                finishInTransaction(userId, sessionId, answers == null ? List.of() : answers)));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.result();
    }

    private FinishOutcome finishInTransaction(long userId, long sessionId, List<Answer> answers) {
        Attempt attempt = attempts.findByIdForUpdate(sessionId).orElseThrow(PracticeService::notOwned);
        Instant now = clock.instant();
        ApiException rejected = writableFailure(attempt, userId, now);
        if (rejected != null) {
            return new FinishOutcome(null, rejected);
        }
        PracticeSource source = PracticeSource.valueOf(attempt.getRefType());
        boolean deferred = source.feedback() == FeedbackMode.DEFERRED;
        if (!deferred && !answers.isEmpty()) {
            throw validation("answers");
        }
        ServedSnapshot snapshot = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
        Map<Integer, ServedItem> served = new LinkedHashMap<>();
        snapshot.items().forEach(item -> served.put(item.no(), item));
        Map<Integer, AttemptItem> recorded = new TreeMap<>();
        attemptItems.findByAttemptIdOrderByOrderIndexAsc(sessionId)
                .forEach(item -> recorded.put(item.getOrderIndex(), item));
        ObjectNode draft = attempt.getDraftAnswers() == null ? mapper.createObjectNode()
                : (ObjectNode) attempt.getDraftAnswers().deepCopy();
        for (Answer answer : answers) {
            validateAnswerEvidence(answer);
            ServedItem item = served.get(answer.no());
            if (item == null) {
                throw failure(ErrorCode.ITEM_NOT_IN_SESSION, HttpStatus.BAD_REQUEST,
                        "Câu hỏi không thuộc lượt luyện này.");
            }
            validateAnswerRules(source, item, answer);
            rateLimiter.checkAnswer(userId);
            if (!item.type().gradable()) {
                if (recorded.containsKey(item.no())) {
                    throw failure(ErrorCode.ANSWER_ALREADY_SUBMITTED, HttpStatus.CONFLICT,
                            "Câu hỏi đã được trả lời.");
                }
                recorded.put(item.no(), recordDeferredItem(attempt, source, item, answer, false));
            } else {
                draft.set(Integer.toString(item.no()), mapper.valueToTree(new DraftAnswer(answer.answer(),
                        answer.durationMs(), answer.replayCount(), answer.skipped(), answer.audioFailed())));
            }
        }
        if (deferred) {
            if (!answers.isEmpty()) {
                attempt.setDraftAnswers(draft);
                attempt.setDraftSavedAt(now);
            }
            for (ServedItem item : snapshot.items()) {
                if (recorded.containsKey(item.no())) {
                    continue;
                }
                JsonNode evidence = draft.get(Integer.toString(item.no()));
                DraftAnswer saved = evidence == null ? null : mapper.convertValue(evidence, DraftAnswer.class);
                Answer answer = saved == null ? new Answer(item.no(), null, 0, 0, false, false, null)
                        : new Answer(item.no(), saved.answer(), saved.durationMs(), saved.replayCount(),
                                saved.skipped(), saved.audioFailed(), null);
                recorded.put(item.no(), recordDeferredItem(attempt, source, item, answer, saved == null));
            }
        }
        List<GradedItem> graded = new ArrayList<>();
        for (AttemptItem evidence : recorded.values()) {
            ServedItem item = served.get(evidence.getOrderIndex());
            Map<String, Object> meta = evidence.getMeta();
            Answer answer = new Answer(item.no(), evidence.getAnswer(),
                    evidence.getDurationMs() == null ? 0 : evidence.getDurationMs(),
                    ((Number) meta.getOrDefault("replayCount", 0)).intValue(),
                    Boolean.TRUE.equals(meta.get("skipped")), Boolean.TRUE.equals(meta.get("audioFailed")), null);
            boolean counted = source.feedback() != FeedbackMode.RECORD && item.type().gradable()
                    && !answer.audioFailed() && (deferred || !answer.skipped());
            graded.add(new GradedItem(item, answer, evidence.getCorrect(), counted, meta));
        }
        boolean hasGradable = source.feedback() != FeedbackMode.RECORD
                && snapshot.items().stream().anyMatch(item -> item.type().gradable());
        Long score = hasGradable ? graded.stream().filter(item -> Boolean.TRUE.equals(item.correct())).count() : null;
        Long maxScore = hasGradable ? graded.stream().filter(GradedItem::counted).count() : null;
        if (hasGradable && source.maxScoreFromServed()) {
            maxScore = snapshot.items().stream().filter(item -> item.type().gradable()).count()
                    - graded.stream().filter(item -> item.item().type().gradable() && item.answer().audioFailed()).count();
        }
        List<GradedItem> answered = graded.stream().filter(item -> item.counted() && item.answer().answer() != null
                && !item.answer().skipped() && !Boolean.TRUE.equals(item.meta().get("missing"))).toList();
        long suspiciousCount = answered.stream().filter(item -> Boolean.TRUE.equals(item.meta().get("suspicious"))).count();
        boolean suspicious = !answered.isEmpty() && suspiciousCount * 100 >= answered.size() * 30L;
        FinishContext context = new FinishContext(userId, sessionId, source, attempt.getRefId(), score, maxScore,
                suspicious, attempt.getServedAt(), now, graded);
        for (PracticeSourceHandler handler : handlers) {
            if (handler.source() == source) {
                suspicious |= handler.isSuspicious(context);
            }
        }
        context = new FinishContext(userId, sessionId, source, attempt.getRefId(), score, maxScore,
                suspicious, attempt.getServedAt(), now, graded);
        int duration = Math.toIntExact(Duration.between(attempt.getServedAt(), now).toMillis());
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setDurationMs(duration);
        attempt.setSuspicious(suspicious);
        attempt.setSubmittedAt(now);
        attempt.setStatus("SUBMITTED");
        entityManager.createNativeQuery("insert into learning.study_events "
                        + "(user_id, event_type, ref_type, ref_id, local_date, duration_ms) "
                        + "values (:userId, :eventType, :refType, :refId, :localDate, :duration)")
                .setParameter("userId", userId).setParameter("eventType", source == PracticeSource.GAME_ROUND ? "GAME" : "STUDY")
                .setParameter("refType", source.name()).setParameter("refId", attempt.getRefId())
                .setParameter("localDate", now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate())
                .setParameter("duration", duration).executeUpdate();
        var extra = new LinkedHashMap<String, Object>();
        for (PracticeSourceHandler handler : handlers) {
            if (handler.source() == source) {
                extra.putAll(handler.onFinish(context));
            }
        }
        events.publishEvent(new SessionFinishedEvent(userId, sessionId, source, attempt.getRefId(), score, maxScore,
                suspicious));
        Integer percent = maxScore == null || maxScore == 0 ? null : Math.toIntExact(score * 100 / maxScore);
        List<FinishItem> items = graded.stream().map(item -> new FinishItem(item.item().no(), item.correct(),
                item.item().type().gradable() ? correctAnswer(item.item()) : null, item.answer().answer())).toList();
        return new FinishOutcome(new FinishResult(sessionId, "SUBMITTED", score, maxScore, percent,
                suspicious, items, extra), null);
    }

    private AttemptItem recordDeferredItem(Attempt attempt, PracticeSource source, ServedItem item,
            Answer answer, boolean missing) {
        var meta = new LinkedHashMap<String, Object>();
        for (PracticeSourceHandler handler : handlers) {
            if (handler.source() == source) {
                handler.validateAnswer(item, answer);
                meta.putAll(handler.enrichMeta(item, answer));
            }
        }
        if (missing || answer.skipped()) {
            meta.put("missing", true);
        }
        if (!missing && answer.durationMs() < item.rules().minDurationMs()) {
            meta.put("suspicious", true);
        }
        if (answer.skipped()) {
            meta.put("skipped", true);
        }
        if (answer.audioFailed()) {
            meta.put("audioFailed", true);
        }
        meta.put("replayCount", answer.replayCount());
        Boolean correct = !item.type().gradable() || answer.audioFailed() ? null
                : !missing && !answer.skipped() && (item.type() == ItemType.PINYIN_INPUT
                        ? PinyinGrader.matches(answer.answer(), item.accept()) : Objects.equals(item.answer(), answer.answer()));
        AttemptItem recorded = new AttemptItem(attempt.getId(), item.no(), item.type().name(), item.skill().name());
        recorded.setLexemeId(item.lexemeId());
        recorded.setAnswer(answer.answer());
        recorded.setCorrect(correct);
        recorded.setDurationMs(missing ? null : answer.durationMs());
        recorded.setMeta(meta);
        attemptItems.saveAndFlush(recorded);
        publishItem(attempt, item, recorded);
        return recorded;
    }

    private String correctAnswer(ServedItem item) {
        return item.answer() != null ? item.answer()
                : item.accept() == null || item.accept().isEmpty() ? null : item.accept().get(0);
    }

    private void publishItem(Attempt attempt, ServedItem item, AttemptItem recorded) {
        events.publishEvent(new ItemGradedEvent(attempt.getUserId(), attempt.getId(),
                PracticeSource.valueOf(attempt.getRefType()), attempt.getRefId(), item.no(), item.lexemeId(),
                item.skill(), item.type(), recorded.getCorrect(), recorded.getMeta()));
    }

    private record FinishOutcome(FinishResult result, ApiException failure) {
    }

    public record FinishResult(long sessionId, String status, Long score, Long maxScore, Integer percent,
            boolean suspicious, List<FinishItem> items, Map<String, Object> extra) {
    }

    public record FinishItem(int no, Boolean correct, String correctAnswer, String yourAnswer) {
    }

    public void abandon(long userId, long sessionId) {
        ApiException rejected = answerTransaction.execute(status -> {
            Attempt attempt = attempts.findByIdForUpdate(sessionId).orElseThrow(PracticeService::notOwned);
            ApiException expired = writableFailure(attempt, userId, clock.instant());
            if (expired != null) {
                return expired;
            }
            attempt.setStatus("ABANDONED");
            return null;
        });
        // The existing T-04 transaction commits expiry before its 422 response is raised.
        if (rejected != null) {
            throw rejected;
        }
    }

    private ApiException writableFailure(Attempt attempt, long userId, Instant now) {
        if (!Objects.equals(attempt.getUserId(), userId)) {
            throw notOwned();
        }
        if ("EXPIRED".equals(attempt.getStatus())
                || ("IN_PROGRESS".equals(attempt.getStatus()) && attempt.getExpiresAt().isBefore(now))) {
            attempt.setStatus("EXPIRED");
            return failure(ErrorCode.SESSION_EXPIRED, HttpStatus.UNPROCESSABLE_ENTITY, "Lượt luyện đã hết hạn.");
        }
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw failure(ErrorCode.SESSION_ALREADY_SUBMITTED, HttpStatus.CONFLICT, "Lượt luyện đã kết thúc.");
        }
        return null;
    }

    private void validateAnswerEvidence(Answer answer) {
        if (answer == null) {
            throw validation("answer");
        }
        if (answer.no() < 1) {
            throw validation("no");
        }
        if (answer.durationMs() < 0 || answer.durationMs() > 3_600_000) {
            throw validation("durationMs");
        }
        if (answer.replayCount() < 0) {
            throw validation("replayCount");
        }
        if ((answer.answer() == null && !answer.skipped() && !answer.audioFailed())
                || (answer.answer() != null && answer.answer().length() > 100)) {
            throw validation("answer");
        }
        if (answer.skipped() && answer.audioFailed()) {
            throw validation("skipped", "audioFailed");
        }
    }

    private void validateAnswerRules(PracticeSource source, ServedItem item, Answer answer) {
        if (answer.skipped() && !source.allowSkip()) {
            throw failure(ErrorCode.SKIP_NOT_ALLOWED, HttpStatus.BAD_REQUEST, "Lượt luyện này không cho phép bỏ qua.");
        }
        if (answer.audioFailed() && !item.rules().hasAudio()) {
            throw failure(ErrorCode.AUDIO_NOT_IN_ITEM, HttpStatus.BAD_REQUEST, "Câu hỏi không có âm thanh.");
        }
        if (answer.replayCount() > item.rules().maxReplays()) {
            throw failure(ErrorCode.TOO_MANY_REPLAYS, HttpStatus.BAD_REQUEST, "Đã vượt số lần nghe cho phép.");
        }
        if (!answer.skipped() && !answer.audioFailed() && item.options() != null
                && item.options().stream().noneMatch(option -> option.key().equals(answer.answer()))) {
            throw failure(ErrorCode.OPTION_NOT_IN_ITEM, HttpStatus.BAD_REQUEST, "Đáp án không thuộc câu hỏi này.");
        }
    }

    private static ApiException failure(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message, Map.of());
    }

    private static ApiException validation(String... fields) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", java.util.Arrays.stream(fields)
                        .map(field -> Map.of("field", field, "message", "Giá trị không hợp lệ.")).toList()));
    }

    private record AnswerOutcome(AnswerResult result, ApiException failure) {
    }

    public record DraftAnswer(String answer, int durationMs, int replayCount, boolean skipped, boolean audioFailed) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerResult(int no, Boolean correct, String correctAnswer, List<String> accept,
            String explanation, Boolean counted, Boolean accepted, Boolean recorded) {
    }

    private Duration lifetime(StartPracticeCommand command) {
        Duration fixed = command.source().ttl();
        Duration override = command.ttlOverride();
        if (fixed != null) {
            if (override != null) {
                throw new IllegalArgumentException("Fixed-lifetime sources must not supply ttlOverride.");
            }
            return fixed;
        }
        if (override == null || override.isZero() || override.isNegative()) {
            throw new IllegalArgumentException("GAME_ROUND requires a positive ttlOverride.");
        }
        return override;
    }

    private void validateItems(StartPracticeCommand command) {
        var numbers = new HashSet<Integer>();
        for (ServedItem item : command.items()) {
            if (item.no() < 1 || !numbers.add(item.no())) {
                throw new IllegalArgumentException("Served item numbers must be positive and unique.");
            }
        }
        var recorded = new HashSet<Integer>();
        for (Answer answer : command.preRecorded()) {
            ServedItem item = itemAt(command.items(), answer.no());
            if (item.type().gradable() || !recorded.add(answer.no())) {
                throw new IllegalArgumentException("preRecorded requires distinct non-gradable served items.");
            }
        }
    }

    private ServedItem itemAt(List<ServedItem> items, int no) {
        return items.stream().filter(item -> item.no() == no).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("preRecorded item was not served."));
    }

    private void rejectAnswerMetadata(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(field -> {
                if (List.of("answer", "accept", "correctAnswer", "explanation").contains(field.getKey())) {
                    throw new IllegalArgumentException("Session metadata must not contain server answers.");
                }
                rejectAnswerMetadata(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectAnswerMetadata);
        }
    }

    private void recordAtStart(Attempt attempt, StartPracticeCommand command, Answer answer) {
        ServedItem item = itemAt(command.items(), answer.no());
        if (answer.durationMs() < 0 || answer.durationMs() > 3_600_000 || answer.replayCount() < 0
                || (answer.answer() == null && !answer.skipped() && !answer.audioFailed())
                || (answer.answer() != null && answer.answer().length() > 100)) {
            throw new IllegalArgumentException("Invalid preRecorded answer evidence.");
        }
        if (answer.skipped() && !command.source().allowSkip()) {
            throw new IllegalArgumentException("Source does not allow skipped preRecorded items.");
        }
        if ((answer.audioFailed() && !item.rules().hasAudio())
                || answer.replayCount() > item.rules().maxReplays()) {
            throw new IllegalArgumentException("Invalid preRecorded audio evidence.");
        }
        var meta = new LinkedHashMap<String, Object>();
        handlers.stream().filter(handler -> handler.source() == command.source()).forEach(handler -> {
            handler.validateAnswer(item, answer);
            meta.putAll(handler.enrichMeta(item, answer));
        });
        if (answer.durationMs() < item.rules().minDurationMs()) {
            meta.put("suspicious", true);
        }
        if (answer.skipped()) {
            meta.put("skipped", true);
        }
        if (answer.audioFailed()) {
            meta.put("audioFailed", true);
        }
        meta.put("replayCount", answer.replayCount());
        AttemptItem recorded = new AttemptItem(attempt.getId(), item.no(), item.type().name(), item.skill().name());
        recorded.setLexemeId(item.lexemeId());
        recorded.setAnswer(answer.answer());
        recorded.setDurationMs(answer.durationMs());
        recorded.setMeta(meta);
        attemptItems.save(recorded);
        publishItem(attempt, item, recorded);
    }

    private SessionView toView(Attempt attempt) {
        PracticeSource source = PracticeSource.valueOf(attempt.getRefType());
        ServedSnapshot snapshot = mapper.convertValue(attempt.getServedItems(), ServedSnapshot.class);
        Map<Integer, ServedItem> servedByNo = new LinkedHashMap<>();
        snapshot.items().forEach(item -> servedByNo.put(item.no(), item));
        Map<Integer, ItemState> answered = new TreeMap<>();
        for (AttemptItem recorded : attemptItems.findByAttemptIdOrderByOrderIndexAsc(attempt.getId())) {
            ServedItem item = servedByNo.get(recorded.getOrderIndex());
            boolean deferred = source.feedback() == FeedbackMode.DEFERRED;
            boolean counted = item.type().gradable()
                    && !Boolean.TRUE.equals(recorded.getMeta().get("skipped"))
                    && !Boolean.TRUE.equals(recorded.getMeta().get("audioFailed"));
            answered.put(item.no(), new ItemState(item.no(), recorded.getAnswer(),
                    deferred ? null : recorded.getCorrect(),
                    !deferred && recorded.getCorrect() != null ? item.answer() : null, !deferred && counted));
        }
        if (source.feedback() == FeedbackMode.DEFERRED && attempt.getDraftAnswers() != null) {
            attempt.getDraftAnswers().properties().forEach(field -> {
                int no = Integer.parseInt(field.getKey());
                answered.put(no, new ItemState(no, field.getValue().path("answer").isNull()
                        ? null : field.getValue().path("answer").asText(null), null, null, false));
            });
        }
        return new SessionView(attempt.getId(), source, attempt.getRefId(), source.feedback(), attempt.getStatus(),
                attempt.getServedAt(), attempt.getExpiresAt(),
                snapshot.items().stream().map(ServedItemView::from).toList(),
                List.copyOf(answered.values()), snapshot.meta());
    }

    private static ApiException notOwned() {
        return new ApiException(ErrorCode.ATTEMPT_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập lượt luyện này.", Map.of());
    }

    public record StartPracticeCommand(long userId, PracticeSource source, Long refId, String gameCode,
            List<ServedItem> items, List<Answer> preRecorded, Duration ttlOverride, Map<String, Object> sessionMeta) {
        public StartPracticeCommand {
            Objects.requireNonNull(source, "source");
            items = List.copyOf(items);
            preRecorded = preRecorded == null ? List.of() : List.copyOf(preRecorded);
            sessionMeta = sessionMeta == null ? Map.of() : new LinkedHashMap<>(sessionMeta);
        }
    }

    public record ServedSnapshot(Map<String, Object> meta, List<ServedItem> items) {
    }

    public record SessionView(long sessionId, PracticeSource source, Long refId, FeedbackMode feedback,
            String status, Instant servedAt, Instant expiresAt, List<ServedItemView> items,
            List<ItemState> answered, Map<String, Object> meta) {
    }

    public record ItemState(int no, String answer, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean correct,
            @JsonInclude(JsonInclude.Include.NON_NULL) String correctAnswer, boolean counted) {
    }
}
