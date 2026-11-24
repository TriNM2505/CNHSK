package com.cnhsk.learning.speaking;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.grading.EssayRules;
import com.cnhsk.learning.grading.GradingRepository;
import com.cnhsk.learning.grading.GradingRequestService;
import com.cnhsk.learning.grading.web.GradingRequestViews.Detail;
import com.cnhsk.learning.speaking.web.SpeakingSubmissionWrite;
import com.cnhsk.learning.speaking.web.SpeakingViews.SpeakingItems;
import com.cnhsk.learning.speaking.web.SpeakingViews.SpeakingUploadSignature;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.media.MediaProperties;
import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Speaking practice: the learner hears a model, records the same word or sentence and sends it to a teacher.
 * A recording rides the existing grading queue as a request with {@code mode = SPEAKING}, so claiming, deadlines,
 * refunds, teacher payout and the "graded" notification all work exactly as for essays.
 */
@Service
public class SpeakingService {
    static final int MAX_ITEMS = 10;
    private final SpeakingItemRepository items;
    private final GradingRepository grading;
    private final GradingRequestService requests;
    private final EntitlementApi entitlement;
    private final MediaProperties media;
    private final SettingsApi settings;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SpeakingService(SpeakingItemRepository items, GradingRepository grading, GradingRequestService requests,
            EntitlementApi entitlement, MediaProperties media, SettingsApi settings, ObjectMapper mapper, Clock clock) {
        this.items = items;
        this.grading = grading;
        this.requests = requests;
        this.entitlement = entitlement;
        this.media = media;
        this.settings = settings;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpeakingItems items(int level, String kind, int count) {
        if (level < 1 || level > 9 || count < 1 || count > MAX_ITEMS
                || !("WORD".equals(kind) || "SENTENCE".equals(kind))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cấp HSK, loại bài hoặc số lượng không hợp lệ.", Map.of());
        }
        return new SpeakingItems("WORD".equals(kind) ? items.words(level, count) : items.sentences(level, count));
    }

    public SpeakingUploadSignature signUpload() {
        requireMedia();
        long timestamp = clock.instant().getEpochSecond();
        String folder = media.getSpeakingFolder();
        return new SpeakingUploadSignature(media.getCloudName(), media.getApiKey(), folder, timestamp,
                media.signature(folder, timestamp), media.uploadUrl());
    }

    @Transactional
    public Detail submit(long userId, SpeakingSubmissionWrite request) {
        requireMedia();
        // Only recordings of this account's speaking folder are accepted, never an arbitrary link.
        if (!request.audioUrl().startsWith(media.deliveryPrefix())
                || !request.publicId().startsWith(media.getSpeakingFolder() + "/")) {
            throw SpeakingSubmissionWrite.invalid();
        }
        int hanzi = EssayRules.countHanzi(request.text());
        if (hanzi < 1) throw SpeakingSubmissionWrite.invalid();
        String recordingKey = "audio:" + request.publicId();
        grading.lockSubmit(userId);
        if (grading.hasActiveEssay(userId, recordingKey)) {
            throw new ApiException(ErrorCode.GRADING_DUPLICATE_ACTIVE, HttpStatus.CONFLICT, "Bài nói này đang chờ chấm.", Map.of());
        }
        // A recording spends a pronunciation lesson; once the month's lessons are gone it costs one credit.
        Consumption consumption = entitlement.consume(userId, PaidFeature.PRONUNCIATION, "speaking:" + UUID.randomUUID());
        var payload = mapper.createObjectNode().put("mode", "SPEAKING").put("essay", request.text())
                .put("essayHash", recordingKey).put("hanziCount", hanzi).put("audioUrl", request.audioUrl());
        payload.put("prompt", prompt(request));
        if (request.hskLevel() == null) payload.putNull("hskLevel");
        else payload.put("hskLevel", request.hskLevel());
        requests.attachTask(payload, request.taskId(), com.cnhsk.learning.knowledge.entity.LearningTask.Kind.SPEAKING);
        payload.set("consumption", mapper.valueToTree(consumption));
        Instant now = clock.instant();
        long id = grading.createPending(userId, payload, now,
                now.plus(settings.getInt(SettingKey.GRADING_CLAIM_HOURS), ChronoUnit.HOURS));
        return requests.get(userId, id);
    }

    /** What the teacher sees as the task: the pinyin and meaning the learner was reading. */
    private static String prompt(SpeakingSubmissionWrite request) {
        StringBuilder prompt = new StringBuilder("Đọc to");
        if (request.pinyin() != null && !request.pinyin().isBlank()) prompt.append(": ").append(request.pinyin().strip());
        if (request.meaning() != null && !request.meaning().isBlank()) prompt.append(" — ").append(request.meaning().strip());
        String text = prompt.toString();
        return text.codePointCount(0, text.length()) <= 500 ? text : text.substring(0, text.offsetByCodePoints(0, 500));
    }

    private void requireMedia() {
        if (!media.configured()) {
            throw new ApiException(ErrorCode.MEDIA_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa bật tính năng ghi âm.", Map.of());
        }
    }
}
